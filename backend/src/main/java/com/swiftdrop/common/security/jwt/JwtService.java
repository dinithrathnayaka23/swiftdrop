package com.swiftdrop.common.security.jwt;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.RequiredTypeException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates signed JWTs.
 *
 * <p>Four token types share the signing key but can't be swapped: every token carries a
 * {@code typ} claim (access, refresh, password_reset, email_verification), and each parser
 * requires its own type.
 * A refresh or reset token can therefore never be used as a bearer token, and vice versa.
 */
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";
    private static final String TYPE_CLAIM = "typ";
    private static final String ACCESS_TYPE = "access";
    private static final String PASSWORD_RESET_TYPE = "password_reset";
    private static final String EMAIL_VERIFICATION_TYPE = "email_verification";
    private static final String EMAIL_FINGERPRINT_CLAIM = "emf";
    private static final String REFRESH_TYPE = "refresh";
    private static final String FINGERPRINT_CLAIM = "pwf";
    private static final String REMEMBER_ME_CLAIM = "rem";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null
                || properties.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Lifetime of access tokens (JWT_EXPIRY). */
    public Duration accessTokenLifetime() {
        return properties.expiry();
    }

    /** Access token sent as {@code Authorization: Bearer ...}. Contains only the user id and roles. */
    public String generateToken(String userId, Collection<Role> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId)
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .claim(ROLES_CLAIM, roles.stream().map(Role::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiry())))
                .signWith(key)
                .compact();
    }

    /** Returns the user for a valid access token, or empty if it is invalid, expired or tampered with. */
    public Optional<AuthenticatedUser> parse(String token) {
        return claims(token, ACCESS_TYPE)
                .flatMap(claims -> rolesFrom(claims)
                        .map(roles -> new AuthenticatedUser(claims.getSubject(), roles)));
    }

    /**
     * Single-use password reset token. {@code passwordFingerprint} is derived from the user's current
     * password hash, so the token stops working as soon as the password changes.
     */
    public String generatePasswordResetToken(String userId, String passwordFingerprint, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId)
                .claim(TYPE_CLAIM, PASSWORD_RESET_TYPE)
                .claim(FINGERPRINT_CLAIM, passwordFingerprint)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    public Optional<PasswordResetClaims> parsePasswordResetToken(String token) {
        return claims(token, PASSWORD_RESET_TYPE)
                .map(claims -> new PasswordResetClaims(claims.getSubject(),
                        claims.get(FINGERPRINT_CLAIM, String.class)));
    }

    public record PasswordResetClaims(String userId, String passwordFingerprint) {
    }

    /**
     * Email verification token, sent as a link to the address being verified. Bound to a
     * fingerprint of that address (not the address itself), so it only verifies the address it was
     * sent to and puts no personal data in the link.
     */
    public String generateEmailVerificationToken(String userId, String emailFingerprint, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId)
                .claim(TYPE_CLAIM, EMAIL_VERIFICATION_TYPE)
                .claim(EMAIL_FINGERPRINT_CLAIM, emailFingerprint)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    public Optional<EmailVerificationClaims> parseEmailVerificationToken(String token) {
        return claims(token, EMAIL_VERIFICATION_TYPE)
                .map(claims -> new EmailVerificationClaims(claims.getSubject(),
                        claims.get(EMAIL_FINGERPRINT_CLAIM, String.class)));
    }

    public record EmailVerificationClaims(String userId, String emailFingerprint) {
    }

    /**
     * Long-lived token used only to get new access tokens (POST /auth/refresh). It names a
     * server-side session (the {@code jti} claim), so it stops working as soon as that session is
     * ended, and is bound to the password fingerprint as a second safeguard.
     *
     * @param sessionId  the server-side session this token belongs to
     * @param rememberMe kept in the token so a refreshed session keeps the same lifetime policy
     */
    public String generateRefreshToken(String userId, String sessionId, String passwordFingerprint,
                                       boolean rememberMe, Duration lifetime) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .id(sessionId)
                .subject(userId)
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .claim(FINGERPRINT_CLAIM, passwordFingerprint)
                .claim(REMEMBER_ME_CLAIM, rememberMe)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    public Optional<RefreshClaims> parseRefreshToken(String token) {
        return claims(token, REFRESH_TYPE)
                .filter(claims -> claims.getId() != null)
                .map(claims -> new RefreshClaims(claims.getSubject(), claims.getId(),
                        claims.get(FINGERPRINT_CLAIM, String.class),
                        Boolean.TRUE.equals(claims.get(REMEMBER_ME_CLAIM, Boolean.class))));
    }

    public record RefreshClaims(String userId, String sessionId, String passwordFingerprint, boolean rememberMe) {
    }

    private Optional<Claims> claims(String token, String expectedType) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .require(TYPE_CLAIM, expectedType)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Empty if the token names a role this version doesn't know (treated as an invalid token). */
    private static Optional<Set<Role>> rolesFrom(Claims claims) {
        try {
            List<?> raw = claims.get(ROLES_CLAIM, List.class);
            if (raw == null || raw.isEmpty()) {
                return Optional.of(EnumSet.noneOf(Role.class));
            }
            return Optional.of(raw.stream()
                    .map(Object::toString)
                    .map(Role::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class))));
        } catch (IllegalArgumentException | RequiredTypeException ex) {
            return Optional.empty();
        }
    }
}
