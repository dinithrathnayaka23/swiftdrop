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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * An API key a merchant uses for the order API. Only a hash of the key is stored; the raw key
 * is shown to the merchant once. Table {@code merchant_api_credentials}.
 */
@Entity
@Table(name = "merchant_api_credentials")
@Getter
@Setter
@NoArgsConstructor
public class MerchantApiCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "api_credential_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    /** Public, non-secret part of the key used to find this row. */
    @Column(name = "key_identifier", nullable = false, length = 100)
    private String keyIdentifier;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
