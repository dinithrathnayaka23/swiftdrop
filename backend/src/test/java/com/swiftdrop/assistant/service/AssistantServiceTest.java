package com.swiftdrop.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.assistant.config.AssistantProperties;
import com.swiftdrop.assistant.dto.request.ChatMessage;
import com.swiftdrop.assistant.dto.request.ChatRequest;
import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.dto.response.ChatResponse;
import com.swiftdrop.assistant.llm.LlmClient;
import com.swiftdrop.assistant.llm.LlmRequest;
import com.swiftdrop.assistant.llm.LlmResponse;
import com.swiftdrop.assistant.llm.ToolCall;
import com.swiftdrop.assistant.llm.ToolDefinition;
import com.swiftdrop.assistant.tools.AssistantTool;
import com.swiftdrop.assistant.tools.ToolArguments;
import com.swiftdrop.assistant.tools.ToolContext;
import com.swiftdrop.assistant.tools.ToolOutput;
import com.swiftdrop.assistant.tools.ToolRegistry;
import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

class AssistantServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ToolContext CONTEXT = new ToolContext(UUID.randomUUID(), "203.0.113.7");
    private static final AssistantCard CARD = AssistantCard.tracking(
            new PublicTrackingResponse("SD7KQ2MX9TRB4H", null, null, null, "Colombo", "Kandy", null, List.of()));

    /** Answers each round from a script and records what it was sent. */
    static final class ScriptedLlm implements LlmClient {
        final Deque<LlmResponse> script = new ArrayDeque<>();
        final List<LlmRequest> requests = new ArrayList<>();

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            return script.isEmpty() ? new LlmResponse("", List.of(), 0, LlmResponse.Source.MODEL) : script.pop();
        }
    }

    private static final AssistantTool TRACK = new AssistantTool() {
        @Override
        public ToolDefinition definition() {
            return ToolDefinition.withoutArguments("track_parcel", "Track");
        }

        @Override
        public ToolOutput run(ToolArguments arguments, ToolContext context) {
            return new ToolOutput(JSON.createObjectNode().put("message", "In transit."), CARD);
        }
    };

    private final ScriptedLlm llm = new ScriptedLlm();
    private final AssistantService service = new AssistantService(llm,
            new ToolRegistry(new StaticListableBeanFactory(Map.of("track", TRACK)).getBeanProvider(AssistantTool.class), JSON),
            new MessageSanitizer(), new AssistantProperties("gemini", "key", null, null, null, 40, 3, 0, null));

    private static LlmResponse callTrack(String id) {
        return new LlmResponse("", List.of(new ToolCall(id, "track_parcel", "{}")), 40, LlmResponse.Source.MODEL);
    }

    private static ChatRequest ask(String... userThenAssistant) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < userThenAssistant.length; i++) {
            messages.add(new ChatMessage(i % 2 == 0 ? "user" : "assistant", userThenAssistant[i]));
        }
        return new ChatRequest(messages);
    }

    @Test
    void runsTheToolTheModelAsksForAndReturnsItsCardWithTheFinalText() {
        llm.script.add(callTrack("c1"));
        llm.script.add(new LlmResponse("It's in transit to Kandy.", List.of(), 60, LlmResponse.Source.MODEL));

        ChatResponse response = service.chat(ask("Where is SD7KQ2MX9TRB4H?"), CONTEXT);

        assertThat(response.reply()).isEqualTo("It's in transit to Kandy.");
        assertThat(response.mode()).isEqualTo(ChatResponse.Mode.AI);
        assertThat(response.cards()).containsExactly(CARD);
        assertThat(llm.requests.get(1).messages()).last().isInstanceOfSatisfying(LlmRequest.ToolResult.class,
                result -> assertThat(result.callId()).isEqualTo("c1"));
    }

    @Test
    void stopsAfterThreeToolRoundsWithASafeReply() {
        for (int i = 0; i < 5; i++) {
            llm.script.add(callTrack("c" + i));
        }

        ChatResponse response = service.chat(ask("Where is SD7KQ2MX9TRB4H?"), CONTEXT);

        assertThat(response.reply()).isEqualTo(AssistantService.REPHRASE);
        assertThat(llm.requests).hasSize(ToolRegistry.MAX_ROUNDS + 1);
    }

    @Test
    void sendsOnlyTheCappedSanitisedHistoryStartingWithTheUser() {
        llm.script.add(new LlmResponse("Hello", List.of(), 5, LlmResponse.Source.RULES));

        ChatResponse response = service.chat(ask("first", "reply", "Call me on 0771234567", "ok",
                "Email me at a@b.lk"), CONTEXT);

        assertThat(response.mode()).isEqualTo(ChatResponse.Mode.RULES);
        List<LlmRequest.Message> sent = llm.requests.getFirst().messages();
        assertThat(sent).containsExactly(LlmRequest.Text.user("Call me on [phone hidden]"),
                LlmRequest.Text.assistant("ok"), LlmRequest.Text.user("Email me at [email hidden]"));
    }

    @Test
    void rejectsAUserMessageOverTheLengthLimit() {
        assertThatThrownBy(() -> service.chat(ask("x".repeat(41)), CONTEXT)).isInstanceOf(BadRequestException.class);
        assertThat(llm.requests).isEmpty();
    }
}
