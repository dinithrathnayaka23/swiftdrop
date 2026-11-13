package com.swiftdrop.merchant.dto.response;

/** OAuth 2.0 client-credentials access token returned to a merchant integration. */
public record OAuthTokenResponse(
        String access_token,
        String token_type,
        long expires_in) {
}
