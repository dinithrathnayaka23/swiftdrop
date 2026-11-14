package com.swiftdrop.notification.mapper;

import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
import com.swiftdrop.notification.dto.response.NotificationLogResponse;
import com.swiftdrop.notification.dto.response.NotificationPreferencesResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.model.Notification;
import com.swiftdrop.notification.model.NotificationPreferences;
import com.swiftdrop.user.dto.response.UserProfileResponse;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification notification, String trackingNumber) {
        String category = resolveCategory(notification);
        String title = notification.getSubject() != null ? notification.getSubject() : "Delivery Update";

        return new NotificationResponse(
                notification.getId(),
                category,
                title,
                notification.getMessage(),
                notification.getChannel().name(),
                notification.getDeliveryStatus().name(),
                notification.isRead(),
                notification.getCreatedAt(),
                trackingNumber,
                notification.getParcelId()
        );
    }

    public static NotificationLogResponse toLogResponse(Notification notification, String trackingNumber) {
        String category = resolveCategory(notification);
        String subject = notification.getSubject() != null ? notification.getSubject() : "Delivery Update";

        return new NotificationLogResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getParcelId(),
                notification.getStatusHistoryId(),
                trackingNumber,
                category,
                notification.getChannel().name(),
                subject,
                notification.getMessage(),
                notification.getDeliveryStatus().name(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getSentAt()
        );
    }

    private static String resolveCategory(Notification notification) {
        if (notification.getParcelId() != null) {
            return "DELIVERY";
        }
        if (notification.getSubject() != null && notification.getSubject().toLowerCase().contains("welcome")) {
            return "ACCOUNT";
        }
        return "SYSTEM";
    }

    public static NotificationPreferencesResponse toPreferencesResponse(
            NotificationPreferences preferences,
            UserProfileResponse userProfile) {
        return new NotificationPreferencesResponse(
                preferences.isEmailEnabled(),
                preferences.isSmsEnabled(),
                preferences.isInAppEnabled(),
                preferences.isNotifyOnPickedUp(),
                preferences.isNotifyOnInTransit(),
                preferences.isNotifyOnOutForDelivery(),
                preferences.isNotifyOnDelivered(),
                preferences.isNotifyOnFailedAttempt(),
                preferences.isMarketingEmails(),
                preferences.isSystemMaintenanceAlerts(),
                userProfile != null ? userProfile.email() : null,
                userProfile != null ? userProfile.phone() : null
        );
    }

    public static void updatePreferences(
            NotificationPreferences target,
            UpdateNotificationPreferencesRequest request) {
        target.setEmailEnabled(request.emailEnabled());
        target.setSmsEnabled(request.smsEnabled());
        target.setInAppEnabled(request.inAppEnabled());
        target.setNotifyOnPickedUp(request.notifyOnPickedUp());
        target.setNotifyOnInTransit(request.notifyOnInTransit());
        target.setNotifyOnOutForDelivery(request.notifyOnOutForDelivery());
        target.setNotifyOnDelivered(request.notifyOnDelivered());
        target.setNotifyOnFailedAttempt(request.notifyOnFailedAttempt());
        target.setMarketingEmails(request.marketingEmails());
        target.setSystemMaintenanceAlerts(request.systemMaintenanceAlerts());
    }
}
