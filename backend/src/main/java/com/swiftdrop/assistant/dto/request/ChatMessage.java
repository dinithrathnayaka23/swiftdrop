package com.swiftdrop.assistant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One message of the conversation the browser keeps. Only {@code user} and {@code assistant} text is
 * accepted: the browser can never send tool calls or tool results.
 *
 * @param content at most {@link #MAX_CONTENT_CHARS} here; user messages are also held to the
 *                stricter {@code swiftdrop.assistant.max-message-chars} by the service
 */
public record ChatMessage(
        @NotNull @Pattern(regexp = "user|assistant", message = "must be user or assistant") String role,
        @NotBlank @Size(max = ChatMessage.MAX_CONTENT_CHARS) String content) {

    /** Hard cap for any message, including earlier assistant replies sent back by the browser. */
    public static final int MAX_CONTENT_CHARS = 4000;

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public boolean fromUser() {
        return USER.equals(role);
    }
}
