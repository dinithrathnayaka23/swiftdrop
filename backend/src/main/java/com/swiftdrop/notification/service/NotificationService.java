package com.swiftdrop.notification.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
import com.swiftdrop.notification.dto.response.NotificationPreferencesResponse;
import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.NotificationUnreadCountResponse;
import com.swiftdrop.notification.mapper.NotificationMapper;
import com.swiftdrop.notification.model.Notification;
import com.swiftdrop.notification.model.NotificationChannel;
import com.swiftdrop.notification.model.NotificationDeliveryStatus;
import com.swiftdrop.notification.model.NotificationPreferences;
import com.swiftdrop.notification.repository.NotificationPreferencesRepository;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final ParcelService parcelService;

    // Cache of parcelId -> trackingNumber to minimize lookups during list queries
    private final Map<UUID, String> trackingNumberCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(UUID userId) {
        List<Notification> items = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return items.stream()
                .map(n -> {
                    String trackingNumber = null;
                    if (n.getParcelId() != null) {
                        trackingNumber = resolveTrackingNumber(n.getParcelId());
                    }
                    return NotificationMapper.toResponse(n, trackingNumber);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(UUID userId) {
        long unread = notificationRepository.countByUserIdAndIsReadFalse(userId);
        long total = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        return new NotificationUnreadCountResponse(unread, total);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        notification.setRead(true);
        notification.setReadAt(Instant.now());
        Notification saved = notificationRepository.save(notification);

        String trackingNumber = saved.getParcelId() != null ? resolveTrackingNumber(saved.getParcelId()) : null;
        return NotificationMapper.toResponse(saved, trackingNumber);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadByUserId(userId, Instant.now());
    }

    @Transactional
    public void deleteNotification(UUID userId, UUID notificationId) {
        notificationRepository.deleteByUserIdAndId(userId, notificationId);
    }

    @Transactional
    public void clearAllNotifications(UUID userId) {
        notificationRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        NotificationPreferences prefs = getOrCreatePreferences(userId);
        UserProfileResponse profile = null;
        try {
            profile = userService.getProfile(userId);
        } catch (Exception e) {
            log.debug("User profile not found for preferences lookup: {}", userId);
        }
        return NotificationMapper.toPreferencesResponse(prefs, profile);
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId, UpdateNotificationPreferencesRequest request) {
        NotificationPreferences prefs = getOrCreatePreferences(userId);
        NotificationMapper.updatePreferences(prefs, request);
        NotificationPreferences saved = preferencesRepository.save(prefs);

        UserProfileResponse profile = null;
        try {
            profile = userService.getProfile(userId);
        } catch (Exception e) {
            log.debug("User profile not found: {}", userId);
        }
        return NotificationMapper.toPreferencesResponse(saved, profile);
    }

    /**
     * Consumes an incoming ParcelStatusChangedEvent from RabbitMQ and creates in-app,
     * email, and SMS notifications according to customer preferences.
     */
    @Transactional
    public void processParcelStatusChange(ParcelStatusChangedEvent event) {
        if (event == null || event.parcelId() == null || event.newStatus() == null) {
            log.warn("Invalid parcel status change event received");
            return;
        }

        // Idempotency: skip if we already stored an in-app notification for this status history record
        if (event.statusHistoryId() != null &&
                notificationRepository.existsByStatusHistoryIdAndChannel(event.statusHistoryId(), NotificationChannel.IN_APP)) {
            log.debug("Notification for status history {} already processed, skipping", event.statusHistoryId());
            return;
        }

        ParcelDetails parcel;
        try {
            parcel = parcelService.getParcel(event.parcelId());
        } catch (Exception ex) {
            log.error("Could not fetch parcel details for notification on parcelId: {}", event.parcelId(), ex);
            return;
        }

        if (event.trackingNumber() != null) {
            trackingNumberCache.put(event.parcelId(), event.trackingNumber());
        }

        UUID customerUserId = parcel.customerUserId();
        NotificationPreferences preferences = customerUserId != null
                ? getOrCreatePreferences(customerUserId)
                : new NotificationPreferences();

        // Check if user wants notifications for this status milestone
        if (!shouldNotifyForStatus(preferences, event.newStatus())) {
            log.debug("User preferences suppress notifications for status {} (parcel {})", event.newStatus(), event.parcelId());
            return;
        }

        String tracking = event.trackingNumber() != null ? event.trackingNumber() : parcel.trackingNumber();
        String subject = buildSubject(event.newStatus(), tracking);
        String message = buildMessage(event.newStatus(), tracking);

        // 1. IN_APP notification
        if (customerUserId != null && preferences.isInAppEnabled()) {
            Notification inAppNotification = new Notification();
            inAppNotification.setUserId(customerUserId);
            inAppNotification.setParcelId(event.parcelId());
            inAppNotification.setStatusHistoryId(event.statusHistoryId());
            inAppNotification.setChannel(NotificationChannel.IN_APP);
            inAppNotification.setRecipient("in-app:" + customerUserId);
            inAppNotification.setSubject(subject);
            inAppNotification.setMessage(message);
            inAppNotification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            inAppNotification.setSentAt(Instant.now());
            notificationRepository.save(inAppNotification);
        }

        // 2. EMAIL notification
        if (preferences.isEmailEnabled()) {
            String recipientEmail = resolveRecipientEmail(customerUserId, parcel);
            if (recipientEmail != null && !recipientEmail.isBlank()) {
                Notification emailNotification = new Notification();
                emailNotification.setUserId(customerUserId);
                emailNotification.setParcelId(event.parcelId());
                emailNotification.setStatusHistoryId(event.statusHistoryId());
                emailNotification.setChannel(NotificationChannel.EMAIL);
                emailNotification.setRecipient(recipientEmail);
                emailNotification.setSubject(subject);
                emailNotification.setMessage(message);
                emailNotification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
                emailNotification.setSentAt(Instant.now());
                notificationRepository.save(emailNotification);

                try {
                    emailService.send(recipientEmail, subject, message);
                } catch (Exception ex) {
                    log.error("Failed to queue email notification for parcel: {}", tracking, ex);
                }
            }
        }

        // 3. SMS notification (Mock SMS)
        if (preferences.isSmsEnabled()) {
            String recipientPhone = resolveRecipientPhone(customerUserId, parcel);
            if (recipientPhone != null && !recipientPhone.isBlank()) {
                Notification smsNotification = new Notification();
                smsNotification.setUserId(customerUserId);
                smsNotification.setParcelId(event.parcelId());
                smsNotification.setStatusHistoryId(event.statusHistoryId());
                smsNotification.setChannel(NotificationChannel.SMS);
                smsNotification.setRecipient(recipientPhone);
                smsNotification.setSubject(subject);
                smsNotification.setMessage(message);
                smsNotification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
                smsNotification.setSentAt(Instant.now());
                notificationRepository.save(smsNotification);

                log.info("[Mock SMS] Dispatched SMS alert to recipient for parcel {}: '{}'", tracking, message);
            }
        }
    }

    private NotificationPreferences getOrCreatePreferences(UUID userId) {
        return preferencesRepository.findById(userId)
                .orElseGet(() -> preferencesRepository.save(new NotificationPreferences(userId)));
    }

    private boolean shouldNotifyForStatus(NotificationPreferences prefs, String status) {
        if (status == null) return false;
        String normalized = status.toUpperCase();
        return switch (normalized) {
            case "PICKED_UP" -> prefs.isNotifyOnPickedUp();
            case "IN_TRANSIT" -> prefs.isNotifyOnInTransit();
            case "OUT_FOR_DELIVERY" -> prefs.isNotifyOnOutForDelivery();
            case "DELIVERED" -> prefs.isNotifyOnDelivered();
            case "FAILED", "DELIVERY_ATTEMPTED" -> prefs.isNotifyOnFailedAttempt();
            default -> true;
        };
    }

    private String buildSubject(String status, String trackingNumber) {
        String normalized = status != null ? status.toUpperCase() : "";
        return switch (normalized) {
            case "PICKED_UP" -> "Parcel Picked Up (" + trackingNumber + ")";
            case "IN_TRANSIT" -> "Parcel in Transit (" + trackingNumber + ")";
            case "OUT_FOR_DELIVERY" -> "Parcel Out for Delivery (" + trackingNumber + ")";
            case "DELIVERED" -> "Parcel Delivered Successfully (" + trackingNumber + ")";
            case "FAILED", "DELIVERY_ATTEMPTED" -> "Delivery Attempt Failed (" + trackingNumber + ")";
            default -> "Parcel Status Update (" + trackingNumber + ")";
        };
    }

    private String buildMessage(String status, String trackingNumber) {
        String normalized = status != null ? status.toUpperCase() : "";
        return switch (normalized) {
            case "PICKED_UP" -> "Your parcel " + trackingNumber + " has been picked up from the sender and registered into our network.";
            case "IN_TRANSIT" -> "Your parcel " + trackingNumber + " has departed the sorting center and is in transit.";
            case "OUT_FOR_DELIVERY" -> "Your parcel " + trackingNumber + " is out for delivery with our courier. Please be available to receive it.";
            case "DELIVERED" -> "Your parcel " + trackingNumber + " has been delivered successfully. Proof of delivery is available.";
            case "FAILED", "DELIVERY_ATTEMPTED" -> "A delivery attempt for parcel " + trackingNumber + " was unsuccessful. A re-attempt or contact will follow.";
            default -> "Your parcel " + trackingNumber + " status is now " + status + ".";
        };
    }

    private String resolveRecipientEmail(UUID userId, ParcelDetails parcel) {
        if (userId != null) {
            try {
                return userService.getProfile(userId).email();
            } catch (Exception e) {
                log.debug("Could not resolve account email for userId: {}", userId);
            }
        }
        return null;
    }

    private String resolveRecipientPhone(UUID userId, ParcelDetails parcel) {
        if (userId != null) {
            try {
                return userService.getProfile(userId).phone();
            } catch (Exception e) {
                log.debug("Could not resolve account phone for userId: {}", userId);
            }
        }
        if (parcel.dropoffAddress() != null && parcel.dropoffAddress().contactPhone() != null) {
            return parcel.dropoffAddress().contactPhone();
        }
        return null;
    }

    private String resolveTrackingNumber(UUID parcelId) {
        String cached = trackingNumberCache.get(parcelId);
        if (cached != null) return cached;
        try {
            ParcelDetails parcel = parcelService.getParcel(parcelId);
            if (parcel != null && parcel.trackingNumber() != null) {
                trackingNumberCache.put(parcelId, parcel.trackingNumber());
                return parcel.trackingNumber();
            }
        } catch (Exception e) {
            log.debug("Could not resolve parcel tracking number for: {}", parcelId);
        }
        return null;
    }
}
