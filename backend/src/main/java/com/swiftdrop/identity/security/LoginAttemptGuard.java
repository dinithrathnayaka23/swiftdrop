package com.swiftdrop.identity.security;

import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.identity.config.AuthRateLimitProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Pauses password sign-in for an account after too many wrong passwords: per account and IP
 * (one attacker), and per account overall (attacks spread over many IPs). A successful sign-in
 * clears the counters. Emails are stored only as a one-way hash.
 */
@Component
public class LoginAttemptGuard {

    private static final String MESSAGE = "Too many failed sign-in attempts.";

    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties properties;

    public LoginAttemptGuard(RateLimiter rateLimiter, AuthRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /** Throws 429 if this account is paused for this IP. Call before checking the password. */
    public void checkAllowed(String email, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        Optional<Duration> wait = Stream.of(
                        rateLimiter.blockedFor(accountAndIpKey(email, clientIp), properties.failedLoginsPerAccountAndIp()),
                        rateLimiter.blockedFor(accountKey(email), properties.failedLoginsPerAccount()))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
        if (wait.isPresent()) {
            throw new RateLimitExceededException(MESSAGE, wait.get());
        }
    }

    public void recordFailure(String email, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        rateLimiter.hit(accountAndIpKey(email, clientIp), properties.failedLoginsPerAccountAndIp());
        rateLimiter.hit(accountKey(email), properties.failedLoginsPerAccount());
    }

    public void recordSuccess(String email, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        rateLimiter.reset(accountAndIpKey(email, clientIp));
        rateLimiter.reset(accountKey(email));
    }

    private static String accountAndIpKey(String email, String clientIp) {
        return "login-fail:account-ip:" + RateLimiter.anonymise(email) + ":" + clientIp;
    }

    private static String accountKey(String email) {
        return "login-fail:account:" + RateLimiter.anonymise(email);
    }
}
