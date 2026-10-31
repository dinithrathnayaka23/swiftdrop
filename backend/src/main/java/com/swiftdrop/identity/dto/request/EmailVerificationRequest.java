package com.swiftdrop.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param token from the emailed link (/verify-email?token=...) */
public record EmailVerificationRequest(
        @NotBlank(message = "Confirmation token is required")
        @Size(max = 2000, message = "This confirmation link is invalid or has expired")
        String token) {

    @Override
    public String toString() {
        return "EmailVerificationRequest[***]";
    }
}
