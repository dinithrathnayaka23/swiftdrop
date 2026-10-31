package com.swiftdrop.common.ratelimit;

import java.time.Duration;

/**
 * At most {@code requests} within {@code window} (fixed window, starting at the first request).
 *
 * @param requests how many requests are allowed per window (at least 1)
 * @param window   window length, e.g. {@code 1m}, {@code 15m}, {@code 1h} in YAML
 */
public record RateLimit(int requests, Duration window) {

    public RateLimit {
        if (requests < 1) {
            throw new IllegalArgumentException("A rate limit must allow at least one request");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("A rate limit window must be positive");
        }
    }
}
