package com.swiftdrop.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A pickup or drop-off address with contact details.
 *
 * @param addressLine2 optional
 * @param postalCode   optional
 */
public record BookingAddressRequest(
        @NotBlank @Size(max = 120) String contactName,
        @NotBlank @Size(max = 25) @Pattern(regexp = "^\\+?[0-9 ()-]{7,25}$", message = "must be a valid phone number")
                String contactPhone,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district,
        @Size(max = 15) String postalCode) {
}