package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    private static final String EMAIL = "admin@swiftdrop.lk";
    private static final String PASSWORD = "admin-pass-2026";

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;

    private final UUID adminId = UUID.randomUUID();

    private AdminAccountInitializer initializer(String email, String password) {
        return new AdminAccountInitializer(userService, passwordEncoder, email, password, "SwiftDrop Admin");
    }

    private UserAccount admin(String email, String name, String hash, AccountStatus status, boolean verified) {
        return new UserAccount(adminId, name, email, hash, null, Role.DISPATCHER_ADMIN, status, verified);
    }

    @Test
    void createsTheAdminFromTheEnvironmentWhenThereIsNone() {
        when(userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN)).thenReturn(Optional.empty());
        when(userService.emailExists(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$admin");
        when(userService.createUser(any()))
                .thenReturn(admin(EMAIL, "SwiftDrop Admin", "$2a$admin", AccountStatus.ACTIVE, true));

        initializer(EMAIL, PASSWORD).run(null);

        verify(userService).createUser(NewUser.withPassword("SwiftDrop Admin", EMAIL, null,
                "$2a$admin", Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE).withVerifiedEmail());
    }

    @Test
    void leavesAnAdminThatAlreadyMatchesTheEnvironmentAlone() {
        when(userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN))
                .thenReturn(Optional.of(admin(EMAIL, "SwiftDrop Admin", "$2a$admin", AccountStatus.ACTIVE, true)));
        when(passwordEncoder.matches(PASSWORD, "$2a$admin")).thenReturn(true);

        initializer(EMAIL, PASSWORD).run(null);

        verify(userService, never()).createUser(any());
        verify(userService, never()).changeEmail(any(), anyString());
        verify(userService, never()).changePasswordHash(any(), anyString());
        verify(userService, never()).changeFullName(any(), anyString());
        verify(userService, never()).changeAccountStatus(any(), any());
    }

    @Test
    void updatesTheExistingAdminWhenTheEnvironmentChanged() {
        // Old email, old name, old password, suspended and unverified: everything follows the environment.
        when(userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN)).thenReturn(Optional.of(
                admin("old-admin@swiftdrop.lk", "Old Name", "$2a$old", AccountStatus.SUSPENDED, false)));
        when(userService.emailExists(EMAIL)).thenReturn(false);
        when(passwordEncoder.matches(PASSWORD, "$2a$old")).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$new");

        initializer(EMAIL, PASSWORD).run(null);

        verify(userService).changeEmail(adminId, EMAIL);
        verify(userService).changeFullName(adminId, "SwiftDrop Admin");
        verify(userService).changePasswordHash(adminId, "$2a$new");
        verify(userService).changeAccountStatus(adminId, AccountStatus.ACTIVE);
        verify(userService).markEmailVerified(adminId);
        verify(userService, never()).createUser(any()); // still the one admin, never a second
    }

    @Test
    void refusesToStartWhenTheAdminEmailBelongsToAnotherAccount() {
        when(userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN)).thenReturn(Optional.of(
                admin("old-admin@swiftdrop.lk", "SwiftDrop Admin", "$2a$admin", AccountStatus.ACTIVE, true)));
        when(userService.emailExists(EMAIL)).thenReturn(true); // e.g. a customer registered it

        assertThatThrownBy(() -> initializer(EMAIL, PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-admin account");
        verify(userService, never()).changeEmail(any(), anyString());
    }

    @Test
    void refusesToStartWithoutAdminCredentials() {
        assertThatThrownBy(() -> initializer("", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL and ADMIN_PASSWORD");
        assertThatThrownBy(() -> initializer(EMAIL, "short").run(null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(userService, passwordEncoder);
    }
}
