package com.swiftdrop.identity.security;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimit;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.identity.config.AuthRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Per-IP request limits for {@code POST /api/v1/auth/**} (brute force, sign-up spam, reset-email
 * spam). Over the limit the request never reaches the controller: the standard 429 error is
 * returned through the global exception handler, with a Retry-After header.
 *
 * <p>Registered in {@link com.swiftdrop.identity.config.AuthRateLimitConfig}, not as a component.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final AuthRateLimitProperties properties;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthRateLimitFilter(RateLimiter rateLimiter, AuthRateLimitProperties properties,
                               HandlerExceptionResolver exceptionResolver) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.exceptionResolver = exceptionResolver;
    }

    private record Policy(String name, RateLimit limit) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !"POST".equals(request.getMethod()) || policyFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = policyFor(request);
        // With server.forward-headers-strategy=native this is the visitor's IP, not nginx's.
        var decision = rateLimiter.hit("auth:" + policy.name() + ":" + request.getRemoteAddr(), policy.limit());
        if (!decision.allowed()) {
            exceptionResolver.resolveException(request, response, null,
                    new RateLimitExceededException("Too many requests.", decision.retryAfter()));
            return;
        }
        chain.doFilter(request, response);
    }

    private Policy policyFor(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(ApiPaths.AUTH + "/")) {
            return null;
        }
        String endpoint = path.substring(ApiPaths.AUTH.length());
        if (endpoint.equals("/login") || endpoint.equals("/google")) {
            return new Policy("login", properties.login());
        }
        if (endpoint.startsWith("/register/")) {
            return new Policy("registration", properties.registration());
        }
        if (endpoint.startsWith("/password-reset")) {
            return new Policy("password-reset", properties.passwordReset());
        }
        if (endpoint.startsWith("/verify-email/")) {
            return new Policy("email-verification", properties.emailVerification());
        }
        if (endpoint.equals("/refresh") || endpoint.equals("/logout")) {
            return new Policy("session", properties.session());
        }
        return null;
    }
}
