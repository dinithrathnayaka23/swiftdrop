package com.swiftdrop.identity.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.ratelimit.RateLimit;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.identity.exception.InvalidVerificationTokenException;
import com.swiftdrop.notification.service.EmailService;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves that a user owns their email address. Password accounts start unverified: they can't sign
 * in, and Google sign-in won't link to them, until the emailed link is opened. Without this,
 * anyone could register someone else's address and keep a password on the account once the real
 * owner starts using it.
 */
@Slf4j
@Service
public class EmailVerificationService {

    private final UserService userService;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RateLimiter rateLimiter;
    private final String frontendBaseUrl;
    private final Duration tokenLifetime;
    private final RateLimit oneEmailPerCooldown;

    public EmailVerificationService(UserService userService, JwtService jwtService, EmailService emailService,
                                    RateLimiter rateLimiter,
                                    @Value("${swiftdrop.frontend.base-url}") String frontendBaseUrl,
                                    @Value("${swiftdrop.email-verification.expiry:86400}") long expirySeconds,
                                    @Value("${swiftdrop.email-verification.resend-cooldown:60}") long cooldownSeconds) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.frontendBaseUrl = frontendBaseUrl;
        this.tokenLifetime = Duration.ofSeconds(expirySeconds);
        this.oneEmailPerCooldown = new RateLimit(1, Duration.ofSeconds(cooldownSeconds));
    }

    /** Fingerprint of the normalised address: the link verifies only the address it was sent to. */
    static String emailFingerprint(String email) {
        return PasswordFingerprint.of("email:" + UserService.normalizeEmail(email));
    }

    /** Sends the confirmation link to a newly registered (unverified) account. */
    public void sendVerification(UserAccount user) {
        if (user.emailVerified()) {
            return;
        }
        if (!rateLimiter.hit(cooldownKey(user.email()), oneEmailPerCooldown).allowed()) {
            log.info("Verification email for user {} skipped: requested again within {}s", user.id(),
                    oneEmailPerCooldown.window().toSeconds());
            return;
        }
        String token = jwtService.generateEmailVerificationToken(user.id().toString(),
                emailFingerprint(user.email()), tokenLifetime);
        String link = frontendBaseUrl + "/verify-email?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        var body = AccountEmails.verify(user.fullName(), link, tokenLifetime.toHours());
        emailService.send(user.email(), AccountEmails.VERIFY_SUBJECT, body.text(), body.html());
        log.info("Verification email sent to user {}", user.id());
    }

    /**
     * Sends a new link if the address belongs to an unverified account. Silent otherwise (always
     * 202), so the endpoint can't be used to find out which emails are registered.
     */
    @Transactional(readOnly = true)
    public void resend(String email) {
        userService.findAccountByEmail(email)
                .filter(user -> !user.emailVerified())
                .ifPresent(this::sendVerification);
    }

    /**
     * Marks the address as verified.
     *
     * @return the account, so the caller can tell a merchant that approval is still pending
     */
    @Transactional
    public UserAccount confirm(String token) {
        var claims = jwtService.parseEmailVerificationToken(token)
                .orElseThrow(InvalidVerificationTokenException::new);
        UserAccount user;
        try {
            user = userService.getAccount(UUID.fromString(claims.userId()));
        } catch (IllegalArgumentException | NotFoundException ex) {
            throw new InvalidVerificationTokenException();
        }
        if (!emailFingerprint(user.email()).equals(claims.emailFingerprint())) {
            throw new InvalidVerificationTokenException(); // sent to an address the account no longer has
        }
        if (user.emailVerified()) {
            return user; // link opened twice: nothing to do
        }
        UserAccount verified = userService.markEmailVerified(user.id());
        rateLimiter.reset(cooldownKey(user.email()));
        log.info("Email verified for user {}", user.id());
        return verified;
    }

    /** True for a merchant whose business still awaits admin approval after verifying. */
    public static boolean awaitingApproval(UserAccount user) {
        return user.accountStatus() == AccountStatus.PENDING_APPROVAL;
    }

    private static String cooldownKey(String email) {
        return "email-verification:" + RateLimiter.anonymise(email);
    }
}
