package com.swiftdrop.assistant.llm;

import java.util.List;
import java.util.Map;

/**
 * A tool offered to the model.
 *
 * @param parameters JSON Schema of the arguments object. It only guides the model: the server
 *                   checks every argument itself.
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameters) {

    public ToolDefinition {
        parameters = Map.copyOf(parameters);
    }

    /** A tool that takes no arguments. */
    public static ToolDefinition withoutArguments(String name, String description) {
        return new ToolDefinition(name, description,
                Map.of("type", "object", "properties", Map.of(), "required", List.of()));
    }
}
