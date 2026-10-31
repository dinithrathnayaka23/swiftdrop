package com.swiftdrop.identity.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.ratelimit.RateLimit;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.identity.exception.InvalidResetTokenException;
import com.swiftdrop.notification.service.EmailService;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.service.UserService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" flow without extra tables:
 * <ol>
 *   <li>{@link #requestReset}: emails a link carrying a signed, short-lived JWT bound to a fingerprint
 *       of the current password hash. Same response whether or not the account exists.</li>
 *   <li>{@link #confirmReset}: checks the token, sets the new password (which also invalidates the
 *       link), signs the user out on every device and sends a "password changed" notice.</li>
 * </ol>
 * At most one reset email per address per cooldown period (kept in Redis, so it holds across
 * restarts and backend instances), to stop inbox flooding.
 * <p>Not for the admin: its password is ADMIN_PASSWORD from the environment ({@link AdminAccountInitializer}).
 */
@Slf4j
@Service
public class PasswordResetService {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SessionService sessionService;
    private final RateLimiter rateLimiter;
    private final String frontendBaseUrl;
    private final Duration tokenLifetime;
    private final RateLimit oneEmailPerCooldown;

    public PasswordResetService(UserService userService, JwtService jwtService, PasswordEncoder passwordEncoder,
                                EmailService emailService, SessionService sessionService, RateLimiter rateLimiter,
                                @Value("${swiftdrop.frontend.base-url}") String frontendBaseUrl,
                                @Value("${swiftdrop.password-reset.expiry}") long expirySeconds,
                                @Value("${swiftdrop.password-reset.resend-cooldown:60}") long cooldownSeconds) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.sessionService = sessionService;
        this.rateLimiter = rateLimiter;
        this.frontendBaseUrl = frontendBaseUrl;
        this.tokenLifetime = Duration.ofSeconds(expirySeconds);
        this.oneEmailPerCooldown = new RateLimit(1, Duration.ofSeconds(cooldownSeconds));
    }

    /**
     * Emails a reset link if the account exists and has a password. Silent otherwise, so emails
     * can't be enumerated. The email is sent in the background after the transaction: the response
     * time doesn't reveal whether an account exists either.
     */
    @Transactional(readOnly = true)
    public void requestReset(String email) {
        userService.findAccountByEmail(email)
                .filter(UserAccount::hasPassword)
                .filter(PasswordResetService::resettable)
                .filter(this::cooldownElapsed)
                .ifPresent(this::sendResetEmail);
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        var claims = jwtService.parsePasswordResetToken(token).orElseThrow(InvalidResetTokenException::new);
        UserAccount user;
        try {
            user = userService.getAccount(UUID.fromString(claims.userId()));
        } catch (IllegalArgumentException | NotFoundException ex) {
            // Malformed user ID, or the account was deleted since the link was sent.
            throw new InvalidResetTokenException();
        }
        if (!user.hasPassword() || !resettable(user)
                || !PasswordFingerprint.of(user.passwordHash()).equals(claims.passwordFingerprint())) {
            throw new InvalidResetTokenException(); // already used: the password has changed since
        }
        userService.changePasswordHash(user.id(), passwordEncoder.encode(newPassword));
        // Opening the emailed link proves the user owns the address, so it counts as verification.
        userService.markEmailVerified(user.id());
        // Whoever knew the old password may still be signed in somewhere: end every session.
        sessionService.signOutEverywhere(user.id());
        rateLimiter.reset(cooldownKey(user)); // a new reset may be requested straight away
        log.info("Password reset completed for user {}", user.id());

        var body = AccountEmails.changed(user.fullName(), frontendBaseUrl + "/forgot-password");
        emailService.send(user.email(), AccountEmails.CHANGED_SUBJECT, body.text(), body.html());
    }

    /** The admin's password comes from the environment, so it can't be changed here. */
    private static boolean resettable(UserAccount user) {
        return user.role() != Role.DISPATCHER_ADMIN;
    }

    private boolean cooldownElapsed(UserAccount user) {
        if (rateLimiter.hit(cooldownKey(user), oneEmailPerCooldown).allowed()) {
            return true;
        }
        log.info("Password reset for user {} skipped: requested again within {}s", user.id(),
                oneEmailPerCooldown.window().toSeconds());
        return false;
    }

    private static String cooldownKey(UserAccount user) {
        return "password-reset:" + RateLimiter.anonymise(user.email());
    }

    private void sendResetEmail(UserAccount user) {
        String token = jwtService.generatePasswordResetToken(user.id().toString(),
                PasswordFingerprint.of(user.passwordHash()), tokenLifetime);
        String link = frontendBaseUrl + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        var body = AccountEmails.reset(user.fullName(), link, tokenLifetime.toMinutes());
        emailService.send(user.email(), AccountEmails.RESET_SUBJECT, body.text(), body.html());
        log.info("Password reset link sent to user {}", user.id());
    }
}
