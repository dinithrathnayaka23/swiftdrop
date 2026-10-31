package com.swiftdrop.identity.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimit;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.identity.config.AuthRateLimitProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginAttemptGuardTest {

    private static final RateLimit PER_ACCOUNT_AND_IP = new RateLimit(5, Duration.ofMinutes(15));
    private static final RateLimit PER_ACCOUNT = new RateLimit(20, Duration.ofHours(1));
    private static final String IP = "203.0.113.7";
    private static final String ACCOUNT_AND_IP_KEY =
            "login-fail:account-ip:" + RateLimiter.anonymise("nimal@example.com") + ":" + IP;
    private static final String ACCOUNT_KEY = "login-fail:account:" + RateLimiter.anonymise("nimal@example.com");

    @Mock private RateLimiter rateLimiter;

    private LoginAttemptGuard guard(boolean enabled) {
        var limit = new RateLimit(100, Duration.ofMinutes(1));
        return new LoginAttemptGuard(rateLimiter, new AuthRateLimitProperties(enabled, limit, limit, limit, limit, limit,
                PER_ACCOUNT_AND_IP, PER_ACCOUNT));
    }

    @Test
    void allowsSignInBelowBothLimits() {
        when(rateLimiter.blockedFor(ACCOUNT_AND_IP_KEY, PER_ACCOUNT_AND_IP)).thenReturn(Optional.empty());
        when(rateLimiter.blockedFor(ACCOUNT_KEY, PER_ACCOUNT)).thenReturn(Optional.empty());

        assertThatCode(() -> guard(true).checkAllowed("nimal@example.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void pausesWithTheLongestWaitOfTheLimitsThatWereHit() {
        when(rateLimiter.blockedFor(ACCOUNT_AND_IP_KEY, PER_ACCOUNT_AND_IP))
                .thenReturn(Optional.of(Duration.ofMinutes(3)));
        when(rateLimiter.blockedFor(ACCOUNT_KEY, PER_ACCOUNT)).thenReturn(Optional.of(Duration.ofMinutes(40)));

        assertThatThrownBy(() -> guard(true).checkAllowed("nimal@example.com", IP))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many failed sign-in attempts. Please try again in 40 minutes.");
    }

    @Test
    void failuresCountAgainstBothLimitsAndSuccessClearsThem() {
        LoginAttemptGuard guard = guard(true);

        guard.recordFailure("nimal@example.com", IP);
        guard.recordSuccess("Nimal@Example.com", IP);

        verify(rateLimiter).hit(ACCOUNT_AND_IP_KEY, PER_ACCOUNT_AND_IP);
        verify(rateLimiter).hit(ACCOUNT_KEY, PER_ACCOUNT);
        verify(rateLimiter).reset(ACCOUNT_AND_IP_KEY);
        verify(rateLimiter).reset(ACCOUNT_KEY);
    }

    @Test
    void emailsNeverAppearInKeys() {
        guard(true).recordFailure("nimal@example.com", IP);

        verify(rateLimiter).hit(startsWith("login-fail:account:"), eq(PER_ACCOUNT));
        verify(rateLimiter, never()).hit(contains("nimal"), any());
    }

    @Test
    void doesNothingWhenRateLimitingIsOff() {
        LoginAttemptGuard guard = guard(false);

        guard.checkAllowed("nimal@example.com", IP);
        guard.recordFailure("nimal@example.com", IP);
        guard.recordSuccess("nimal@example.com", IP);

        verifyNoInteractions(rateLimiter);
    }
}
