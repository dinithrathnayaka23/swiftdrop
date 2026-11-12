package com.swiftdrop.assistant.llm;

import com.swiftdrop.assistant.llm.LlmRequest.Message;
import com.swiftdrop.assistant.llm.LlmRequest.Text;
import com.swiftdrop.assistant.llm.LlmRequest.ToolCalls;
import com.swiftdrop.assistant.llm.LlmRequest.ToolResult;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls an OpenAI-compatible Chat Completions endpoint ({@code POST <base>/chat/completions}):
 * Gemini, OpenAI, OpenRouter or Grok. Only standard fields are sent. Any failure becomes an
 * {@link LlmUnavailableException}; there are no retries.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient http;
    private final LlmEndpoint endpoint;
    private final JsonMapper json;
    private final String url;

    /** {@code http} is pre-configured with timeouts (see {@link #create}) or bound to a mock server in tests. */
    OpenAiCompatibleLlmClient(RestClient.Builder http, LlmEndpoint endpoint, JsonMapper json) {
        this.http = http.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + endpoint.apiKey()).build();
        this.endpoint = endpoint;
        this.json = json;
        this.url = endpoint.baseUrl().replaceAll("/+$", "") + "/chat/completions";
    }

    public static OpenAiCompatibleLlmClient create(LlmEndpoint endpoint, JsonMapper json) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(endpoint.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(endpoint.timeout());
        return new OpenAiCompatibleLlmClient(RestClient.builder().requestFactory(requestFactory), endpoint, json);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String body;
        try {
            body = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(requestBody(request)))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw new LlmUnavailableException("HTTP " + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            throw new LlmUnavailableException("timeout or network error", ex);
        } catch (RestClientException ex) {
            throw new LlmUnavailableException("request failed", ex);
        }
        return parse(body);
    }

    private ObjectNode requestBody(LlmRequest request) {
        ObjectNode body = json.createObjectNode()
                .put("model", endpoint.model())
                .put("max_tokens", request.maxReplyTokens());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        request.messages().forEach(message -> add(messages, message));
        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            request.tools().forEach(tool -> {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function")
                        .put("name", tool.name())
                        .put("description", tool.description());
                // Gemini rejects an object schema with no properties, so argument-free tools send none.
                if (tool.parameters().get("properties") instanceof Map<?, ?> properties && !properties.isEmpty()) {
                    function.set("parameters", json.valueToTree(tool.parameters()));
                }
            });
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private static void add(ArrayNode messages, Message message) {
        switch (message) {
            case Text text -> messages.addObject()
                    .put("role", text.fromUser() ? "user" : "assistant")
                    .put("content", text.text());
            case ToolCalls calls -> {
                ArrayNode toolCalls = messages.addObject().put("role", "assistant").putArray("tool_calls");
                calls.calls().forEach(call -> toolCalls.addObject()
                        .put("id", call.id())
                        .put("type", "function")
                        .putObject("function").put("name", call.name()).put("arguments", call.arguments()));
            }
            case ToolResult result -> messages.addObject()
                    .put("role", "tool")
                    .put("tool_call_id", result.callId())
                    .put("content", result.content());
        }
    }

    private LlmResponse parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body == null ? "" : body);
        } catch (JacksonException ex) {
            throw new LlmUnavailableException("unreadable reply");
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (!message.isObject()) {
            throw new LlmUnavailableException("unreadable reply");
        }

        List<ToolCall> calls = new ArrayList<>();
        JsonNode toolCalls = message.path("tool_calls");
        for (int i = 0; i < toolCalls.size(); i++) {
            JsonNode call = toolCalls.get(i);
            JsonNode function = call.path("function");
            String id = call.path("id").asString("");
            JsonNode arguments = function.path("arguments");
            calls.add(new ToolCall(id.isBlank() ? "call_" + i : id, function.path("name").asString(""),
                    arguments.isString() ? arguments.asString() : arguments.toString()));
        }
        String text = message.path("content").isString() ? message.path("content").asString() : "";
        if (calls.isEmpty() && text.isBlank()) {
            throw new LlmUnavailableException("empty reply (finish_reason "
                    + choice.path("finish_reason").asString("unknown") + ")");
        }
        int tokens = root.path("usage").path("total_tokens").asInt(0);
        return new LlmResponse(text, calls, tokens, LlmResponse.Source.MODEL);
    }
}
