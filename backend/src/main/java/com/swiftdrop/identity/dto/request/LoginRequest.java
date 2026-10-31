package com.swiftdrop.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param rememberMe "Remember me" ticked: keep the user signed in for weeks instead of only until
 *                   the browser closes. Optional, defaults to false.
 * @param portal     the sign-in page used. Optional, defaults to the main page ({@link LoginPortal#PUBLIC}).
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        // Only presence/length: existing passwords are never rejected by format rules.
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Invalid email or password")
        String password,

        Boolean rememberMe,

        LoginPortal portal) {

    public LoginRequest(String email, String password, Boolean rememberMe) {
        this(email, password, rememberMe, null);
    }

    public boolean rememberMeOrDefault() {
        return Boolean.TRUE.equals(rememberMe);
    }

    public LoginPortal portalOrDefault() {
        return portal == null ? LoginPortal.PUBLIC : portal;
    }

    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***, rememberMe=" + rememberMe + "]";
    }
}
