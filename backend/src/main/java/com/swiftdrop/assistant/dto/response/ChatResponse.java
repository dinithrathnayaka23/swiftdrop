package com.swiftdrop.assistant.dto.response;

import java.util.List;
import java.util.Objects;

/**
 * Reply to {@code POST /api/v1/assistant/chat}.
 *
 * @param reply       plain text (the widget renders no HTML or markdown)
 * @param cards       facts from this turn's tool results, oldest first
 * @param suggestions follow-up questions shown as quick-reply chips
 * @param mode        {@link Mode#RULES} when the offline rule-based assistant answered ("Basic mode")
 */
public record ChatResponse(String reply, List<AssistantCard> cards, List<String> suggestions, Mode mode) {

    public enum Mode {
        /** Answered by the language model. */
        AI,
        /** Answered by the offline rule-based assistant (no key, or the model failed). */
        RULES
    }

    public ChatResponse {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(mode, "mode");
        cards = cards == null ? List.of() : List.copyOf(cards);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}
