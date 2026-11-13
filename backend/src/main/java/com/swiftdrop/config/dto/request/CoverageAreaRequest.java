package com.swiftdrop.config.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A place a zone covers. Without a postal code it covers the whole city.
 *
 * @param postalCode optional; 3-15 letters, digits, spaces or hyphens
 */
public record CoverageAreaRequest(
        @NotBlank(message = "District is required") @Size(max = 100, message = "District must be at most 100 characters")
        String district,
        @NotBlank(message = "City is required") @Size(max = 100, message = "City must be at most 100 characters")
        String city,
        @Pattern(regexp = "^[A-Za-z0-9 -]{3,15}$",
                message = "Postal code must be 3-15 letters, digits, spaces or hyphens")
        String postalCode) {
}
