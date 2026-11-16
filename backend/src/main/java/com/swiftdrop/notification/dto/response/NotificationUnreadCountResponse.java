package com.swiftdrop.notification.dto.response;

public record NotificationUnreadCountResponse(
        long unreadCount,
        long totalCount) {
}

