package com.swiftdrop.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantRegistrationRequest(
        @NotBlank(message = "Business name is required")
        @Size(min = 2, max = 180, message = "Business name must be 2-180 characters")
        String businessName,

        @NotBlank(message = "Registration number is required")
        @Pattern(regexp = ValidationPatterns.REGISTRATION_NO, message = ValidationPatterns.REGISTRATION_NO_MESSAGE)
        String registrationNo,

        @NotBlank(message = "Contact person name is required")
        @Size(min = 2, max = 120, message = "Contact person name must be 2-120 characters")
        @Pattern(regexp = ValidationPatterns.PERSON_NAME,
                message = "Contact person name can only contain letters, spaces, apostrophes, dots and hyphens")
        String contactPersonName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = ValidationPatterns.PHONE_E164, message = "Enter a valid phone number")
        String phone,

        @NotBlank(message = "Business address is required")
        @Size(min = 10, max = 500, message = "Please enter the full business address")
        String businessAddress,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password) {

    @Override
    public String toString() {
        return "MerchantRegistrationRequest[***]";
    }
}
