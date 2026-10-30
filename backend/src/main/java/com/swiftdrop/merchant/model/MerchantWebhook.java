package com.swiftdrop.merchant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A merchant's webhook endpoint subscribed to parcel events. Table {@code merchant_webhooks}. */
@Entity
@Table(name = "merchant_webhooks")
@Getter
@Setter
@NoArgsConstructor
public class MerchantWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "webhook_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "endpoint_url", nullable = false)
    private String endpointUrl;

    /** Event types this endpoint receives, e.g. {@code parcel.status.changed}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    private List<String> eventTypes = new ArrayList<>();

    /** Secret used to sign webhook payloads, stored encrypted. */
    @Column(name = "signing_secret_encrypted", nullable = false)
    private String signingSecretEncrypted;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
