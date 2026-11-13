package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.model.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

/** Operational webhook delivery metadata; the request payload is intentionally excluded. */
public record WebhookDeliveryResponse(
        UUID deliveryId,
        UUID parcelId,
        String eventType,
        WebhookDeliveryStatus status,
        int retryCount,
        Instant lastAttemptAt,
        Integer lastHttpStatus,
        String lastResponseExcerpt,
        String lastErrorMessage,
        Instant nextRetryAt,
        Instant deliveredAt,
        Instant createdAt) {
}
