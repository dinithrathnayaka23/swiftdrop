package com.swiftdrop.settings.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.settings.exception.MaintenanceModeException;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeFilterTest {

    @Mock private SystemSettingsService settings;
    @Mock private HandlerExceptionResolver exceptionResolver;

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void run(String method, String path) throws Exception {
        new MaintenanceModeFilter(settings, exceptionResolver).doFilter(new MockHttpServletRequest(method, path),
                response, chain);
    }

    private static void signInAs(Role role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-1", null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @Test
    void doesNothingWhenMaintenanceIsOff() throws Exception {
        when(settings.maintenanceMode()).thenReturn(false);

        run("GET", "/api/v1/users/me");

        assertThat(chain.getRequest()).isNotNull(); // passed on
        verifyNoInteractions(exceptionResolver);
    }

    @Test
    void refusesEveryoneButTheAdminDuringMaintenance() throws Exception {
        when(settings.maintenanceMode()).thenReturn(true);
        signInAs(Role.CUSTOMER);

        run("GET", "/api/v1/users/me");

        assertThat(chain.getRequest()).isNull(); // stopped
        verify(exceptionResolver).resolveException(any(), eq(response), isNull(), any(MaintenanceModeException.class));
    }

    @Test
    void refusesVisitorsWhoAreNotSignedIn() throws Exception {
        when(settings.maintenanceMode()).thenReturn(true);

        run("POST", "/api/v1/auth/register/customer");

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void letsTheAdminWorkDuringMaintenance() throws Exception {
        when(settings.maintenanceMode()).thenReturn(true);
        signInAs(Role.DISPATCHER_ADMIN);

        run("PUT", "/api/v1/admin/settings");

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(exceptionResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/health", "/api/v1/public/settings", "/api/v1/auth/login",
            "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/google/config"})
    void keepsTheWayToTheAdminPortalOpen(String path) throws Exception {
        when(settings.maintenanceMode()).thenReturn(true);

        run("POST", path);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsCorsPreflightThrough() throws Exception {
        when(settings.maintenanceMode()).thenReturn(true);

        run("OPTIONS", "/api/v1/users/me");

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doesNotLockEveryoneOutWhenTheSettingsCannotBeRead() throws Exception {
        when(settings.maintenanceMode()).thenThrow(new IllegalStateException("database down"));

        run("GET", "/api/v1/users/me");

        assertThat(chain.getRequest()).isNotNull();
    }
}
