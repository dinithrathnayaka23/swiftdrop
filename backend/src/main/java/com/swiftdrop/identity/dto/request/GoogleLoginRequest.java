package com.swiftdrop.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param credential the Google ID token (a JWT) from Google Identity Services in the browser
 * @param rememberMe same meaning as in {@link LoginRequest}; optional, defaults to false
 */
public record GoogleLoginRequest(
        @NotBlank(message = "Google credential is required")
        @Size(max = 4096, message = "Google sign-in failed. Please try again.")
        String credential,

        Boolean rememberMe) {

    public boolean rememberMeOrDefault() {
        return Boolean.TRUE.equals(rememberMe);
    }

    @Override
    public String toString() {
        return "GoogleLoginRequest[credential=***, rememberMe=" + rememberMe + "]";
    }
}
