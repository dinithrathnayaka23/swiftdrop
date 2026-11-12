package com.swiftdrop.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String category,
        String title,
        String message,
        String channel,
        String deliveryStatus,
        boolean isRead,
        Instant createdAt,
        String trackingNumber,
        UUID parcelId) {
}

