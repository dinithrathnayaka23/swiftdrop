package com.swiftdrop.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.security.Role;
import java.time.Duration;
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
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", Duration.ofMinutes(5), "swiftdrop")))
                .isInstanceOf(IllegalStateException.class);
    }
}
