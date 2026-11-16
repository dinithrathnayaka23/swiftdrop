package com.swiftdrop.notification.dto.response;

public record NotificationPreferencesResponse(
        boolean emailEnabled,
        boolean smsEnabled,
        boolean inAppEnabled,
        boolean notifyOnPickedUp,
        boolean notifyOnInTransit,
        boolean notifyOnOutForDelivery,
        boolean notifyOnDelivered,
        boolean notifyOnFailedAttempt,
        boolean marketingEmails,
        boolean systemMaintenanceAlerts,
        String notificationEmail,
        String notificationPhone) {
}

