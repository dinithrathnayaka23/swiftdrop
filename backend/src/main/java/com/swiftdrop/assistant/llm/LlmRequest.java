package com.swiftdrop.assistant.llm;

import java.util.List;

/**
 * One round of a conversation with the model.
 *
 * @param systemPrompt   the assistant's rules
 * @param messages       oldest first: the (sanitised) history, then this turn's tool calls and results
 * @param tools          the tools the model may ask for
 * @param maxReplyTokens longest reply the model may write
 */
public record LlmRequest(String systemPrompt, List<Message> messages, List<ToolDefinition> tools, int maxReplyTokens) {

    public LlmRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }

    /** A message in the conversation sent to the model. */
    public sealed interface Message permits Text, ToolCalls, ToolResult {
    }

    /** Plain text from the customer ({@code user}) or an earlier reply ({@code assistant}). */
    public record Text(boolean fromUser, String text) implements Message {

        public static Text user(String text) {
            return new Text(true, text);
        }

        public static Text assistant(String text) {
            return new Text(false, text);
        }
    }

    /** The model's earlier request for tools in this turn. */
    public record ToolCalls(List<ToolCall> calls) implements Message {

        public ToolCalls {
            calls = List.copyOf(calls);
        }
    }

    /**
     * A tool's result for one call.
     *
     * @param content a JSON object string. Every SwiftDrop tool includes a {@code message}: one plain
     *                sentence written by the server, which the rule-based client shows as it is.
     */
    public record ToolResult(String callId, String toolName, String content) implements Message {
    }
}
