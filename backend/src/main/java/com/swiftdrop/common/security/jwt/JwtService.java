package com.swiftdrop.common.security.jwt;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
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
 * Issues and validates access tokens.
 *
 * <p>SKELETON: owned by the identity module. Refresh tokens, revocation and
 * the Google OAuth exchange are still to be designed.
 */
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";
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

    public String generateToken(String userId, Collection<Role> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId)
                .claim(ROLES_CLAIM, roles.stream().map(Role::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiry())))
                .signWith(key)
                .compact();
    }

    /** Returns the user for a valid token, or empty if it is invalid, expired or tampered with. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(claims.getSubject(), rolesFrom(claims)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Set<Role> rolesFrom(Claims claims) {
        List<?> raw = claims.get(ROLES_CLAIM, List.class);
        if (raw == null || raw.isEmpty()) {
            return EnumSet.noneOf(Role.class);
        }
        return raw.stream()
                .map(Object::toString)
                .map(Role::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }
}
