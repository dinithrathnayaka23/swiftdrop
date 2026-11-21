package com.swiftdrop.merchant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API for merchant API keys (scope: Member 8, used by Member 9's order API and webhooks).
 * Other modules check keys through this interface only, never through the key service, entity or
 * repository.
 *
 * <p>Both methods treat a key the same way:
 * <ul>
 *   <li>Malformed, unknown, wrong, revoked or expired: empty, so the caller answers 401 without
 *       saying which.</li>
 *   <li>Valid, but the merchant is no longer approved: throws
 *       {@link com.swiftdrop.merchant.exception.MerchantNotApprovedException} (403).</li>
 *   <li>Valid: the key's {@code last_used_at} is updated, at most once a minute.</li>
 * </ul>
 * The raw key is never logged or stored; only its hash is compared.
 */
public interface ApiKeyApi {

    /** Checks a raw key, such as the {@code X-API-Key} header or an OAuth {@code client_secret}. */
    Optional<ValidatedApiKey> validate(String apiKey);

    /** The approved merchant that owns this key. */
    Optional<MerchantSummary> getMerchantForKey(String apiKey);

    /**
     * Checks the key behind an issued OAuth access token by its id, with the same rules as
     * {@link #validate(String)}: empty once the key is revoked or expired (or isn't this merchant's),
     * and 403 once the merchant is no longer approved. So a token stops working with its key.
     */
    Optional<ValidatedApiKey> validateCredential(UUID merchantId, UUID apiCredentialId);
}
