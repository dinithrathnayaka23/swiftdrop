package com.swiftdrop.assistant.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.assistant.llm.LlmRequest.Message;
import com.swiftdrop.assistant.llm.LlmRequest.Text;
import com.swiftdrop.assistant.llm.LlmRequest.ToolCalls;
import com.swiftdrop.assistant.llm.LlmRequest.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RuleBasedLlmClientTest {

    private static final List<ToolDefinition> TOOLS = List.of(
            ToolDefinition.withoutArguments("track_parcel", "Track"),
            ToolDefinition.withoutArguments("list_my_recent_parcels", "Mine"),
            ToolDefinition.withoutArguments("get_service_info", "Info"));

    private final RuleBasedLlmClient client = new RuleBasedLlmClient(JsonMapper.builder().build());

    private LlmResponse ask(Message... messages) {
        return client.complete(new LlmRequest("rules", List.of(messages), TOOLS, 600));
    }

    @Test
    void asksToTrackATrackingNumberFoundInTheQuestion() {
        LlmResponse response = ask(Text.user("where is sd7kq2mx9trb4h please?"));

        assertThat(response.source()).isEqualTo(LlmResponse.Source.RULES);
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("track_parcel");
            assertThat(call.arguments()).isEqualTo("{\"trackingNumber\":\"SD7KQ2MX9TRB4H\"}");
        });
    }

    @Test
    void picksMyParcelsServiceInfoOrTheFeeFormFromTheWords() {
        assertThat(ask(Text.user("Where are my parcels?")).toolCalls().getFirst().name())
                .isEqualTo("list_my_recent_parcels");
        assertThat(ask(Text.user("How do I contact you?")).toolCalls().getFirst().name())
                .isEqualTo("get_service_info");
        assertThat(ask(Text.user("How much to send 2 kg to Kandy?")).text()).isEqualTo(RuleBasedLlmClient.FEE_FORM);
        assertThat(ask(Text.user("Write me a poem")).text()).isEqualTo(RuleBasedLlmClient.HELP);
    }

    @Test
    void answersWithTheToolMessagesOnceTheResultsAreBack() {
        var call = new ToolCall("rules-track_parcel", "track_parcel", "{}");
        LlmResponse response = ask(Text.user("Where is SD7KQ2MX9TRB4H?"), new ToolCalls(List.of(call)),
                new ToolResult(call.id(), call.name(), "{\"found\":false,\"message\":\"I couldn't find that number.\"}"));

        assertThat(response.wantsTools()).isFalse();
        assertThat(response.text()).isEqualTo("I couldn't find that number.");
    }
}
