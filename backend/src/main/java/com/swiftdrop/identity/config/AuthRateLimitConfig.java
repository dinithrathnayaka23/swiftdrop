package com.swiftdrop.identity.config;

import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.identity.security.AuthRateLimitFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Registers the auth rate-limit filter ahead of Spring Security, for /api/v1/auth/* only. */
@Configuration
public class AuthRateLimitConfig {

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(
            RateLimiter rateLimiter, AuthRateLimitProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        var registration = new FilterRegistrationBean<>(
                new AuthRateLimitFilter(rateLimiter, properties, exceptionResolver));
        registration.addUrlPatterns("/api/v1/auth/*");
        // Before the security chain (-100): rejected requests cost no token parsing or DB work.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
