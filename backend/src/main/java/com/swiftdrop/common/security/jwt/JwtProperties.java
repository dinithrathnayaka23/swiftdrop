package com.swiftdrop.common.security.jwt;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

/**
 * Bound from {@code swiftdrop.jwt.*} (env: JWT_SECRET, JWT_EXPIRY).
 *
 * @param secret HMAC secret, at least 32 bytes. Never commit a real value.
 * @param expiry token lifetime; a plain number is read as seconds (e.g. 3600)
 */
@ConfigurationProperties(prefix = "swiftdrop.jwt")
public record JwtProperties(
        String secret,
        @DurationUnit(ChronoUnit.SECONDS) Duration expiry,
        String issuer) {
}
