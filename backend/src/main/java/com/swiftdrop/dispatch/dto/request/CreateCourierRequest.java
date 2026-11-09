package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Adds a courier: their sign-in account (the courier app, /courier/login) and their courier profile.
 * The dispatcher sets the first password and passes it on; the courier can change it with "Forgot password".
 * The account rules match the customer sign-up (see identity's ValidationPatterns).
 *
 * @param serviceAreaIds delivery zones the courier covers; optional
 */
public record CreateCourierRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
        @Pattern(regexp = CourierRules.PERSON_NAME,
                message = "Full name can only contain letters, spaces, apostrophes, dots and hyphens")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = CourierRules.PHONE_E164, message = "Enter a valid phone number")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = CourierRules.PASSWORD, message = CourierRules.PASSWORD_MESSAGE)
        String password,

        @NotBlank(message = "Vehicle type is required")
        @Size(max = 50, message = "Vehicle type must be at most 50 characters")
        String vehicleType,

        @NotBlank(message = "Vehicle registration is required")
        @Size(max = 30, message = "Vehicle registration must be at most 30 characters")
        @Pattern(regexp = CourierRules.VEHICLE_REGISTRATION, message = CourierRules.VEHICLE_REGISTRATION_MESSAGE)
        String vehicleRegistration,

        @Size(max = CourierRules.MAX_SERVICE_AREAS, message = "Too many service areas")
        Set<UUID> serviceAreaIds) {

    @Override
    public String toString() {
        return "CreateCourierRequest[***]";
    }
}
