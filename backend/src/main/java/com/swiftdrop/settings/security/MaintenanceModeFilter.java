package com.swiftdrop.settings.security;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.settings.exception.MaintenanceModeException;
import com.swiftdrop.settings.service.SystemSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * While maintenance mode is on, answers every API request with 503 MAINTENANCE_MODE, except for
 * the admin and the few endpoints needed to reach the admin portal (health, public settings, sign
 * in, session renewal, sign out). Runs after Spring Security, so the caller's role is known.
 * Customers, merchants and couriers can't sign in meanwhile (AuthService checks the portal).
 */
@Slf4j
public class MaintenanceModeFilter extends OncePerRequestFilter {

    static final Set<String> ALWAYS_ALLOWED = Set.of(
            ApiPaths.HEALTH,
            ApiPaths.PUBLIC_SETTINGS,
            ApiPaths.AUTH + "/login",
            ApiPaths.AUTH + "/refresh",
            ApiPaths.AUTH + "/logout",
            ApiPaths.AUTH + "/google/config");

    private final SystemSettingsService settings;
    private final HandlerExceptionResolver exceptionResolver;

    public MaintenanceModeFilter(SystemSettingsService settings, HandlerExceptionResolver exceptionResolver) {
        this.settings = settings;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (maintenanceOn() && !allowed(request) && !isAdmin()) {
            exceptionResolver.resolveException(request, response, null, new MaintenanceModeException());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean maintenanceOn() {
        try {
            return settings.maintenanceMode();
        } catch (RuntimeException ex) {
            // Settings unreadable (database down): don't lock everyone out because of it; the
            // request itself will report the outage.
            log.warn("Could not read maintenance mode; letting the request through", ex);
            return false;
        }
    }

    private static boolean allowed(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true; // CORS preflight
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALWAYS_ALLOWED.contains(path);
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Role.DISPATCHER_ADMIN.authority()::equals);
    }
}
