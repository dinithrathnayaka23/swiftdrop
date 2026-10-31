package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.common.security.jwt.JwtService.RefreshClaims;
import com.swiftdrop.identity.exception.AccountNotActiveException;
import com.swiftdrop.identity.security.SessionStore;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Duration REMEMBER_ME = Duration.ofDays(30);
    private static final Duration SESSION = Duration.ofDays(1);
    private static final Duration GRACE = Duration.ofSeconds(30);

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private SessionStore sessionStore;

    private SessionService sessionService;
    private final UserAccount user = new UserAccount(UUID.randomUUID(), "Nimal Perera", "nimal@example.com",
            "$2a$hash", null, Role.CUSTOMER, AccountStatus.ACTIVE, true);

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(userService, jwtService, sessionStore, REMEMBER_ME.toSeconds(),
                SESSION.toSeconds(), GRACE);
    }

    private void stubIssuing() {
        when(sessionStore.create(any(), any())).thenReturn("new-session");
        when(jwtService.generateToken(anyString(), any())).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken(anyString(), anyString(), anyString(), anyBoolean(),
                any())).thenReturn("refresh-jwt");
        when(jwtService.accessTokenLifetime()).thenReturn(Duration.ofMinutes(15));
    }

    @Nested
    class Issue {

        @Test
        void browserSessionByDefault() {
            stubIssuing();

            IssuedSession issued = sessionService.issue(user, false);

            verify(sessionStore).create(user.id(), SESSION);
            verify(jwtService).generateToken(user.id().toString(), Set.of(Role.CUSTOMER));
            verify(jwtService).generateRefreshToken(user.id().toString(), "new-session",
                    PasswordFingerprint.of("$2a$hash"), false, SESSION);
            assertThat(issued.refreshToken()).isEqualTo("refresh-jwt");
            assertThat(issued.refreshLifetime()).isEqualTo(SESSION);
            assertThat(issued.response().token()).isEqualTo("access-jwt");
            assertThat(issued.response().expiresIn()).isEqualTo(900);
            assertThat(issued.response().user().displayName()).isEqualTo("Nimal Perera");
        }

        @Test
        void rememberMeLastsThirtyDays() {
            stubIssuing();

            IssuedSession issued = sessionService.issue(user, true);

            verify(sessionStore).create(user.id(), REMEMBER_ME);
            assertThat(issued.rememberMe()).isTrue();
        }

        @Test
        void googleOnlyAccountsAreBoundToTheirGoogleId() {
            stubIssuing();
            var googleUser = new UserAccount(user.id(), "G", "g@gmail.com", null, "google-sub", Role.CUSTOMER,
                    AccountStatus.ACTIVE, true);

            sessionService.issue(googleUser, false);

            verify(jwtService).generateRefreshToken(user.id().toString(), "new-session",
                    PasswordFingerprint.of("google:google-sub"), false, SESSION);
        }
    }

    @Nested
    class Refresh {

        private void validToken(boolean rememberMe) {
            when(jwtService.parseRefreshToken("refresh-jwt")).thenReturn(Optional.of(new RefreshClaims(
                    user.id().toString(), "old-session", PasswordFingerprint.of("$2a$hash"), rememberMe)));
            when(userService.getAccount(user.id())).thenReturn(user);
        }

        @Test
        void rotatesTheSessionAndKeepsRememberMe() {
            validToken(true);
            when(sessionStore.isActive("old-session", user.id())).thenReturn(true);
            stubIssuing();

            IssuedSession issued = sessionService.refresh("refresh-jwt");

            assertThat(issued.rememberMe()).isTrue();
            verify(sessionStore).retire("old-session", GRACE);
            verify(sessionStore).create(user.id(), REMEMBER_ME);
        }

        @Test
        void missingCookie() {
            assertThatThrownBy(() -> sessionService.refresh(null))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("session has expired");
            verifyNoInteractions(jwtService, sessionStore);
        }

        @Test
        void invalidOrExpiredToken() {
            when(jwtService.parseRefreshToken("garbage")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.refresh("garbage")).isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void signedOutSessionCannotBeRefreshed() {
            validToken(false);
            when(sessionStore.isActive("old-session", user.id())).thenReturn(false);

            assertThatThrownBy(() -> sessionService.refresh("refresh-jwt")).isInstanceOf(UnauthorizedException.class);
            verify(sessionStore, never()).create(any(), any());
        }

        @Test
        void passwordChangedSinceSignInEndsTheSession() {
            when(jwtService.parseRefreshToken(anyString())).thenReturn(Optional.of(new RefreshClaims(
                    user.id().toString(), "old-session", PasswordFingerprint.of("$2a$old-hash"), true)));
            when(userService.getAccount(user.id())).thenReturn(user);
            when(sessionStore.isActive("old-session", user.id())).thenReturn(true);

            assertThatThrownBy(() -> sessionService.refresh("refresh-jwt")).isInstanceOf(UnauthorizedException.class);
            verify(sessionStore).revoke("old-session");
        }

        @Test
        void suspendedAccountIsSignedOut() {
            var suspended = new UserAccount(user.id(), user.fullName(), user.email(), user.passwordHash(), null,
                    Role.CUSTOMER, AccountStatus.SUSPENDED, true);
            when(jwtService.parseRefreshToken(anyString())).thenReturn(Optional.of(new RefreshClaims(
                    user.id().toString(), "old-session", PasswordFingerprint.of("$2a$hash"), false)));
            when(userService.getAccount(user.id())).thenReturn(suspended);
            when(sessionStore.isActive("old-session", user.id())).thenReturn(true);

            assertThatThrownBy(() -> sessionService.refresh("refresh-jwt"))
                    .isInstanceOf(AccountNotActiveException.class);
            verify(sessionStore).revoke("old-session");
        }

        @Test
        void unconfirmedEmailCannotKeepASession() {
            var unconfirmed = new UserAccount(user.id(), user.fullName(), user.email(), user.passwordHash(), null,
                    Role.CUSTOMER, AccountStatus.ACTIVE, false);
            when(jwtService.parseRefreshToken(anyString())).thenReturn(Optional.of(new RefreshClaims(
                    user.id().toString(), "old-session", PasswordFingerprint.of("$2a$hash"), false)));
            when(userService.getAccount(user.id())).thenReturn(unconfirmed);
            when(sessionStore.isActive("old-session", user.id())).thenReturn(true);

            assertThatThrownBy(() -> sessionService.refresh("refresh-jwt"))
                    .isInstanceOf(AccountNotActiveException.class);
            verify(sessionStore).revoke("old-session");
        }

        @Test
        void deletedAccount() {
            UUID id = UUID.randomUUID();
            when(jwtService.parseRefreshToken(anyString()))
                    .thenReturn(Optional.of(new RefreshClaims(id.toString(), "s", "fp", false)));
            when(userService.getAccount(id)).thenThrow(new NotFoundException("User not found"));

            assertThatThrownBy(() -> sessionService.refresh("refresh-jwt")).isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void malformedUserId() {
            when(jwtService.parseRefreshToken(anyString()))
                    .thenReturn(Optional.of(new RefreshClaims("not-a-uuid", "s", "fp", false)));

            assertThatThrownBy(() -> sessionService.refresh("refresh-jwt")).isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    void signOutEndsThatSessionOnly() {
        when(jwtService.parseRefreshToken("refresh-jwt")).thenReturn(Optional.of(
                new RefreshClaims(user.id().toString(), "this-device", "fp", false)));

        sessionService.signOut("refresh-jwt");

        verify(sessionStore).revoke("this-device");
    }

    @Test
    void signOutWithoutAValidCookieDoesNothing() {
        when(jwtService.parseRefreshToken("expired")).thenReturn(Optional.empty());

        sessionService.signOut(null);
        sessionService.signOut("expired");

        verify(sessionStore, never()).revoke(anyString());
    }

    @Test
    void signOutEverywhereEndsEverySession() {
        sessionService.signOutEverywhere(user.id());

        verify(sessionStore).revokeAll(user.id());
    }
}
