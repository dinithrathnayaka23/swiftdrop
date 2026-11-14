package com.swiftdrop.merchant.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Retry delays for failed merchant webhook deliveries, ordered by retry number. */
@ConfigurationProperties(prefix = "swiftdrop.merchant.webhooks.retry")
public record WebhookRetryProperties(List<Duration> backoff) {

    public WebhookRetryProperties {
        if (backoff == null || backoff.isEmpty()) {
            throw new IllegalStateException("At least one webhook retry backoff duration is required");
        }
        backoff = List.copyOf(backoff);
    }
}
