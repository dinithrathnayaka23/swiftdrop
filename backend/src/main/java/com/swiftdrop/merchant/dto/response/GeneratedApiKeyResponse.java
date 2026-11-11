package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A newly created API key. The only response that ever contains {@code apiKey}: it is not stored
 * and can't be shown again, so the merchant must copy it now.
 */
public record GeneratedApiKeyResponse(
        UUID apiCredentialId,
        String keyId,
        String apiKey,
        Instant createdAt) {
}
