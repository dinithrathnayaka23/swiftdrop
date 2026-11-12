package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * An API key as listed in the merchant portal. Never contains the key itself, only its public
 * {@code keyId}, so the merchant can tell keys apart.
 */
public record ApiKeyResponse(
        UUID apiCredentialId,
        String keyId,
        Status status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastUsedAt) {

    public enum Status {
        ACTIVE,
        REVOKED,
        EXPIRED
    }
}
