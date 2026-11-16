package com.swiftdrop.assistant.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/v1/assistant/chat}: the conversation so far, oldest first, ending with the
 * customer's new question. Only the latest {@code swiftdrop.assistant.max-history-messages} are used.
 */
public record ChatRequest(
        @NotEmpty @Size(max = ChatRequest.MAX_MESSAGES) List<@NotNull @Valid ChatMessage> messages) {

    /** More than this is rejected outright; fewer are trimmed to the history setting. */
    public static final int MAX_MESSAGES = 50;

    /** The question being asked is always the last message, from the user. */
    @AssertTrue(message = "the last message must be from the user")
    boolean isLastMessageFromUser() {
        return messages == null || messages.isEmpty() || messages.getLast() == null
                || messages.getLast().fromUser();
    }

    /** The new question. */
    public ChatMessage question() {
        return messages.getLast();
    }
}
