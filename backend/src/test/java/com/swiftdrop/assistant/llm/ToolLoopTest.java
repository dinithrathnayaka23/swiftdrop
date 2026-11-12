package com.swiftdrop.assistant.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.assistant.config.AssistantProperties;
import com.swiftdrop.assistant.dto.request.ChatMessage;
import com.swiftdrop.assistant.dto.request.ChatRequest;
import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.dto.response.ChatResponse;
import com.swiftdrop.assistant.service.AssistantService;
import com.swiftdrop.assistant.service.MessageSanitizer;
import com.swiftdrop.assistant.tools.AssistantTool;
import com.swiftdrop.assistant.tools.ToolArguments;
import com.swiftdrop.assistant.tools.ToolContext;
import com.swiftdrop.assistant.tools.ToolOutput;
import com.swiftdrop.assistant.tools.ToolRegistry;
import com.swiftdrop.assistant.tools.TrackParcelTool;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

/** The whole question loop: model or rules, the registry, a tool, and falling back. */
class ToolLoopTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ToolContext CONTEXT = new ToolContext(UUID.randomUUID(), "203.0.113.7");
    private static final AssistantCard CARD = AssistantCard.tracking(
            new PublicTrackingResponse("SD7KQ2MX9TRB4H", null, null, null, "Colombo", "Kandy", null, List.of()));

    /** Stands in for TrackParcelTool under its real name and schema. */
    private static final AssistantTool TRACK = new AssistantTool() {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(TrackParcelTool.NAME, "Track", Map.of("type", "object",
                    "properties", Map.of("trackingNumber", Map.of("type", "string")),
                    "required", List.of("trackingNumber")));
        }

        @Override
        public ToolOutput run(ToolArguments arguments, ToolContext context) {
            String number = arguments.text("trackingNumber", 40);
            return new ToolOutput(JSON.createObjectNode().put("message", number + ": In transit."), CARD);
        }
    };

    private final Deque<LlmResponse> script = new ArrayDeque<>();
    private final List<LlmRequest> modelRequests = new ArrayList<>();
    private final LlmClient model = request -> {
        modelRequests.add(request);
        if (script.isEmpty()) {
            throw new LlmUnavailableException("HTTP 503");
        }
        return script.pop();
    };

    private AssistantService service(LlmClient llm) {
        var registry = new ToolRegistry(
                new StaticListableBeanFactory(Map.of("track", TRACK)).getBeanProvider(AssistantTool.class), JSON);
        return new AssistantService(llm, registry, new MessageSanitizer(),
                new AssistantProperties("gemini", "key", "model", null, null, 0, 0, 0, null), JSON);
    }

    private static ChatRequest ask(String question) {
        return new ChatRequest(List.of(new ChatMessage("user", question)));
    }

    private static LlmResponse calls(ToolCall... calls) {
        return new LlmResponse("", List.of(calls), 30, LlmResponse.Source.MODEL);
    }

    @Test
    void anUnknownToolGoesBackToTheModelAsAnErrorAndTheModelStillAnswers() {
        script.add(calls(new ToolCall("c1", "cancel_parcel", "{\"trackingNumber\":\"SD7KQ2MX9TRB4H\"}")));
        script.add(new LlmResponse("I can't cancel parcels.", List.of(), 20, LlmResponse.Source.MODEL));

        ChatResponse response = service(model).chat(ask("Cancel SD7KQ2MX9TRB4H"), CONTEXT);

        assertThat(response.reply()).isEqualTo("I can't cancel parcels.");
        assertThat(response.mode()).isEqualTo(ChatResponse.Mode.AI);
        assertThat(response.cards()).isEmpty();
        assertThat(modelRequests.get(1).messages().getLast()).isInstanceOfSatisfying(LlmRequest.ToolResult.class,
                result -> assertThat(result.content()).contains("UNKNOWN_TOOL"));
    }

    @Test
    void whenTheModelFailsAfterAToolRoundTheRulesAnswerAndTheCardIsKept() {
        script.add(calls(new ToolCall("c1", TrackParcelTool.NAME, "{\"trackingNumber\":\"SD7KQ2MX9TRB4H\"}")));
        var fallback = new FallbackLlmClient(model, new RuleBasedLlmClient(JSON), Instant::now);

        ChatResponse response = service(fallback).chat(ask("Where is SD7KQ2MX9TRB4H?"), CONTEXT);

        assertThat(response.mode()).isEqualTo(ChatResponse.Mode.RULES);
        assertThat(response.reply()).isEqualTo("SD7KQ2MX9TRB4H: In transit.");
        assertThat(response.cards()).containsExactly(CARD);
    }

    @Test
    void basicModeTracksAParcelWithTheRealToolNameEndToEnd() {
        var basicMode = new FallbackLlmClient(null, new RuleBasedLlmClient(JSON), Instant::now);

        ChatResponse response = service(basicMode).chat(ask("where is sd7kq2mx9trb4h?"), CONTEXT);

        assertThat(response.mode()).isEqualTo(ChatResponse.Mode.RULES);
        assertThat(response.reply()).isEqualTo("SD7KQ2MX9TRB4H: In transit.");
        assertThat(response.cards()).containsExactly(CARD);
        assertThat(modelRequests).isEmpty();
    }
}
