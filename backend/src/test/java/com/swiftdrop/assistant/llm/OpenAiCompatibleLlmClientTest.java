package com.swiftdrop.assistant.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.swiftdrop.assistant.llm.LlmRequest.Text;
import com.swiftdrop.assistant.llm.LlmRequest.ToolCalls;
import com.swiftdrop.assistant.llm.LlmRequest.ToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenAiCompatibleLlmClientTest {

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final List<ToolDefinition> TOOLS = List.of(
            new ToolDefinition("track_parcel", "Track", Map.of("type", "object",
                    "properties", Map.of("trackingNumber", Map.of("type", "string")),
                    "required", List.of("trackingNumber"))),
            ToolDefinition.withoutArguments("get_service_info", "Info"));

    // Recorded Chat Completions replies, trimmed to the fields the client reads.
    private static final String TOOL_CALL_REPLY = """
            {"choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
              "tool_calls":[{"id":"call_abc","type":"function","function":{"name":"track_parcel",
                "arguments":"{\\"trackingNumber\\":\\"SD7KQ2MX9TRB4H\\"}"}}]}}],
             "usage":{"prompt_tokens":410,"completion_tokens":21,"total_tokens":431}}
            """;
    private static final String FINAL_REPLY = """
            {"choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant",
              "content":"Your parcel SD7KQ2MX9TRB4H is out for delivery in Kandy."}}],
             "usage":{"total_tokens":512}}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(builder,
            new LlmEndpoint("gemini", "test-key", "gemini-flash-lite",
                    "https://generativelanguage.googleapis.com/v1beta/openai/", Duration.ofSeconds(30)),
            JsonMapper.builder().build());

    @Test
    void sendsAStandardChatCompletionsRequestAndReadsTheToolCalls() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gemini-flash-lite"))
                .andExpect(jsonPath("$.max_tokens").value(600))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("Where is SD7KQ2MX9TRB4H?"))
                .andExpect(jsonPath("$.tools[0].function.parameters.required[0]").value("trackingNumber"))
                .andExpect(jsonPath("$.tools[1].function.parameters").doesNotExist())
                .andExpect(jsonPath("$.store").doesNotExist())
                .andExpect(jsonPath("$.tools[0].function.strict").doesNotExist())
                .andRespond(withSuccess(TOOL_CALL_REPLY, MediaType.APPLICATION_JSON));

        LlmResponse response = client.complete(new LlmRequest("rules",
                List.of(Text.user("Where is SD7KQ2MX9TRB4H?")), TOOLS, 600));

        server.verify();
        assertThat(response.source()).isEqualTo(LlmResponse.Source.MODEL);
        assertThat(response.totalTokens()).isEqualTo(431);
        assertThat(response.toolCalls()).containsExactly(
                new ToolCall("call_abc", "track_parcel", "{\"trackingNumber\":\"SD7KQ2MX9TRB4H\"}"));
    }

    @Test
    void sendsTheToolCallsAndResultsBackAndReadsTheFinalAnswer() {
        var call = new ToolCall("call_abc", "track_parcel", "{\"trackingNumber\":\"SD7KQ2MX9TRB4H\"}");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_abc"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.arguments").value(call.arguments()))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_abc"))
                .andExpect(jsonPath("$.messages[3].content").value("{\"found\":false}"))
                .andRespond(withSuccess(FINAL_REPLY, MediaType.APPLICATION_JSON));

        LlmResponse response = client.complete(new LlmRequest("rules", List.of(Text.user("Where is it?"),
                new ToolCalls(List.of(call)), new ToolResult("call_abc", "track_parcel", "{\"found\":false}")),
                TOOLS, 600));

        assertThat(response.wantsTools()).isFalse();
        assertThat(response.text()).isEqualTo("Your parcel SD7KQ2MX9TRB4H is out for delivery in Kandy.");
    }

    @Test
    void quotaErrorsServerErrorsAndEmptyRepliesMakeTheModelUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"\"}}]}",
                MediaType.APPLICATION_JSON));
        LlmRequest request = new LlmRequest("rules", List.of(Text.user("Hi")), TOOLS, 600);

        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmUnavailableException.class)
                .hasMessage("HTTP 429");
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmUnavailableException.class)
                .hasMessage("HTTP 500");
        assertThatThrownBy(() -> client.complete(request)).isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("length");
        server.verify();
    }
}
