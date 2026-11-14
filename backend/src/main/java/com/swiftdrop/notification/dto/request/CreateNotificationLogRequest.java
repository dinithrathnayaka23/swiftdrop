package com.swiftdrop.notification.dto.request;

import java.util.UUID;

/**
 * Request payload to record a notification log entry in the database.
 */
public record CreateNotificationLogRequest(
        String channel,
        String subject,
        String message,
        UUID parcelId,
        UUID statusHistoryId,
        String recipient,
        String deliveryStatus) {
}
