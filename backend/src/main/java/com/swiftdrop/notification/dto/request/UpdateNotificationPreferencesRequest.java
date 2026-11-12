package com.swiftdrop.notification.dto.request;

public record UpdateNotificationPreferencesRequest(
        boolean emailEnabled,
        boolean smsEnabled,
        boolean inAppEnabled,
        boolean notifyOnPickedUp,
        boolean notifyOnInTransit,
        boolean notifyOnOutForDelivery,
        boolean notifyOnDelivered,
        boolean notifyOnFailedAttempt,
        boolean marketingEmails,
        boolean systemMaintenanceAlerts) {
}

