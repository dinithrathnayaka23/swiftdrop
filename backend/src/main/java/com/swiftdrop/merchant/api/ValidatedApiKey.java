package com.swiftdrop.merchant.api;

import java.util.UUID;

/**
 * A merchant API key that passed {@link ApiKeyApi#validate}. Holds identifiers only, never the key
 * or its hash.
 *
 * @param merchantId      the approved merchant that owns the key; scope every request to it
 * @param apiCredentialId the key's {@code merchant_api_credentials} id, safe to log
 * @param keyId           the key's public identifier, also the OAuth {@code client_id}
 */
public record ValidatedApiKey(UUID merchantId, UUID apiCredentialId, String keyId) {
}
