package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.llm.ToolDefinition;

/**
 * A function the model may call. Tools are read-only and return only public-safe data: their
 * output is sent to the model provider.
 *
 * <p>Each tool is a Spring bean; {@link ToolRegistry} offers every one of them to the model.
 */
public interface AssistantTool {

    /** Name, description and JSON Schema. The schema's properties are the only arguments accepted. */
    ToolDefinition definition();

    /**
     * Runs the tool. Expected problems (not found, rate limited, pricing unavailable) are results,
     * not exceptions, so the model can explain them.
     *
     * @param arguments the model's arguments, already checked to be an object with known names only
     * @param context   who is asking; never taken from the model's arguments
     * @throws InvalidToolArgumentException when an argument is missing or not valid
     */
    ToolOutput run(ToolArguments arguments, ToolContext context);

    default String name() {
        return definition().name();
    }
}
