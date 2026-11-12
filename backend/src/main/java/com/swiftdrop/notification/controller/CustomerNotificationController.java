package com.swiftdrop.notification.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
import com.swiftdrop.notification.dto.response.NotificationPreferencesResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.NotificationUnreadCountResponse;
import com.swiftdrop.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/customer/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CustomerNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> listNotifications(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID userId = UUID.fromString(currentUser.userId());
        return notificationService.getNotifications(userId);
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID userId = UUID.fromString(currentUser.userId());
        return notificationService.getUnreadCount(userId);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable("id") UUID notificationId) {
        UUID userId = UUID.fromString(currentUser.userId());
        return notificationService.markAsRead(userId, notificationId);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID userId = UUID.fromString(currentUser.userId());
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable("id") UUID notificationId) {
        UUID userId = UUID.fromString(currentUser.userId());
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAllNotifications(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID userId = UUID.fromString(currentUser.userId());
        notificationService.clearAllNotifications(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public NotificationPreferencesResponse getPreferences(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UUID userId = UUID.fromString(currentUser.userId());
        return notificationService.getPreferences(userId);
    }

    @PutMapping("/preferences")
    public NotificationPreferencesResponse updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody UpdateNotificationPreferencesRequest request) {
        UUID userId = UUID.fromString(currentUser.userId());
        return notificationService.updatePreferences(userId, request);
    }
}

