package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.Size;

/** Retry a failed attempt; the note (optional) goes into the parcel's history. The body is optional. */
public record RetryDeliveryRequest(
        @Size(max = ReassignParcelRequest.MAX_REASON, message = "Note must be at most "
                + ReassignParcelRequest.MAX_REASON + " characters")
        String note) {
}
