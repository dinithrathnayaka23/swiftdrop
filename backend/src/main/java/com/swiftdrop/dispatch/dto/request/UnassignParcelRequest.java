package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.Size;

/** Take a parcel back from its courier before pickup. The body and the reason are optional. */
public record UnassignParcelRequest(
        @Size(max = ReassignParcelRequest.MAX_REASON, message = "Reason must be at most "
                + ReassignParcelRequest.MAX_REASON + " characters")
        String reason) {
}
