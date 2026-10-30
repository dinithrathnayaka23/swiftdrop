package com.swiftdrop.merchant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One attempt-tracked delivery of an event to a merchant webhook. Table {@code webhook_deliveries}. */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "webhook_delivery_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_id", nullable = false)
    private MerchantWebhook webhook;

    /** {@code parcels.parcel_id} (parcel module). */
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    /** {@code parcel_status_history.status_history_id} that triggered this delivery (parcel module). */
    @Column(name = "status_history_id", updatable = false)
    private UUID statusHistoryId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** Exact JSON body sent to the merchant. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private WebhookDeliveryStatus deliveryStatus = WebhookDeliveryStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    /** First part of the merchant's response body, for debugging. */
    @Column(name = "last_response_excerpt")
    private String lastResponseExcerpt;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
