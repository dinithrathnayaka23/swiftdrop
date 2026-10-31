package com.swiftdrop.identity.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.identity.dto.response.AuthResponse;
import com.swiftdrop.identity.exception.AccountNotActiveException;
import com.swiftdrop.identity.security.SessionStore;
import com.swiftdrop.user.dto.UserAccount;
import com.swiftdrop.user.service.UserService;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in sessions: issuing them, renewing them (refresh token rotation) and ending them.
 *
 * <p>A session lives in Redis ({@link SessionStore}) and its refresh token names it, so ending
 * the session (sign-out, password reset) invalidates the token immediately. Access tokens are
 * short-lived and are not checked against Redis on every request.
 */
@Slf4j
@Service
public class SessionService {

    static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";

    private final UserService userService;
    private final JwtService jwtService;
    private final SessionStore sessionStore;
    /** Session length with "Remember me" (sliding: renewed on every refresh). */
    private final Duration rememberMeLifetime;
    /** Session length without "Remember me" (the browser also forgets the cookie on close). */
    private final Duration sessionLifetime;
    private final Duration rotationGrace;

    public SessionService(UserService userService, JwtService jwtService, SessionStore sessionStore,
                          @Value("${swiftdrop.auth.refresh-token.remember-me-expiry}") long rememberMeSeconds,
                          @Value("${swiftdrop.auth.refresh-token.session-expiry}") long sessionSeconds,
                          @Value("${swiftdrop.auth.refresh-token.rotation-grace:30s}") Duration rotationGrace) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.sessionStore = sessionStore;
        this.rememberMeLifetime = Duration.ofSeconds(rememberMeSeconds);
        this.sessionLifetime = Duration.ofSeconds(sessionSeconds);
        this.rotationGrace = rotationGrace;
    }

    /**
     * 403 unless the account may sign in: the email must be verified, and the account active (not a
     * merchant awaiting approval, not disabled).
     */
    static void requireCanSignIn(UserAccount user) {
        if (!user.emailVerified()) {
            throw AccountNotActiveException.emailNotVerified();
        }
        switch (user.accountStatus()) {
            case ACTIVE -> { }
            case PENDING_APPROVAL -> throw AccountNotActiveException.pendingApproval();
            case SUSPENDED, DEACTIVATED -> throw AccountNotActiveException.disabled();
        }
    }

    /** Starts a session for a user who has just proven who they are. */
    public IssuedSession issue(UserAccount user, boolean rememberMe) {
        Duration lifetime = rememberMe ? rememberMeLifetime : sessionLifetime;
        String userId = user.id().toString();
        String sessionId = sessionStore.create(user.id(), lifetime);
        String accessToken = jwtService.generateToken(userId, Set.of(user.role()));
        String refreshToken = jwtService.generateRefreshToken(userId, sessionId, PasswordFingerprint.of(user),
                rememberMe, lifetime);
        var response = new AuthResponse(accessToken, jwtService.accessTokenLifetime().toSeconds(), rememberMe,
                new AuthResponse.AuthUser(userId, user.fullName(), user.role()));
        return new IssuedSession(response, refreshToken, lifetime);
    }

    /**
     * Swaps a refresh token for a new session (rotation): the old one stops working after a short
     * grace period. Fails if the token is missing, invalid or expired, its session was ended, the
     * password changed since, or the account can no longer sign in.
     */
    @Transactional(readOnly = true)
    public IssuedSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException(SESSION_EXPIRED);
        }
        var claims = jwtService.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException(SESSION_EXPIRED));
        UserAccount user;
        try {
            user = userService.getAccount(UUID.fromString(claims.userId()));
        } catch (IllegalArgumentException | NotFoundException ex) {
            // Malformed user ID, or the account was deleted since the token was issued.
            throw new UnauthorizedException(SESSION_EXPIRED);
        }
        if (!sessionStore.isActive(claims.sessionId(), user.id())) {
            throw new UnauthorizedException(SESSION_EXPIRED); // signed out, or the session expired
        }
        if (!PasswordFingerprint.of(user).equals(claims.passwordFingerprint())) {
            sessionStore.revoke(claims.sessionId());
            throw new UnauthorizedException(SESSION_EXPIRED); // password changed since sign-in
        }
        try {
            requireCanSignIn(user);
        } catch (AccountNotActiveException ex) {
            sessionStore.revoke(claims.sessionId());
            throw ex;
        }
        sessionStore.retire(claims.sessionId(), rotationGrace);
        return issue(user, claims.rememberMe());
    }

    /** Ends the session the refresh token belongs to. Invalid or missing tokens are ignored. */
    public void signOut(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        jwtService.parseRefreshToken(refreshToken).ifPresent(claims -> {
            sessionStore.revoke(claims.sessionId());
            log.info("User {} signed out", claims.userId());
        });
    }

    /** Ends every session of the user, on every device. */
    public void signOutEverywhere(UUID userId) {
        sessionStore.revokeAll(userId);
        log.info("All sessions of user {} ended", userId);
    }
}
