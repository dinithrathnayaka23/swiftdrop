package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;

/** One entry of a parcel's timeline on the customer and admin screens. */
public record StatusHistoryItemResponse(
        ParcelStatusCode status,
        Instant changedAt,
        String note,
        String failureReason) {
}
