package com.swiftdrop.merchant.dto.response;

/** Result of sending a signed test event to the configured merchant webhook. */
public record WebhookTestResponse(
        boolean delivered,
        Integer httpStatus,
        String responseExcerpt,
        String errorMessage) {
}
