package com.swiftdrop.merchant.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.response.OAuthTokenResponse;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import java.time.Duration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth 2.0 client-credentials token endpoint for merchant integrations. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/oauth")
@Tag(name = "Merchant OAuth", description = "OAuth 2.0 client-credentials authentication for merchant integrations")
@RequiredArgsConstructor
public class OAuthTokenController {

    static final String CLIENT_CREDENTIALS_GRANT = "client_credentials";
    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final MerchantApiKeyService merchantApiKeyService;
    private final JwtService jwtService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(
            summary = "Issue a merchant access token",
            description = "Exchanges approved merchant client credentials for a short-lived Bearer token.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    value = "grant_type=client_credentials&client_id=merchant-key-id&client_secret=merchant-secret"))),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Token issued",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            value = """
                                                    {
                                                      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                                      "tokenType": "Bearer",
                                                      "expiresIn": 900
                                                    }
                                                    """))),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "Invalid client credentials")
            })
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
