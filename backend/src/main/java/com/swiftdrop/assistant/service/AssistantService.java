package com.swiftdrop.assistant.service;

import com.swiftdrop.assistant.config.AssistantPrompts;
import com.swiftdrop.assistant.config.AssistantProperties;
import com.swiftdrop.assistant.dto.request.ChatMessage;
import com.swiftdrop.assistant.dto.request.ChatRequest;
import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.dto.response.ChatResponse;
import com.swiftdrop.assistant.llm.LlmClient;
import com.swiftdrop.assistant.llm.LlmRequest;
import com.swiftdrop.assistant.llm.LlmResponse;
import com.swiftdrop.assistant.tools.ToolContext;
import com.swiftdrop.assistant.tools.ToolRegistry;
import com.swiftdrop.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Answers one question: caps and sanitises the history, then runs the model and its tool calls. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    static final String REPHRASE = "Sorry, I couldn't work that out. Please rephrase your question, "
            + "or ask about one parcel at a time.";

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final MessageSanitizer sanitizer;
    private final AssistantProperties properties;

    public ChatResponse chat(ChatRequest request, ToolContext context) {
        long started = System.nanoTime();
        List<LlmRequest.Message> messages = new ArrayList<>(history(request.messages()));
        ToolRegistry.Turn turn = toolRegistry.startTurn(context);

        LlmResponse response = llmClient.complete(llmRequest(messages));
        int tokens = response.totalTokens();
        String reply = null;
        while (response.wantsTools()) {
            if (!turn.canRunAnotherRound()) {
                reply = REPHRASE;
                break;
            }
            messages.add(new LlmRequest.ToolCalls(response.toolCalls()));
            messages.addAll(turn.runRound(response.toolCalls()));
            response = llmClient.complete(llmRequest(messages));
            tokens += response.totalTokens();
        }
        if (reply == null) {
            reply = response.text().isBlank() ? REPHRASE : response.text().strip();
        }

        ChatResponse.Mode mode = response.source() == LlmResponse.Source.MODEL
                ? ChatResponse.Mode.AI : ChatResponse.Mode.RULES;
        log.info("Assistant answered in {} ms: mode {}, {} tokens, {} cards", (System.nanoTime() - started) / 1_000_000,
                mode, tokens, turn.cards().size());
        return new ChatResponse(reply, turn.cards(), suggestions(turn.cards()), mode);
    }

    /** The latest messages within the history cap, each checked against the length limit and sanitised. */
    private List<LlmRequest.Message> history(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message.fromUser() && message.content().length() > properties.maxMessageChars()) {
                throw new BadRequestException("Messages can be at most " + properties.maxMessageChars()
                        + " characters long.");
            }
        }
        List<ChatMessage> recent = messages.subList(Math.max(0, messages.size() - properties.maxHistoryMessages()),
                messages.size());
        return recent.stream()
                .dropWhile(message -> !message.fromUser())
                .<LlmRequest.Message>map(message -> {
                    String text = sanitizer.sanitize(message.content());
                    return message.fromUser() ? LlmRequest.Text.user(text) : LlmRequest.Text.assistant(text);
                })
                .toList();
    }

    private LlmRequest llmRequest(List<LlmRequest.Message> messages) {
        return new LlmRequest(AssistantPrompts.SYSTEM_PROMPT, messages, toolRegistry.definitions(),
                properties.maxReplyTokens());
    }

    private static List<String> suggestions(List<AssistantCard> cards) {
        boolean tracked = cards.stream().anyMatch(card -> card.type() == AssistantCard.Type.TRACKING);
        boolean priced = cards.stream().anyMatch(card -> card.type() == AssistantCard.Type.FEE);
        if (tracked) {
            return List.of("Where are my parcels?", "How much to send 2 kg?");
        }
        if (priced) {
            return List.of("What can I send?", "Where are my parcels?");
        }
        return List.of("Where are my parcels?", "How much to send 2 kg?", "How do I contact you?");
    }
}
