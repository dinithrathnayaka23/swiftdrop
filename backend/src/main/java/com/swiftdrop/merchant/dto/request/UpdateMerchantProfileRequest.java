package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The profile fields a merchant may change. Business name and registration number are left out:
 * the admin approved the merchant against them. Rules match merchant registration (see
 * identity's MerchantRegistrationRequest).
 */
public record UpdateMerchantProfileRequest(
        @NotBlank(message = "Contact email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String contactEmail,

        @NotBlank(message = "Contact phone is required")
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Enter a valid phone number")
        String contactPhone,

        @NotBlank(message = "Business address is required")
        @Size(min = 10, max = 500, message = "Please enter the full business address")
        String businessAddress) {

    /** Keeps contact details out of logs. */
    @Override
    public String toString() {
        return "UpdateMerchantProfileRequest[***]";
    }
}
