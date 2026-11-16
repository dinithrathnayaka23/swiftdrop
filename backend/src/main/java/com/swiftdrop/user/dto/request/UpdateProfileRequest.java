package com.swiftdrop.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The signed-in user's own editable details. Email, role and status are not editable here.
 * <p>
 * The rules match customer registration (identity's ValidationPatterns, which this module can't
 * use without a dependency cycle: identity already depends on user).
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters")
        @Pattern(regexp = "^\\p{L}[\\p{L}\\p{M}\\s.'-]*$",
                message = "Full name can only contain letters, spaces, apostrophes, dots and hyphens")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Enter a valid phone number")
        String phone) {

    @Override
    public String toString() {
        return "UpdateProfileRequest[...]"; // personal data: never logged
    }
}
