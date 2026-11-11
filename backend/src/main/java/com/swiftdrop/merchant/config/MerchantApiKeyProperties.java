package com.swiftdrop.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for merchant API keys, under {@code swiftdrop.merchant.api-keys}.
 *
 * @param pepper        server-side secret mixed into every key hash (env API_KEY_PEPPER), at least
 *                      32 characters. A copy of the database alone is then not enough to check
 *                      guessed keys. Never commit a real value.
 * @param maxActiveKeys keys a merchant may hold at once; 2 lets a merchant switch to a new key
 *                      before retiring the old one
 */
@ConfigurationProperties(prefix = "swiftdrop.merchant.api-keys")
public record MerchantApiKeyProperties(String pepper, int maxActiveKeys) {

    public static final int MIN_PEPPER_LENGTH = 32;
    public static final int DEFAULT_MAX_ACTIVE_KEYS = 2;

    public MerchantApiKeyProperties {
        if (pepper == null || pepper.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException("swiftdrop.merchant.api-keys.pepper (env API_KEY_PEPPER) must be at least "
                    + MIN_PEPPER_LENGTH + " characters");
        }
        if (maxActiveKeys <= 0) {
            maxActiveKeys = DEFAULT_MAX_ACTIVE_KEYS;
        }
    }
}
