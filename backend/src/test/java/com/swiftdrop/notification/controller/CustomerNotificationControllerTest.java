package com.swiftdrop.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
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
    void markAsReadDelegatesToService() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.CUSTOMER));

        NotificationResponse response = new NotificationResponse(
                notifId, "DELIVERY", "Delivered", "Delivered to reception",
                "EMAIL", "SENT", true, Instant.now(), "SD123456789", UUID.randomUUID()
        );
        when(notificationService.markAsRead(userId, notifId)).thenReturn(response);

        NotificationResponse res = controller.markAsRead(user, notifId);

        assertThat(res.isRead()).isTrue();
        verify(notificationService).markAsRead(userId, notifId);
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

