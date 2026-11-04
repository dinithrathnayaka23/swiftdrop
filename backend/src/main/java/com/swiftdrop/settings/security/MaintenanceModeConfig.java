package com.swiftdrop.settings.security;

import com.swiftdrop.settings.service.SystemSettingsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Registers the maintenance-mode filter for the API, right after the Spring Security chain. */
@Configuration
public class MaintenanceModeConfig {

    /** Spring Boot registers the Spring Security filter chain at order -100. */
    private static final int SECURITY_FILTER_ORDER = -100;

    @Bean
    public FilterRegistrationBean<MaintenanceModeFilter> maintenanceModeFilter(
            SystemSettingsService settings,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        var registration = new FilterRegistrationBean<>(new MaintenanceModeFilter(settings, exceptionResolver));
        registration.addUrlPatterns("/api/*");
        // After the security chain, so the signed-in user's role is known.
        registration.setOrder(SECURITY_FILTER_ORDER + 10);
        return registration;
    }
}
