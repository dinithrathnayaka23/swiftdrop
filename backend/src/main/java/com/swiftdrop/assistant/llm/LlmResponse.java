package com.swiftdrop.assistant.llm;

import java.util.List;

/**
 * The model's answer to one round: final text, or tools to run first.
 *
 * @param text        the reply when there are no tool calls; may be empty otherwise
 * @param toolCalls   tools the model wants run before it answers
 * @param totalTokens tokens used by this round, for the logs (0 when unknown)
 * @param source      who answered: the language model or the offline rules
 */
public record LlmResponse(String text, List<ToolCall> toolCalls, int totalTokens, Source source) {

    public enum Source {
        MODEL,
        RULES
    }

    public LlmResponse {
        text = text == null ? "" : text;
        toolCalls = List.copyOf(toolCalls);
    }

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }
}
