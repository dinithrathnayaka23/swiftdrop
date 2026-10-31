package com.swiftdrop.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.identity.exception.GoogleSignInUnavailableException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs tokens with a local RSA key standing in for Google's, and checks every rule is enforced. */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    private static KeyPair googleKey;
    private static KeyPair otherKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKey = generator.generateKeyPair();
        otherKey = generator.generateKeyPair();
    }

    private static GoogleIdTokenVerifier verifier() {
        return new GoogleIdTokenVerifier(CLIENT_ID,
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) googleKey.getPublic()).build());
    }

    private static String token(KeyPair key, Consumer<JwtClaimsSet.Builder> customise) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) key.getPublic())
                .privateKey((RSAPrivateKey) key.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject("google-sub-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", "nimal@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Nimal Perera");
        customise.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims.build())).getTokenValue();
    }

    private static String token(Consumer<JwtClaimsSet.Builder> customise) {
        return token(googleKey, customise);
    }

    @Test
    void acceptsValidTokenAndReturnsIdentity() {
        var identity = verifier().verify(token(claims -> { }));

        assertThat(identity.subject()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("nimal@gmail.com");
        assertThat(identity.name()).isEqualTo("Nimal Perera");
    }

    @Test
    void acceptsIssuerWithoutScheme() {
        assertThat(verifier().verify(token(claims -> claims.issuer("accounts.google.com"))).subject())
                .isEqualTo("google-sub-123");
    }

    @Test
    void rejectsTokenSignedByAnotherKey() {
        assertThatThrownBy(() -> verifier().verify(token(otherKey, claims -> { })))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTokenForAnotherClient() {
        assertThatThrownBy(() -> verifier().verify(token(claims ->
                claims.audience(List.of("someone-else.apps.googleusercontent.com")))))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        assertThatThrownBy(() -> verifier().verify(token(claims -> claims.issuer("https://evil.example.com"))))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minusSeconds(7200);
        assertThatThrownBy(() -> verifier().verify(token(claims ->
                claims.issuedAt(past).expiresAt(past.plusSeconds(600)))))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsUnverifiedEmail() {
        assertThatThrownBy(() -> verifier().verify(token(claims -> claims.claim("email_verified", false))))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> verifier().verify("not-a-jwt")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void disabledWithoutClientId() {
        var disabled = new GoogleIdTokenVerifier("", "https://www.googleapis.com/oauth2/v3/certs");

        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.verify(token(claims -> { })))
                .isInstanceOf(GoogleSignInUnavailableException.class);
    }
}
