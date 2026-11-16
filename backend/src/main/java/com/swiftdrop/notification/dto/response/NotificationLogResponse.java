package com.swiftdrop.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entry from the {@code notifications} table (excludes recipient PII).
 */
public record NotificationLogResponse(
        UUID id,
        UUID userId,
        UUID parcelId,
        UUID statusHistoryId,
        String trackingNumber,
        String category,
        String channel,
        String subject,
        String message,
        String deliveryStatus,
        boolean isRead,
        Instant readAt,
        Instant createdAt,
        Instant sentAt) {
}
