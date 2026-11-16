package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import java.util.Objects;
import tools.jackson.databind.node.ObjectNode;

/**
 * What a tool found.
 *
 * @param result sent to the model as JSON: only what it needs, always with a plain one-sentence
 *               {@code message} written by the server (shown as it is in basic mode)
 * @param card   shown to the customer under the reply; {@code null} when there is nothing to show
 */
public record ToolOutput(ObjectNode result, AssistantCard card) {

    public ToolOutput {
        Objects.requireNonNull(result, "result");
        if (!result.path("message").isString()) {
            throw new IllegalArgumentException("A tool result needs a message");
        }
    }

    public static ToolOutput of(ObjectNode result) {
        return new ToolOutput(result, null);
    }
}
