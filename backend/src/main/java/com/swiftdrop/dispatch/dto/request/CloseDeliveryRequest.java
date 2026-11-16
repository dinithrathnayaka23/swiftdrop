package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Close a failed attempt for good (parcel FAILED); the reason is recorded in its history. */
public record CloseDeliveryRequest(
        @NotBlank(message = "Reason is required")
        @Size(max = ReassignParcelRequest.MAX_REASON, message = "Reason must be at most "
                + ReassignParcelRequest.MAX_REASON + " characters")
        String reason) {
}
