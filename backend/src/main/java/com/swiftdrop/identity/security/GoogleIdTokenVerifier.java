package com.swiftdrop.identity.security;

import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.identity.exception.GoogleSignInUnavailableException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies the ID token that Google Identity Services gives the browser ("Sign in with Google").
 * Checks Google's signature (public keys from the JWKS endpoint, cached), the issuer, that the
 * token was issued for our client ID, the expiry, and that Google has verified the email.
 * Disabled (every call fails with 503) while GOOGLE_CLIENT_ID is not set.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String FAILED = "Google sign-in failed. Please try again.";

    private final String clientId;
    private final NimbusJwtDecoder decoder;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${swiftdrop.auth.google.client-id:}") String clientId,
                                 @Value("${swiftdrop.auth.google.jwk-set-uri}") String jwkSetUri) {
        this(clientId, clientId.isBlank() ? null : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build());
        if (!isEnabled()) {
            log.info("Google sign-in is disabled: GOOGLE_CLIENT_ID is not set");
        }
    }

    /** Package-private so tests can supply a decoder that trusts a local key. */
    GoogleIdTokenVerifier(String clientId, NimbusJwtDecoder decoder) {
        this.clientId = clientId.trim();
        this.decoder = decoder;
        if (decoder != null) {
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                            iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            aud -> aud != null && aud.contains(this.clientId))));
        }
    }

    public boolean isEnabled() {
        return decoder != null;
    }

    /** The OAuth client ID the browser needs to render the Google button (public, not a secret). */
    public String clientId() {
        return clientId;
    }

    public GoogleIdentity verify(String idToken) {
        if (!isEnabled()) {
            throw new GoogleSignInUnavailableException();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException ex) {
            log.info("Rejected Google ID token: {}", ex.getMessage());
            throw new UnauthorizedException(FAILED);
        } catch (JwtException ex) {
            // Not the token's fault: e.g. Google's key endpoint could not be reached.
            log.warn("Could not verify Google ID token: {}", ex.getMessage());
            throw new GoogleSignInUnavailableException();
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new UnauthorizedException("Your Google account's email address is not verified.");
        }
        return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }

    /** The verified Google account. {@code name} may be null (the user hid it). */
    public record GoogleIdentity(String subject, String email, String name) {
    }
}
