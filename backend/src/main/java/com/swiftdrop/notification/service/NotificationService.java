package com.swiftdrop.notification.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.messaging.event.ParcelStatusChangedEvent;
import com.swiftdrop.notification.dto.request.CreateNotificationLogRequest;
import com.swiftdrop.notification.dto.request.UpdateNotificationPreferencesRequest;
import com.swiftdrop.notification.dto.response.NotificationLogResponse;
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
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import com.swiftdrop.user.dto.response.UserProfileResponse;
import com.swiftdrop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationTemplateService templateService;
    private final UserService userService;
    private final ParcelService parcelService;
    private final SystemSettingsService systemSettingsService;

    // Cache of parcelId -> trackingNumber to minimize lookups during list queries
    private final Map<UUID, String> trackingNumberCache = new ConcurrentHashMap<>();

    @Autowired
    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferencesRepository preferencesRepository,
            EmailService emailService,
            SmsService smsService,
            NotificationTemplateService templateService,
            UserService userService,
            ParcelService parcelService,
            SystemSettingsService systemSettingsService) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.emailService = emailService;
        this.smsService = smsService;
        this.templateService = templateService;
        this.userService = userService;
        this.parcelService = parcelService;
        this.systemSettingsService = systemSettingsService;
    }

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferencesRepository preferencesRepository,
            EmailService emailService,
            UserService userService,
            ParcelService parcelService) {
        this(
                notificationRepository,
                preferencesRepository,
                emailService,
                new SmsService(),
                new NotificationTemplateService(),
                userService,
                parcelService,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(UUID userId) {
        List<Notification> items = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return items.stream()
                .map(n -> {
                    String trackingNumber = n.getParcelId() != null ? resolveTrackingNumber(n.getParcelId()) : null;
                    return NotificationMapper.toResponse(n, trackingNumber);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationLogResponse> getNotificationLogs(
            UUID userId,
            String channelFilter,
            String statusFilter,
            UUID parcelIdFilter) {
        List<Notification> items;
        if (parcelIdFilter != null) {
            items = notificationRepository.findByParcelIdOrderByCreatedAtDesc(parcelIdFilter).stream()
                    .filter(n -> userId.equals(n.getUserId()))
                    .toList();
        } else if (channelFilter != null && !channelFilter.isBlank()) {
            NotificationChannel ch = parseChannel(channelFilter);
            items = notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, ch);
        } else if (statusFilter != null && !statusFilter.isBlank()) {
            NotificationDeliveryStatus st = parseDeliveryStatus(statusFilter);
            items = notificationRepository.findByUserIdAndDeliveryStatusOrderByCreatedAtDesc(userId, st);
        } else {
            items = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        return items.stream()
                .filter(n -> channelFilter == null || channelFilter.isBlank()
                        || n.getChannel().name().equalsIgnoreCase(channelFilter.trim()))
                .filter(n -> statusFilter == null || statusFilter.isBlank()
                        || n.getDeliveryStatus().name().equalsIgnoreCase(statusFilter.trim()))
                .filter(n -> parcelIdFilter == null || parcelIdFilter.equals(n.getParcelId()))
                .map(n -> {
                    String trackingNumber = n.getParcelId() != null ? resolveTrackingNumber(n.getParcelId()) : null;
                    return NotificationMapper.toLogResponse(n, trackingNumber);
                })
                .toList();
    }

    @Transactional
    public NotificationLogResponse createNotificationLog(UUID userId, CreateNotificationLogRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        NotificationChannel channel = parseChannel(request.channel());
        NotificationDeliveryStatus status = parseDeliveryStatus(request.deliveryStatus());

        Notification logEntry = new Notification();
        logEntry.setUserId(userId);
        logEntry.setParcelId(request.parcelId());
        logEntry.setStatusHistoryId(request.statusHistoryId());
        logEntry.setChannel(channel);
        logEntry.setRecipient(
                request.recipient() != null && !request.recipient().isBlank()
                        ? request.recipient().trim()
                        : "in-app:" + userId
        );
        logEntry.setSubject(
                request.subject() != null && !request.subject().isBlank()
                        ? request.subject().trim()
                        : "System Notification"
        );
        logEntry.setMessage(request.message().trim());
        logEntry.setDeliveryStatus(status);
        if (status == NotificationDeliveryStatus.SENT) {
            logEntry.setSentAt(Instant.now());
        }

        Notification saved = notificationRepository.save(logEntry);
        String trackingNumber = saved.getParcelId() != null ? resolveTrackingNumber(saved.getParcelId()) : null;
        return NotificationMapper.toLogResponse(saved, trackingNumber);
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
    public NotificationResponse markAsUnread(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));

        notification.setRead(false);
        notification.setReadAt(null);
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
     * Consumes an incoming {@link ParcelStatusChangedEvent} from RabbitMQ, renders email and SMS
     * templates, dispatches across enabled channels, and persists delivery audit logs to PostgreSQL.
     */
    @Transactional
    public void processParcelStatusChange(ParcelStatusChangedEvent event) {
        if (event == null || event.parcelId() == null || event.newStatus() == null || event.newStatus().isBlank()) {
            throw new IllegalArgumentException("Invalid parcel status change event: missing parcelId or newStatus");
        }

        // Idempotency: skip if we already stored an in-app notification for this status history record
        if (event.statusHistoryId() != null &&
                notificationRepository.existsByStatusHistoryIdAndChannel(event.statusHistoryId(), NotificationChannel.IN_APP)) {
            log.debug("Notification for status history {} already processed, skipping", event.statusHistoryId());
            return;
        }

        ParcelDetails parcel = parcelService.getParcel(event.parcelId());
        if (parcel == null) {
            throw new NotFoundException("Parcel not found for notification event: " + event.parcelId());
        }

        if (event.trackingNumber() != null && !event.trackingNumber().isBlank()) {
            trackingNumberCache.put(event.parcelId(), event.trackingNumber());
        } else if (parcel.trackingNumber() != null) {
            trackingNumberCache.put(event.parcelId(), parcel.trackingNumber());
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

        SystemSettingsView settings = resolveSystemSettings();
        String companyName = settings != null ? settings.companyName() : "SwiftDrop";
        String supportEmail = settings != null ? settings.contactEmail() : "support@swiftdrop.lk";
        String supportPhone = settings != null ? settings.contactPhone() : "+94 11 234 5678";

        String tracking = (event.trackingNumber() != null && !event.trackingNumber().isBlank())
                ? event.trackingNumber()
                : parcel.trackingNumber();

        NotificationTemplateService.RenderedNotification rendered = templateService.render(
                event.newStatus(),
                tracking,
                companyName,
                supportEmail,
                supportPhone
        );

        // 1. IN_APP notification log & delivery
        if (customerUserId != null && preferences.isInAppEnabled()) {
            Notification inAppNotification = new Notification();
            inAppNotification.setUserId(customerUserId);
            inAppNotification.setParcelId(event.parcelId());
            inAppNotification.setStatusHistoryId(event.statusHistoryId());
            inAppNotification.setChannel(NotificationChannel.IN_APP);
            inAppNotification.setRecipient("in-app:" + customerUserId);
            inAppNotification.setSubject(rendered.subject());
            inAppNotification.setMessage(templateService.buildMessage(event.newStatus(), tracking));
            inAppNotification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            inAppNotification.setSentAt(Instant.now());
            notificationRepository.save(inAppNotification);
        }

        // 2. EMAIL notification log & delivery
        if (preferences.isEmailEnabled()) {
            String recipientEmail = resolveRecipientEmail(customerUserId);
            if (recipientEmail != null && !recipientEmail.isBlank()) {
                Notification emailNotification = new Notification();
                emailNotification.setUserId(customerUserId);
                emailNotification.setParcelId(event.parcelId());
                emailNotification.setStatusHistoryId(event.statusHistoryId());
                emailNotification.setChannel(NotificationChannel.EMAIL);
                emailNotification.setRecipient(recipientEmail);
                emailNotification.setSubject(rendered.subject());
                emailNotification.setMessage(templateService.buildMessage(event.newStatus(), tracking));
                emailNotification.setDeliveryStatus(NotificationDeliveryStatus.PENDING);

                try {
                    emailService.send(recipientEmail, rendered.subject(), rendered.plainText(), rendered.htmlBody());
                    emailNotification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
                    emailNotification.setSentAt(Instant.now());
                } catch (Exception ex) {
                    log.error("Failed to dispatch email notification for parcel {}", tracking, ex);
                    emailNotification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
                }
                notificationRepository.save(emailNotification);
            }
        }

        // 3. SMS notification (Mock SMS) log & delivery
        if (preferences.isSmsEnabled()) {
            String recipientPhone = resolveRecipientPhone(customerUserId, parcel);
            if (recipientPhone != null && !recipientPhone.isBlank()) {
                Notification smsNotification = new Notification();
                smsNotification.setUserId(customerUserId);
                smsNotification.setParcelId(event.parcelId());
                smsNotification.setStatusHistoryId(event.statusHistoryId());
                smsNotification.setChannel(NotificationChannel.SMS);
                smsNotification.setRecipient(recipientPhone);
                smsNotification.setSubject(rendered.subject());
                smsNotification.setMessage(rendered.smsText());
                smsNotification.setDeliveryStatus(NotificationDeliveryStatus.PENDING);

                try {
                    smsService.send(recipientPhone, rendered.smsText(), tracking);
                    smsNotification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
                    smsNotification.setSentAt(Instant.now());
                } catch (Exception ex) {
                    log.warn("Failed to dispatch mock SMS notification for parcel {}: {}", tracking, ex.getMessage());
                    smsNotification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
                }
                notificationRepository.save(smsNotification);
            }
        }
    }

    /**
     * Records a failed notification log entry in the database when a parcel status event
     * lands in the Dead-Letter Queue (DLQ).
     */
    @Transactional
    public void recordDeadLetterFailure(ParcelStatusChangedEvent deadEvent) {
        if (deadEvent == null) {
            return;
        }
        Notification failedLog = new Notification();
        failedLog.setParcelId(deadEvent.parcelId());
        failedLog.setStatusHistoryId(deadEvent.statusHistoryId());
        failedLog.setChannel(NotificationChannel.IN_APP);
        failedLog.setRecipient("dlq:" + (deadEvent.eventId() != null ? deadEvent.eventId() : "unknown"));
        String tracking = deadEvent.trackingNumber() != null ? deadEvent.trackingNumber() : "UNKNOWN";
        failedLog.setSubject("Dead-Lettered Notification (" + tracking + ")");
        failedLog.setMessage("Notification event moved to dead-letter queue for status: "
                + (deadEvent.newStatus() != null ? deadEvent.newStatus() : "UNKNOWN"));
        failedLog.setDeliveryStatus(NotificationDeliveryStatus.FAILED);

        if (deadEvent.parcelId() != null) {
            try {
                ParcelDetails parcel = parcelService.getParcel(deadEvent.parcelId());
                if (parcel != null) {
                    failedLog.setUserId(parcel.customerUserId());
                }
            } catch (Exception ex) {
                log.debug("Could not resolve parcel owner for dead-lettered event {}", deadEvent.eventId());
            }
        }

        notificationRepository.save(failedLog);
    }

    private NotificationPreferences getOrCreatePreferences(UUID userId) {
        return preferencesRepository.findById(userId)
                .orElseGet(() -> preferencesRepository.save(new NotificationPreferences(userId)));
    }

    private boolean shouldNotifyForStatus(NotificationPreferences prefs, String status) {
        if (status == null) return false;
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "PICKED_UP" -> prefs.isNotifyOnPickedUp();
            case "IN_TRANSIT" -> prefs.isNotifyOnInTransit();
            case "OUT_FOR_DELIVERY" -> prefs.isNotifyOnOutForDelivery();
            case "DELIVERED" -> prefs.isNotifyOnDelivered();
            case "FAILED", "DELIVERY_ATTEMPTED" -> prefs.isNotifyOnFailedAttempt();
            default -> true;
        };
    }

    private String resolveRecipientEmail(UUID userId) {
        if (userId != null) {
            try {
                UserProfileResponse profile = userService.getProfile(userId);
                return profile != null ? profile.email() : null;
            } catch (Exception e) {
                log.debug("Could not resolve account email for userId: {}", userId);
            }
        }
        return null;
    }

    private String resolveRecipientPhone(UUID userId, ParcelDetails parcel) {
        if (userId != null) {
            try {
                UserProfileResponse profile = userService.getProfile(userId);
                if (profile != null && profile.phone() != null && !profile.phone().isBlank()) {
                    return profile.phone();
                }
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

    private SystemSettingsView resolveSystemSettings() {
        if (systemSettingsService == null) {
            return null;
        }
        try {
            return systemSettingsService.current();
        } catch (Exception e) {
            log.debug("System settings unavailable during notification dispatch, using defaults");
            return null;
        }
    }

    private static NotificationChannel parseChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            return NotificationChannel.IN_APP;
        }
        try {
            return NotificationChannel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported notification channel: " + raw);
        }
    }

    private static NotificationDeliveryStatus parseDeliveryStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return NotificationDeliveryStatus.SENT;
        }
        try {
            return NotificationDeliveryStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported notification delivery status: " + raw);
        }
    }
}
