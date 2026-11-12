package com.swiftdrop.merchant.dto;

import java.util.UUID;

/**
 * The merchant behind a valid API key. The order API scopes everything to {@code merchantId};
 * {@code apiCredentialId} identifies the key in logs without exposing it, and {@code keyId} is
 * the public client id used by the OAuth client-credentials flow.
 */
public record AuthenticatedMerchant(UUID merchantId, UUID apiCredentialId, String keyId) {

    /** Compatibility constructor for callers that only need the merchant and credential ids. */
    public AuthenticatedMerchant(UUID merchantId, UUID apiCredentialId) {
        this(merchantId, apiCredentialId, null);
    }
}
