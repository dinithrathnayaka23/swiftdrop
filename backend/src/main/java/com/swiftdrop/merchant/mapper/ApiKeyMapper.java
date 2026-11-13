package com.swiftdrop.merchant.mapper;

import com.swiftdrop.merchant.dto.response.ApiKeyResponse;
import com.swiftdrop.merchant.model.MerchantApiCredential;
import java.time.Instant;

public final class ApiKeyMapper {

    private ApiKeyMapper() {
    }

    /** {@code now} decides whether a key with an expiry date has expired yet. */
    public static ApiKeyResponse toResponse(MerchantApiCredential credential, Instant now) {
        return new ApiKeyResponse(credential.getId(), credential.getKeyIdentifier(), statusOf(credential, now),
                credential.getCreatedAt(), credential.getExpiresAt(), credential.getRevokedAt(),
                credential.getLastUsedAt());
    }

    /** Revoked wins over expired: revoking is the merchant's own decision and the more recent fact. */
    public static ApiKeyResponse.Status statusOf(MerchantApiCredential credential, Instant now) {
        if (credential.getRevokedAt() != null) {
            return ApiKeyResponse.Status.REVOKED;
        }
        if (credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(now)) {
            return ApiKeyResponse.Status.EXPIRED;
        }
        return ApiKeyResponse.Status.ACTIVE;
    }
}
