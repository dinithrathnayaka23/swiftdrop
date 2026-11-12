package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
import com.swiftdrop.notification.dto.response.NotificationPreferencesResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.NotificationUnreadCountResponse;
import com.swiftdrop.notification.model.Notification;
import com.swiftdrop.notification.model.NotificationChannel;
import com.swiftdrop.notification.model.NotificationDeliveryStatus;
import com.swiftdrop.notification.model.NotificationPreferences;
import com.swiftdrop.notification.repository.NotificationPreferencesRepository;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.parcel.dto.AddressDetails;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationPreferencesRepository preferencesRepository = mock(NotificationPreferencesRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final UserService userService = mock(UserService.class);
    private final ParcelService parcelService = mock(ParcelService.class);

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                preferencesRepository,
                emailService,
                userService,
                parcelService
        );
    }

    @Test
    void getNotificationsReturnsMappedList() {
        UUID userId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setUserId(userId);
        n.setParcelId(parcelId);
        n.setSubject("Parcel Out for Delivery");
        n.setMessage("Your package is on its way");
        n.setChannel(NotificationChannel.IN_APP);
        n.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        n.setRead(false);
        n.setCreatedAt(Instant.now());

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n));
        ParcelDetails parcel = createSampleParcel(parcelId, userId, "SD123456789");
        when(parcelService.getParcel(parcelId)).thenReturn(parcel);

        List<NotificationResponse> result = notificationService.getNotifications(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Parcel Out for Delivery");
        assertThat(result.get(0).trackingNumber()).isEqualTo("SD123456789");
        assertThat(result.get(0).isRead()).isFalse();
    }

    @Test
    void getUnreadCountCalculatesAccurately() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(3L);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                new Notification(), new Notification(), new Notification(), new Notification()
        ));

        NotificationUnreadCountResponse counts = notificationService.getUnreadCount(userId);

        assertThat(counts.unreadCount()).isEqualTo(3L);
        assertThat(counts.totalCount()).isEqualTo(4L);
    }

    @Test
    void markAsReadUpdatesNotification() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        Notification n = new Notification();
        n.setId(notifId);
        n.setUserId(userId);
        n.setRead(false);
        n.setChannel(NotificationChannel.IN_APP);
        n.setDeliveryStatus(NotificationDeliveryStatus.SENT);

        when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(userId, notifId);

        assertThat(response.isRead()).isTrue();
        assertThat(n.isRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    void markAsReadThrowsNotFoundWhenMissing() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(userId, notifId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAllAsReadCallsRepositoryModifyingQuery() {
        UUID userId = UUID.randomUUID();
        notificationService.markAllAsRead(userId);

        verify(notificationRepository).markAllAsReadByUserId(eq(userId), any(Instant.class));
    }

    @Test
    void deleteNotificationDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        notificationService.deleteNotification(userId, notifId);

        verify(notificationRepository).deleteByUserIdAndId(userId, notifId);
    }

    @Test
    void clearAllNotificationsDelegatesToRepository() {
        UUID userId = UUID.randomUUID();

        notificationService.clearAllNotifications(userId);

        verify(notificationRepository).deleteByUserId(userId);
    }

    @Test
    void updatePreferencesPersistsUpdatedValues() {
        UUID userId = UUID.randomUUID();
        NotificationPreferences existing = new NotificationPreferences(userId);
        when(preferencesRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(preferencesRepository.save(any(NotificationPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse profile = new UserProfileResponse(
                userId, "Jane Doe", "jane@example.com", "+94771234567",
                Role.CUSTOMER, AccountStatus.ACTIVE, Instant.now()
        );
        when(userService.getProfile(userId)).thenReturn(profile);

        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                true, false, true, true, true, true, true, true, false, true
        );

        NotificationPreferencesResponse updated = notificationService.updatePreferences(userId, request);

        assertThat(updated.emailEnabled()).isTrue();
        assertThat(updated.smsEnabled()).isFalse();
        assertThat(updated.notifyOnInTransit()).isTrue();
        assertThat(updated.notificationEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void processParcelStatusChangeCreatesInAppAndDispatchesEmailAndSms() {
        UUID parcelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID statusHistoryId = UUID.randomUUID();
        String trackingNumber = "SD2508220098";

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION,
                parcelId,
                trackingNumber,
                null,
                "IN_TRANSIT",
                "OUT_FOR_DELIVERY",
                4L,
                statusHistoryId,
                null,
                Instant.now()
        );

        when(notificationRepository.existsByStatusHistoryIdAndChannel(statusHistoryId, NotificationChannel.IN_APP))
                .thenReturn(false);

        ParcelDetails parcel = createSampleParcel(parcelId, userId, trackingNumber);
        when(parcelService.getParcel(parcelId)).thenReturn(parcel);

        NotificationPreferences prefs = new NotificationPreferences(userId);
        prefs.setEmailEnabled(true);
        prefs.setSmsEnabled(true);
        prefs.setInAppEnabled(true);
        prefs.setNotifyOnOutForDelivery(true);
        when(preferencesRepository.findById(userId)).thenReturn(Optional.of(prefs));

        UserProfileResponse profile = new UserProfileResponse(
                userId, "Kamal Silva", "kamal@example.com", "+94779876543",
                Role.CUSTOMER, AccountStatus.ACTIVE, Instant.now()
        );
        when(userService.getProfile(userId)).thenReturn(profile);

        notificationService.processParcelStatusChange(event);

        // Verify In-App, Email and SMS notifications were saved
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeast(3)).save(captor.capture());

        List<Notification> savedList = captor.getAllValues();
        assertThat(savedList).anyMatch(n -> n.getChannel() == NotificationChannel.IN_APP);
        assertThat(savedList).anyMatch(n -> n.getChannel() == NotificationChannel.EMAIL);
        assertThat(savedList).anyMatch(n -> n.getChannel() == NotificationChannel.SMS);

        // Verify email was sent
        verify(emailService).send(eq("kamal@example.com"), any(String.class), any(String.class));
    }

    @Test
    void processParcelStatusChangeSuppressesNotificationsWhenUserOptedOut() {
        UUID parcelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID statusHistoryId = UUID.randomUUID();

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                ParcelStatusChangedEvent.EVENT_TYPE,
                ParcelStatusChangedEvent.SCHEMA_VERSION,
                parcelId,
                "SD999999999",
                null,
                "PICKED_UP",
                "IN_TRANSIT",
                2L,
                statusHistoryId,
                null,
                Instant.now()
        );

        when(notificationRepository.existsByStatusHistoryIdAndChannel(statusHistoryId, NotificationChannel.IN_APP))
                .thenReturn(false);

        ParcelDetails parcel = createSampleParcel(parcelId, userId, "SD999999999");
        when(parcelService.getParcel(parcelId)).thenReturn(parcel);

        NotificationPreferences prefs = new NotificationPreferences(userId);
        prefs.setNotifyOnInTransit(false); // OPTED OUT of in-transit updates
        when(preferencesRepository.findById(userId)).thenReturn(Optional.of(prefs));

        notificationService.processParcelStatusChange(event);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailService, never()).send(any(), any(), any());
    }

    private ParcelDetails createSampleParcel(UUID parcelId, UUID userId, String trackingNumber) {
        AddressDetails address = new AddressDetails(
                UUID.randomUUID(), "Jane Doe", "+94771234567",
                "123 Main St", null, "Colombo", "Western", "00100", UUID.randomUUID()
        );

        return new ParcelDetails(
                parcelId,
                trackingNumber,
                userId,
                null,
                null,
                address,
                address,
                ParcelType.PARCEL,
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(15),
                BigDecimal.valueOf(10),
                "Sample books",
                null,
                ParcelStatusCode.OUT_FOR_DELIVERY,
                UUID.randomUUID(),
                BigDecimal.valueOf(500),
                "LKR",
                Instant.now(),
                Instant.now()
        );
    }
}
