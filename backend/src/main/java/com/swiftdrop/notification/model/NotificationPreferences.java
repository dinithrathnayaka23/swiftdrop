package com.swiftdrop.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Customer notification channel & milestone preferences.
 * Table {@code notification_preferences}.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreferences {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "notify_on_picked_up", nullable = false)
    private boolean notifyOnPickedUp = true;

    @Column(name = "notify_on_in_transit", nullable = false)
    private boolean notifyOnInTransit = false;

    @Column(name = "notify_on_out_for_delivery", nullable = false)
    private boolean notifyOnOutForDelivery = true;

    @Column(name = "notify_on_delivered", nullable = false)
    private boolean notifyOnDelivered = true;

    @Column(name = "notify_on_failed_attempt", nullable = false)
    private boolean notifyOnFailedAttempt = true;

    @Column(name = "marketing_emails", nullable = false)
    private boolean marketingEmails = false;

    @Column(name = "system_maintenance_alerts", nullable = false)
    private boolean systemMaintenanceAlerts = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationPreferences(UUID userId) {
        this.userId = userId;
    }
}
