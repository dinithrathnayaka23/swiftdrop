package com.swiftdrop.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), "swiftdrop"));

    @Test
    void roundTripsUserIdAndRoles() {
        String token = jwtService.generateToken("42", List.of(Role.MERCHANT, Role.CUSTOMER));

        assertThat(jwtService.parse(token)).hasValueSatisfying(user -> {
            assertThat(user.userId()).isEqualTo("42");
            assertThat(user.roles()).containsExactlyInAnyOrder(Role.MERCHANT, Role.CUSTOMER);
        });
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        var other = new JwtService(new JwtProperties(SECRET + "-other", Duration.ofMinutes(5), "swiftdrop"));
        String token = other.generateToken("42", List.of(Role.COURIER));

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        var expired = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-10), "swiftdrop"));

        assertThat(jwtService.parse(expired.generateToken("42", List.of()))).isEmpty();
    }

    @Test
    void passwordResetTokensAreNotAccessTokens() {
        String resetToken = jwtService.generatePasswordResetToken("42", "abc123", Duration.ofMinutes(30));

        assertThat(jwtService.parse(resetToken)).isEmpty();
        assertThat(jwtService.parsePasswordResetToken(resetToken)).hasValueSatisfying(claims -> {
            assertThat(claims.userId()).isEqualTo("42");
            assertThat(claims.passwordFingerprint()).isEqualTo("abc123");
        });
    }

    @Test
    void accessTokensAreNotPasswordResetTokens() {
        String accessToken = jwtService.generateToken("42", List.of(Role.CUSTOMER));

        assertThat(jwtService.parsePasswordResetToken(accessToken)).isEmpty();
    }

    @Test
    void refreshTokensNameTheirSessionAndCannotBeUsedAsOtherTokens() {
        String refresh = jwtService.generateRefreshToken("42", "session-1", "fp1", true, Duration.ofDays(30));

        assertThat(jwtService.parseRefreshToken(refresh)).hasValueSatisfying(claims -> {
            assertThat(claims.userId()).isEqualTo("42");
            assertThat(claims.sessionId()).isEqualTo("session-1");
            assertThat(claims.passwordFingerprint()).isEqualTo("fp1");
            assertThat(claims.rememberMe()).isTrue();
        });
        assertThat(jwtService.parse(refresh)).isEmpty();
        assertThat(jwtService.parsePasswordResetToken(refresh)).isEmpty();
        assertThat(jwtService.parseRefreshToken(jwtService.generateToken("42", List.of(Role.CUSTOMER)))).isEmpty();
    }

    @Test
    void rejectsExpiredRefreshToken() {
        String expired = jwtService.generateRefreshToken("42", "session-1", "fp1", false, Duration.ofSeconds(-10));

        assertThat(jwtService.parseRefreshToken(expired)).isEmpty();
    }

    @Test
    void accessTokenWithAnUnknownRoleIsTreatedAsInvalid() {
        String token = Jwts.builder()
                .issuer("swiftdrop")
                .subject("42")
                .claim("typ", "access")
                .claim("roles", List.of("SUPER_USER"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    void emailVerificationTokensAreTheirOwnType() {
        String token = jwtService.generateEmailVerificationToken("42", "email-fp", Duration.ofHours(24));

        assertThat(jwtService.parseEmailVerificationToken(token)).hasValueSatisfying(claims -> {
            assertThat(claims.userId()).isEqualTo("42");
            assertThat(claims.emailFingerprint()).isEqualTo("email-fp");
        });
        assertThat(jwtService.parse(token)).isEmpty();
        assertThat(jwtService.parsePasswordResetToken(token)).isEmpty();
        assertThat(jwtService.parseRefreshToken(token)).isEmpty();
        String reset = jwtService.generatePasswordResetToken("42", "fp", Duration.ofMinutes(30));
        assertThat(jwtService.parseEmailVerificationToken(reset)).isEmpty();
        assertThat(jwtService.parseEmailVerificationToken(
                jwtService.generateEmailVerificationToken("42", "fp", Duration.ofSeconds(-10)))).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", Duration.ofMinutes(5), "swiftdrop")))
                .isInstanceOf(IllegalStateException.class);
    }
}
