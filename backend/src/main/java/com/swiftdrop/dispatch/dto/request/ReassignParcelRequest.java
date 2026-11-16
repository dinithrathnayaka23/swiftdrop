package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Move a parcel from its current courier to another one. */
public record ReassignParcelRequest(
        @NotNull(message = "Courier is required") UUID courierId,
        @NotBlank(message = "Reason is required")
        @Size(max = ReassignParcelRequest.MAX_REASON, message = "Reason must be at most "
                + ReassignParcelRequest.MAX_REASON + " characters")
        String reason) {

    public static final int MAX_REASON = 500;
}
