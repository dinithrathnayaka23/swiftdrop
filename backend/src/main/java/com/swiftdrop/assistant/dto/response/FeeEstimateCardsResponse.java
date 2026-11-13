package com.swiftdrop.assistant.dto.response;

import java.util.List;

/**
 * Reply to {@code POST /api/v1/assistant/fee-estimate}.
 *
 * @param cards   one FEE card, or none when prices aren't available
 * @param message one plain sentence to show, e.g. the fee or how to contact us
 */
public record FeeEstimateCardsResponse(List<AssistantCard> cards, String message) {

    public FeeEstimateCardsResponse {
        cards = List.copyOf(cards);
    }
}
