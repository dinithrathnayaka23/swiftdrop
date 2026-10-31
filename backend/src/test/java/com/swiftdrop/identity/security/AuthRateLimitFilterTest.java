package com.swiftdrop.identity.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimit;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.common.ratelimit.RateLimiter.Decision;
import com.swiftdrop.identity.config.AuthRateLimitProperties;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    private static final RateLimit LOGIN = new RateLimit(20, Duration.ofMinutes(1));
    private static final RateLimit REGISTRATION = new RateLimit(10, Duration.ofHours(1));
    private static final RateLimit RESET = new RateLimit(10, Duration.ofMinutes(15));
    private static final RateLimit SESSION = new RateLimit(120, Duration.ofMinutes(1));
    private static final RateLimit FAILURES = new RateLimit(5, Duration.ofMinutes(15));

    @Mock private RateLimiter rateLimiter;
    @Mock private HandlerExceptionResolver exceptionResolver;
    @Mock private FilterChain chain;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private AuthRateLimitFilter filter(boolean enabled) {
        return new AuthRateLimitFilter(rateLimiter,
                new AuthRateLimitProperties(enabled, LOGIN, REGISTRATION, RESET, RESET, SESSION, FAILURES, FAILURES),
                exceptionResolver);
    }

    private static MockHttpServletRequest post(String path) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/auth/login, login",
            "/api/v1/auth/google, login",
            "/api/v1/auth/register/customer, registration",
            "/api/v1/auth/register/merchant, registration",
            "/api/v1/auth/password-reset, password-reset",
            "/api/v1/auth/password-reset/confirm, password-reset",
            "/api/v1/auth/refresh, session",
            "/api/v1/auth/logout, session"})
    void countsEachEndpointPerClientIp(String path, String policy) throws Exception {
        when(rateLimiter.hit(eq("auth:" + policy + ":203.0.113.7"), any())).thenReturn(new Decision(true, Duration.ZERO));
        var request = post(path);

        filter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void overTheLimitTheRequestStopsWithA429() throws Exception {
        when(rateLimiter.hit("auth:login:203.0.113.7", LOGIN)).thenReturn(new Decision(false, Duration.ofSeconds(25)));
        var request = post("/api/v1/auth/login");

        filter(true).doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(exceptionResolver).resolveException(eq(request), eq(response), isNull(),
                argThat(ex -> ex instanceof RateLimitExceededException limit && limit.getRetryAfterSeconds() == 25));
    }

    @Test
    void readOnlyRequestsAndOtherPathsAreNotLimited() throws Exception {
        var config = new MockHttpServletRequest("GET", "/api/v1/auth/google/config");

        filter(true).doFilter(config, response, chain);
        filter(true).doFilter(post("/api/v1/users/me"), response, chain);

        verifyNoInteractions(rateLimiter);
    }

    @Test
    void canBeSwitchedOff() throws Exception {
        filter(false).doFilter(post("/api/v1/auth/login"), response, chain);

        verifyNoInteractions(rateLimiter);
    }
}
