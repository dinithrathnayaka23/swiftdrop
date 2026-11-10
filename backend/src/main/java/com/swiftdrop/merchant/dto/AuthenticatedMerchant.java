package com.swiftdrop.merchant.dto;

import java.util.UUID;

/**
 * The merchant behind a valid API key. The order API scopes everything to {@code merchantId};
 * {@code apiCredentialId} identifies the key in logs without exposing it.
 */
public record AuthenticatedMerchant(UUID merchantId, UUID apiCredentialId) {
}
