package com.swiftdrop.identity.service;

import com.swiftdrop.identity.dto.response.AuthResponse;
import java.time.Duration;

/**
 * A new sign-in session: the JSON body for the browser, plus the refresh token that the controller
 * puts in the httpOnly cookie (it never appears in a response body).
 */
public record IssuedSession(AuthResponse response, String refreshToken, Duration refreshLifetime) {

    public boolean rememberMe() {
        return response.rememberMe();
    }

    @Override
    public String toString() {
        return "IssuedSession[user=" + response.user().id() + ", rememberMe=" + rememberMe() + "]";
    }
}
