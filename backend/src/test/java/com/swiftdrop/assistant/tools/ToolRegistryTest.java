package com.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.swiftdrop.assistant.llm.LlmRequest;
import com.swiftdrop.assistant.llm.ToolCall;
import com.swiftdrop.assistant.llm.ToolDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ToolRegistryTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ToolContext CONTEXT = new ToolContext(UUID.randomUUID(), "203.0.113.7");

    /** Doubles the weight it is given. */
    private static final AssistantTool DOUBLE = new AssistantTool() {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition("double_weight", "Doubles", Map.of("type", "object",
                    "properties", Map.of("weightKg", Map.of("type", "number")), "required", List.of("weightKg")));
        }

        @Override
        public ToolOutput run(ToolArguments arguments, ToolContext context) {
            BigDecimal doubled = arguments.positiveNumber("weightKg").multiply(BigDecimal.TWO);
            return ToolOutput.of(JSON.createObjectNode().put("weightKg", doubled).put("message", "Doubled."));
        }
    };

    private final ToolRegistry registry = new ToolRegistry(List.of(DOUBLE), JSON);

    private static String content(LlmRequest.ToolResult result) {
        return result.content();
    }

    @Test
    void runsAKnownToolWithCheckedArguments() {
        List<LlmRequest.ToolResult> results = registry.startTurn(CONTEXT)
                .runRound(List.of(new ToolCall("c1", "double_weight", "{\"weightKg\":\"2.5\"}")));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.callId()).isEqualTo("c1");
            assertThat(JSON.readTree(content(result)).get("weightKg").decimalValue()).isEqualByComparingTo("5.0");
        });
    }

    @Test
    void unknownToolsBadJsonUnknownArgumentsAndInvalidValuesBecomeErrorResults() {
        List<LlmRequest.ToolResult> results = registry.startTurn(CONTEXT).runRound(List.of(
                new ToolCall("c1", "delete_parcel", "{}"),
                new ToolCall("c2", "double_weight", "not json"),
                new ToolCall("c3", "double_weight", "{\"weightKg\":1,\"customerId\":\"someone-else\"}"),
                new ToolCall("c4", "double_weight", "{\"weightKg\":-1}")));

        assertThat(results).extracting(r -> JSON.readTree(content(r)).get("error").asString())
                .containsExactly("UNKNOWN_TOOL", "INVALID_INPUT", "INVALID_INPUT", "INVALID_INPUT");
        assertThat(content(results.get(2))).doesNotContain("customerId", "someone-else");
        assertThat(JSON.readTree(content(results.get(3))).get("field").asString()).isEqualTo("weightKg");
    }

    @Test
    void allowsAtMostThreeRoundsPerQuestion() {
        ToolRegistry.Turn turn = registry.startTurn(CONTEXT);
        for (int round = 0; round < ToolRegistry.MAX_ROUNDS; round++) {
            assertThat(turn.canRunAnotherRound()).isTrue();
            turn.runRound(List.of(new ToolCall("c" + round, "double_weight", "{\"weightKg\":1}")));
        }

        assertThat(turn.canRunAnotherRound()).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> turn.runRound(List.of()));
    }
}
