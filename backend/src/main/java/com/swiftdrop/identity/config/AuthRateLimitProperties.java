package com.swiftdrop.identity.config;

import com.swiftdrop.common.ratelimit.RateLimit;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse protection for the authentication endpoints ({@code swiftdrop.rate-limit.*}).
 *
 * @param enabled                     turn every limit below on or off (e.g. for load tests)
 * @param login                       sign-in requests per client IP (password and Google)
 * @param registration                sign-up requests per client IP
 * @param passwordReset               reset requests and confirmations per client IP
 * @param emailVerification           confirmation-link resends and confirmations per client IP
 * @param session                     refresh and sign-out requests per client IP
 * @param failedLoginsPerAccountAndIp wrong passwords for one account from one IP before a pause
 * @param failedLoginsPerAccount      wrong passwords for one account from anywhere (slows down
 *                                    attacks spread over many IPs)
 */
@ConfigurationProperties(prefix = "swiftdrop.rate-limit")
public record AuthRateLimitProperties(
        boolean enabled,
        RateLimit login,
        RateLimit registration,
        RateLimit passwordReset,
        RateLimit emailVerification,
        RateLimit session,
        RateLimit failedLoginsPerAccountAndIp,
        RateLimit failedLoginsPerAccount) {
}
