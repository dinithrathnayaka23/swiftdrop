package com.swiftdrop.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for merchant webhook settings and secret encryption. */
@ConfigurationProperties(prefix = "swiftdrop.merchant.webhooks")
public record MerchantWebhookProperties(String secretKey, boolean allowPrivateHosts) {

    public static final int MIN_SECRET_KEY_LENGTH = 32;

    public MerchantWebhookProperties {
        if (secretKey == null || secretKey.length() < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalStateException("swiftdrop.merchant.webhooks.secret-key "
                    + "(env WEBHOOK_SECRET_KEY) must be at least " + MIN_SECRET_KEY_LENGTH + " characters");
        }
    }
}
