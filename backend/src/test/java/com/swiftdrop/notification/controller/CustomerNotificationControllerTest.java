package com.swiftdrop.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.notification.dto.request.CreateNotificationLogRequest;
import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
import com.swiftdrop.notification.dto.response.NotificationLogResponse;
import com.swiftdrop.notification.dto.response.NotificationPreferencesResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.NotificationUnreadCountResponse;
import com.swiftdrop.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CustomerNotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CustomerNotificationController controller;

    @Test
    void controllerRequiresAuthentication() {
        PreAuthorize auth = CustomerNotificationController.class.getAnnotation(PreAuthorize.class);
        assertThat(auth).isNotNull();
        assertThat(auth.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void listNotificationsReturnsServiceResults() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        NotificationResponse response = new NotificationResponse(
                UUID.randomUUID(), "DELIVERY", "Out for delivery", "Driver en route",
                "IN_APP", "SENT", false, Instant.now(), "SD123456789", UUID.randomUUID()
        );
        when(notificationService.getNotifications(userId)).thenReturn(List.of(response));

        List<NotificationResponse> list = controller.listNotifications(user);

        assertThat(list).containsExactly(response);
        verify(notificationService).getNotifications(userId);
    }

    @Test
    void listNotificationLogsReturnsFilteredDatabaseLogs() {
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        NotificationLogResponse logEntry = new NotificationLogResponse(
                UUID.randomUUID(), userId, parcelId, null, "SD123456789",
                "DELIVERY", "EMAIL", "Parcel Delivered", "Delivered safely",
                "SENT", false, null, Instant.now(), Instant.now()
        );
        when(notificationService.getNotificationLogs(userId, "EMAIL", "SENT", parcelId))
                .thenReturn(List.of(logEntry));

        List<NotificationLogResponse> result = controller.listNotificationLogs(user, "EMAIL", "SENT", parcelId);

        assertThat(result).containsExactly(logEntry);
        verify(notificationService).getNotificationLogs(userId, "EMAIL", "SENT", parcelId);
    }

    @Test
    void createNotificationLogPersistsAndReturnsCreated() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        CreateNotificationLogRequest request = new CreateNotificationLogRequest(
                "IN_APP", "Welcome", "Account created", null, null, null, "SENT"
        );
        NotificationLogResponse created = new NotificationLogResponse(
                UUID.randomUUID(), userId, null, null, null,
                "ACCOUNT", "IN_APP", "Welcome", "Account created",
                "SENT", false, null, Instant.now(), Instant.now()
        );
        when(notificationService.createNotificationLog(userId, request)).thenReturn(created);

        ResponseEntity<NotificationLogResponse> response = controller.createNotificationLog(user, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
    }

    @Test
    void getUnreadCountReturnsCountResponse() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        NotificationUnreadCountResponse countResponse = new NotificationUnreadCountResponse(2L, 5L);
        when(notificationService.getUnreadCount(userId)).thenReturn(countResponse);

        NotificationUnreadCountResponse res = controller.getUnreadCount(user);

        assertThat(res.unreadCount()).isEqualTo(2L);
        assertThat(res.totalCount()).isEqualTo(5L);
    }

    @Test
    void markAsReadAndMarkAsUnreadDelegateToService() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        NotificationResponse readResponse = new NotificationResponse(
                notifId, "DELIVERY", "Delivered", "Delivered to reception",
                "EMAIL", "SENT", true, Instant.now(), "SD123456789", UUID.randomUUID()
        );
        when(notificationService.markAsRead(userId, notifId)).thenReturn(readResponse);

        NotificationResponse res = controller.markAsRead(user, notifId);
        assertThat(res.isRead()).isTrue();
        verify(notificationService).markAsRead(userId, notifId);

        NotificationResponse unreadResponse = new NotificationResponse(
                notifId, "DELIVERY", "Delivered", "Delivered to reception",
                "EMAIL", "SENT", false, Instant.now(), "SD123456789", UUID.randomUUID()
        );
        when(notificationService.markAsUnread(userId, notifId)).thenReturn(unreadResponse);

        NotificationResponse resUnread = controller.markAsUnread(user, notifId);
        assertThat(resUnread.isRead()).isFalse();
        verify(notificationService).markAsUnread(userId, notifId);
    }

    @Test
    void markAllAsReadReturnsNoContent() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        ResponseEntity<Void> res = controller.markAllAsRead(user);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notificationService).markAllAsRead(userId);
    }

    @Test
    void deleteNotificationReturnsNoContent() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        ResponseEntity<Void> res = controller.deleteNotification(user, notifId);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notificationService).deleteNotification(userId, notifId);
    }

    @Test
    void clearAllNotificationsReturnsNoContent() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        ResponseEntity<Void> res = controller.clearAllNotifications(user);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        verify(notificationService).clearAllNotifications(userId);
    }

    @Test
    void getPreferencesReturnsPreferences() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        NotificationPreferencesResponse prefs = new NotificationPreferencesResponse(
                true, true, true, true, false, true, true, true, false, true, "customer@example.com", null
        );
        when(notificationService.getPreferences(userId)).thenReturn(prefs);

        NotificationPreferencesResponse res = controller.getPreferences(user);

        assertThat(res.emailEnabled()).isTrue();
        assertThat(res.notificationEmail()).isEqualTo("customer@example.com");
    }

    @Test
    void updatePreferencesUpdatesAndReturnsPreferences() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        UpdateNotificationPreferencesRequest req = new UpdateNotificationPreferencesRequest(
                false, true, true, true, true, true, true, true, false, true
        );
        NotificationPreferencesResponse updated = new NotificationPreferencesResponse(
                false, true, true, true, true, true, true, true, false, true, "customer@example.com", null
        );
        when(notificationService.updatePreferences(userId, req)).thenReturn(updated);

        NotificationPreferencesResponse res = controller.updatePreferences(user, req);

        assertThat(res.emailEnabled()).isFalse();
        assertThat(res.notifyOnInTransit()).isTrue();
    }
}
