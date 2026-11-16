package com.swiftdrop.assistant.llm;

/**
 * The model asking for one tool.
 *
 * @param id        the provider's call id; the tool's result is sent back with the same id
 * @param arguments the arguments as a JSON object string, exactly as the model wrote them (unchecked)
 */
public record ToolCall(String id, String name, String arguments) {
}
