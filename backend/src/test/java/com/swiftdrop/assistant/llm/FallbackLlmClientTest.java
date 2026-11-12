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

    private final RuleBasedLlmClient rules = new RuleBasedLlmClient(JsonMapper.builder().build());
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-12T08:00:00Z"));

    /** A model that answers with its name, or throws while {@code failure} is set. */
    private static final class FakeModel implements LlmClient {
        final String name;
        final AtomicInteger calls = new AtomicInteger();
        RuntimeException failure;

        FakeModel(String name) {
            this.name = name;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return new LlmResponse("From " + name, List.of(), 10, LlmResponse.Source.MODEL);
        }
    }

    private final FakeModel gemini = new FakeModel("gemini");
    private final FakeModel openrouter = new FakeModel("openrouter");
    private final FakeModel grok = new FakeModel("grok");

    private FallbackLlmClient chain() {
        return new FallbackLlmClient(List.of(new FallbackLlmClient.Model("gemini", gemini),
                new FallbackLlmClient.Model("openrouter", openrouter), new FallbackLlmClient.Model("grok", grok)),
                rules, now::get);
    }

    private static LlmRequest question(LlmRequest.Message... messages) {
        return new LlmRequest("prompt", List.of(messages), List.of(), 600);
    }

    @Test
    void usesTheRulesWhenNoModelIsConfigured() {
        var client = new FallbackLlmClient(List.of(), rules, now::get);

        assertThat(client.complete(question(Text.user("Hi"))).source()).isEqualTo(LlmResponse.Source.RULES);
    }

    @Test
    void whenGeminiHitsItsQuotaOpenRouterAnswersAndGeminiIsPausedForAMinute() {
        var client = chain();
        gemini.failure = new LlmUnavailableException("HTTP 429");

        assertThat(client.complete(question(Text.user("Hi"))).text()).isEqualTo("From openrouter");
        gemini.failure = null;
        assertThat(client.complete(question(Text.user("Hi"))).text()).isEqualTo("From openrouter");
        now.set(now.get().plus(FallbackLlmClient.PAUSE_AFTER_FAILURE).plusSeconds(1));
        assertThat(client.complete(question(Text.user("Hi"))).text()).isEqualTo("From gemini");

        assertThat(gemini.calls).hasValue(2);
    }

    @Test
    void triesGrokAfterOpenRouterAndTheRulesLast() {
        var client = chain();
        gemini.failure = new LlmUnavailableException("HTTP 429");
        openrouter.failure = new LlmUnavailableException("HTTP 402");

        assertThat(client.complete(question(Text.user("Hi"))).text()).isEqualTo("From grok");

        grok.failure = new LlmUnavailableException("timeout or network error");
        now.set(now.get().plus(FallbackLlmClient.PAUSE_AFTER_FAILURE).plusSeconds(1));
        LlmResponse response = client.complete(question(Text.user("Hi")));
        assertThat(response.source()).isEqualTo(LlmResponse.Source.RULES);
    }

    @Test
    void whenEveryModelFailsAfterAToolRoundTheRulesAnswerFromTheToolResults() {
        var client = chain();
        gemini.failure = new IllegalStateException("unexpected");
        openrouter.failure = new LlmUnavailableException("HTTP 500");
        grok.failure = new LlmUnavailableException("HTTP 503");
        var call = new ToolCall("call_1", "track_parcel", "{}");

        LlmResponse response = client.complete(question(Text.user("Where is SD7KQ2MX9TRB4H?"),
                new ToolCalls(List.of(call)),
                new ToolResult("call_1", "track_parcel", "{\"message\":\"SD7KQ2MX9TRB4H: In transit.\"}")));

        assertThat(response.source()).isEqualTo(LlmResponse.Source.RULES);
        assertThat(response.text()).isEqualTo("SD7KQ2MX9TRB4H: In transit.");
    }
}
