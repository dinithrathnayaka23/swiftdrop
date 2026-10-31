package com.swiftdrop.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.swiftdrop.common.security.jwt.JwtService.EmailVerificationClaims;
import com.swiftdrop.identity.exception.InvalidVerificationTokenException;
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

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final RateLimit ONE_PER_MINUTE = new RateLimit(1, Duration.ofSeconds(60));

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;
    @Mock private RateLimiter rateLimiter;

    private EmailVerificationService service;
    private final UUID id = UUID.randomUUID();
    private final UserAccount unverified = new UserAccount(id, "Nimal Perera", "nimal@example.com", "$2a$hash", null,
            Role.CUSTOMER, AccountStatus.ACTIVE, false);
    private final UserAccount verified = new UserAccount(id, "Nimal Perera", "nimal@example.com", "$2a$hash", null,
            Role.CUSTOMER, AccountStatus.ACTIVE, true);
    private final String cooldownKey = "email-verification:" + RateLimiter.anonymise("nimal@example.com");
    private final String fingerprint = EmailVerificationService.emailFingerprint("nimal@example.com");

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(userService, jwtService, emailService, rateLimiter,
                "http://localhost:3000", 86_400, 60);
    }

    private void cooldown(boolean elapsed) {
        when(rateLimiter.hit(cooldownKey, ONE_PER_MINUTE))
                .thenReturn(new Decision(elapsed, elapsed ? Duration.ZERO : Duration.ofSeconds(30)));
    }

    @Test
    void emailsALinkBoundToTheAddress() {
        cooldown(true);
        when(jwtService.generateEmailVerificationToken(id.toString(), fingerprint, Duration.ofHours(24)))
                .thenReturn("a.b+c");

        service.sendVerification(unverified);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("nimal@example.com"), eq(AccountEmails.VERIFY_SUBJECT), text.capture(),
                anyString());
        assertThat(text.getValue()).contains("http://localhost:3000/verify-email?token=a.b%2Bc");
    }

    @Test
    void fingerprintIgnoresCaseAndSpacesButHidesTheAddress() {
        assertThat(EmailVerificationService.emailFingerprint(" Nimal@Example.COM ")).isEqualTo(fingerprint)
                .doesNotContain("nimal");
    }

    @Test
    void sendsNothingToVerifiedAccountsOrWithinTheCooldown() {
        service.sendVerification(verified);
        verifyNoInteractions(rateLimiter, emailService);

        cooldown(false);
        service.sendVerification(unverified);
        verifyNoInteractions(emailService);
    }

    @Test
    void resendIsSilentForUnknownOrAlreadyVerifiedAddresses() {
        when(userService.findAccountByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(userService.findAccountByEmail("nimal@example.com")).thenReturn(Optional.of(verified));

        service.resend("ghost@example.com");
        service.resend("nimal@example.com");

        verifyNoInteractions(emailService, jwtService);
    }

    @Test
    void resendSendsANewLinkToAnUnverifiedAccount() {
        when(userService.findAccountByEmail("nimal@example.com")).thenReturn(Optional.of(unverified));
        cooldown(true);
        when(jwtService.generateEmailVerificationToken(anyString(), anyString(), any())).thenReturn("token");

        service.resend("nimal@example.com");

        verify(emailService).send(eq("nimal@example.com"), eq(AccountEmails.VERIFY_SUBJECT), anyString(), anyString());
    }

    @Test
    void validLinkVerifiesTheAddress() {
        when(jwtService.parseEmailVerificationToken("valid"))
                .thenReturn(Optional.of(new EmailVerificationClaims(id.toString(), fingerprint)));
        when(userService.getAccount(id)).thenReturn(unverified);
        when(userService.markEmailVerified(id)).thenReturn(verified);

        assertThat(service.confirm("valid").emailVerified()).isTrue();
        verify(rateLimiter).reset(cooldownKey);
    }

    @Test
    void openingTheLinkTwiceIsHarmless() {
        when(jwtService.parseEmailVerificationToken("valid"))
                .thenReturn(Optional.of(new EmailVerificationClaims(id.toString(), fingerprint)));
        when(userService.getAccount(id)).thenReturn(verified);

        assertThat(service.confirm("valid")).isEqualTo(verified);
        verify(userService, never()).markEmailVerified(any());
    }

    @Test
    void linkForAnotherAddressIsRejected() {
        when(jwtService.parseEmailVerificationToken("old"))
                .thenReturn(Optional.of(new EmailVerificationClaims(id.toString(),
                        EmailVerificationService.emailFingerprint("previous@example.com"))));
        when(userService.getAccount(id)).thenReturn(unverified);

        assertThatThrownBy(() -> service.confirm("old")).isInstanceOf(InvalidVerificationTokenException.class);
        verify(userService, never()).markEmailVerified(any());
    }

    @Test
    void invalidExpiredOrOrphanedLinksAreRejected() {
        when(jwtService.parseEmailVerificationToken("bad")).thenReturn(Optional.empty());
        when(jwtService.parseEmailVerificationToken("deleted"))
                .thenReturn(Optional.of(new EmailVerificationClaims(id.toString(), fingerprint)));
        when(userService.getAccount(id)).thenThrow(new NotFoundException("User not found"));
        when(jwtService.parseEmailVerificationToken("garbled"))
                .thenReturn(Optional.of(new EmailVerificationClaims("not-a-uuid", fingerprint)));

        assertThatThrownBy(() -> service.confirm("bad")).isInstanceOf(InvalidVerificationTokenException.class);
        assertThatThrownBy(() -> service.confirm("deleted")).isInstanceOf(InvalidVerificationTokenException.class);
        assertThatThrownBy(() -> service.confirm("garbled")).isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void merchantsStillAwaitApprovalAfterVerifying() {
        var merchant = new UserAccount(id, "Shop", "shop@example.com", "$2a$h", null, Role.MERCHANT,
                AccountStatus.PENDING_APPROVAL, true);

        assertThat(EmailVerificationService.awaitingApproval(merchant)).isTrue();
        assertThat(EmailVerificationService.awaitingApproval(verified)).isFalse();
    }
}
