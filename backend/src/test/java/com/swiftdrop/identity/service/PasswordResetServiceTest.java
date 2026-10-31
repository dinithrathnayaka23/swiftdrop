package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.ratelimit.RateLimit;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.common.ratelimit.RateLimiter.Decision;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.common.security.jwt.JwtService.PasswordResetClaims;
import com.swiftdrop.identity.exception.InvalidResetTokenException;
import com.swiftdrop.notification.service.EmailService;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String FRONTEND = "http://localhost:3000";
    private static final RateLimit ONE_PER_MINUTE = new RateLimit(1, Duration.ofSeconds(60));

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private SessionService sessionService;
    @Mock private RateLimiter rateLimiter;

    private PasswordResetService service;
    private final UserAccount user = new UserAccount(UUID.randomUUID(), "Nimal Perera", "nimal@example.com",
            "$2a$hash", null, Role.CUSTOMER, AccountStatus.ACTIVE, true);
    private final String cooldownKey = "password-reset:" + RateLimiter.anonymise("nimal@example.com");

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userService, jwtService, passwordEncoder, emailService, sessionService,
                rateLimiter, FRONTEND, 1800, 60);
    }

    private void cooldown(boolean elapsed) {
        when(rateLimiter.hit(cooldownKey, ONE_PER_MINUTE))
                .thenReturn(new Decision(elapsed, elapsed ? Duration.ZERO : Duration.ofSeconds(42)));
    }

    @Test
    void unknownEmailSendsNothingButDoesNotFail() {
        when(userService.findAccountByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verifyNoInteractions(emailService, jwtService, rateLimiter);
    }

    @Test
    void googleOnlyAccountHasNoPasswordToReset() {
        when(userService.findAccountByEmail(anyString())).thenReturn(Optional.of(
                new UserAccount(UUID.randomUUID(), "G", "g@gmail.com", null, "sub", Role.CUSTOMER,
                        AccountStatus.ACTIVE, true)));

        service.requestReset("g@gmail.com");

        verifyNoInteractions(emailService);
    }

    @Test
    void adminPasswordCannotBeResetByEmail() {
        UserAccount admin = new UserAccount(UUID.randomUUID(), "SwiftDrop Admin", "admin@swiftdrop.lk", "$2a$admin",
                null, Role.DISPATCHER_ADMIN, AccountStatus.ACTIVE, true);
        when(userService.findAccountByEmail("admin@swiftdrop.lk")).thenReturn(Optional.of(admin));
        when(jwtService.parsePasswordResetToken("admin-token")).thenReturn(Optional.of(
                new PasswordResetClaims(admin.id().toString(), PasswordFingerprint.of("$2a$admin"))));
        when(userService.getAccount(admin.id())).thenReturn(admin);

        service.requestReset("admin@swiftdrop.lk");

        verifyNoInteractions(emailService, rateLimiter);
        // Even a correctly signed link can't change it: the password comes from ADMIN_PASSWORD.
        assertThatThrownBy(() -> service.confirmReset("admin-token", "newpass2026"))
                .isInstanceOf(InvalidResetTokenException.class);
        verify(userService, never()).changePasswordHash(any(), anyString());
    }

    @Test
    void emailsAThirtyMinuteSingleUseLink() {
        when(userService.findAccountByEmail("nimal@example.com")).thenReturn(Optional.of(user));
        cooldown(true);
        when(jwtService.generatePasswordResetToken(user.id().toString(), PasswordFingerprint.of("$2a$hash"),
                Duration.ofMinutes(30))).thenReturn("a.b+c");

        service.requestReset("nimal@example.com");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("nimal@example.com"), eq(AccountEmails.RESET_SUBJECT), text.capture(),
                html.capture());
        assertThat(text.getValue()).contains(FRONTEND + "/reset-password?token=a.b%2Bc"); // URL-encoded
        assertThat(html.getValue()).contains("Nimal Perera");
    }

    @Test
    void secondRequestWithinTheCooldownSendsNothing() {
        when(userService.findAccountByEmail(anyString())).thenReturn(Optional.of(user));
        cooldown(false);

        service.requestReset("nimal@example.com");

        verifyNoInteractions(emailService, jwtService);
    }

    @Test
    void invalidOrExpiredToken() {
        when(jwtService.parsePasswordResetToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset("bad", "newpass2026"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void alreadyUsedLinkIsRejectedBecauseThePasswordChanged() {
        when(jwtService.parsePasswordResetToken("used")).thenReturn(Optional.of(
                new PasswordResetClaims(user.id().toString(), PasswordFingerprint.of("$2a$previous-hash"))));
        when(userService.getAccount(user.id())).thenReturn(user);

        assertThatThrownBy(() -> service.confirmReset("used", "newpass2026"))
                .isInstanceOf(InvalidResetTokenException.class);
        verify(userService, never()).changePasswordHash(any(), anyString());
        verifyNoInteractions(sessionService);
    }

    @Test
    void accountDeletedSinceTheLinkWasSent() {
        UUID id = UUID.randomUUID();
        when(jwtService.parsePasswordResetToken(anyString()))
                .thenReturn(Optional.of(new PasswordResetClaims(id.toString(), "fp")));
        when(userService.getAccount(id)).thenThrow(new NotFoundException("User not found"));

        assertThatThrownBy(() -> service.confirmReset("token", "newpass2026"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void validLinkChangesThePasswordEndsEverySessionAndNotifiesTheUser() {
        when(jwtService.parsePasswordResetToken("valid")).thenReturn(Optional.of(
                new PasswordResetClaims(user.id().toString(), PasswordFingerprint.of("$2a$hash"))));
        when(userService.getAccount(user.id())).thenReturn(user);
        when(passwordEncoder.encode("newpass2026")).thenReturn("$2a$new-hash");

        service.confirmReset("valid", "newpass2026");

        var order = inOrder(userService, sessionService, emailService);
        order.verify(userService).changePasswordHash(user.id(), "$2a$new-hash");
        order.verify(userService).markEmailVerified(user.id()); // the link proved they own the address
        order.verify(sessionService).signOutEverywhere(user.id());
        order.verify(emailService).send(eq("nimal@example.com"), eq(AccountEmails.CHANGED_SUBJECT), anyString(),
                anyString());
        verify(rateLimiter).reset(cooldownKey); // a new reset may be requested straight away
    }
}
