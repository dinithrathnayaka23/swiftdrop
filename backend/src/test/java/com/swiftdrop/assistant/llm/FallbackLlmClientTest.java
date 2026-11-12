package com.swiftdrop.assistant.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.assistant.llm.LlmRequest.Text;
import com.swiftdrop.assistant.llm.LlmRequest.ToolCalls;
import com.swiftdrop.assistant.llm.LlmRequest.ToolResult;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FallbackLlmClientTest {

    private static final LlmResponse MODEL_ANSWER = new LlmResponse("From the model", List.of(), 10,
            LlmResponse.Source.MODEL);

    private final RuleBasedLlmClient rules = new RuleBasedLlmClient(JsonMapper.builder().build());
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-12T08:00:00Z"));
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicReference<RuntimeException> modelFailure = new AtomicReference<>();

    private final LlmClient model = request -> {
        modelCalls.incrementAndGet();
        if (modelFailure.get() != null) {
            throw modelFailure.get();
        }
        return MODEL_ANSWER;
    };

    private static LlmRequest question(LlmRequest.Message... messages) {
        return new LlmRequest("prompt", List.of(messages), List.of(), 600);
    }

    @Test
    void usesTheRulesWhenNoModelIsConfigured() {
        var client = new FallbackLlmClient(null, rules, now::get);

        assertThat(client.complete(question(Text.user("Hi"))).source()).isEqualTo(LlmResponse.Source.RULES);
    }

    @Test
    void answersWithTheRulesWhenTheQuotaIsUsedUpAndPausesTheModelForAMinute() {
        var client = new FallbackLlmClient(model, rules, now::get);
        modelFailure.set(new LlmUnavailableException("HTTP 429"));

        LlmResponse first = client.complete(question(Text.user("Hi")));
        modelFailure.set(null);
        LlmResponse duringPause = client.complete(question(Text.user("Hi")));
        now.set(now.get().plus(FallbackLlmClient.PAUSE_AFTER_FAILURE).plusSeconds(1));
        LlmResponse afterPause = client.complete(question(Text.user("Hi")));

        assertThat(first.source()).isEqualTo(LlmResponse.Source.RULES);
        assertThat(duringPause.source()).isEqualTo(LlmResponse.Source.RULES);
        assertThat(afterPause).isEqualTo(MODEL_ANSWER);
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void aFailureAfterAToolRoundStillAnswersFromTheToolResults() {
        var client = new FallbackLlmClient(model, rules, now::get);
        modelFailure.set(new IllegalStateException("unexpected"));
        var call = new ToolCall("call_1", "track_parcel", "{}");

        LlmResponse response = client.complete(question(Text.user("Where is SD7KQ2MX9TRB4H?"),
                new ToolCalls(List.of(call)),
                new ToolResult("call_1", "track_parcel", "{\"message\":\"SD7KQ2MX9TRB4H: In transit.\"}")));

        assertThat(response.source()).isEqualTo(LlmResponse.Source.RULES);
        assertThat(response.text()).isEqualTo("SD7KQ2MX9TRB4H: In transit.");
    }
}
