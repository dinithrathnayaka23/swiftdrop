package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.response.OAuthTokenResponse;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth 2.0 client-credentials token endpoint for merchant integrations. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/oauth")
@RequiredArgsConstructor
public class OAuthTokenController {

    static final String CLIENT_CREDENTIALS_GRANT = "client_credentials";
    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final MerchantApiKeyService merchantApiKeyService;
    private final JwtService jwtService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public OAuthTokenResponse token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret) {
        if (!CLIENT_CREDENTIALS_GRANT.equals(grantType)
                || clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()) {
            throw invalidCredentials();
        }

        AuthenticatedMerchant merchant = merchantApiKeyService.authenticate(clientSecret)
                .filter(authenticated -> clientId.equals(authenticated.keyId()))
                .orElseThrow(OAuthTokenController::invalidCredentials);
        String token = jwtService.generateMerchantApiToken(
                merchant.merchantId(), merchant.apiCredentialId(), TOKEN_LIFETIME);
        return new OAuthTokenResponse(token, "Bearer", TOKEN_LIFETIME.toSeconds());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid client credentials");
    }
}
