package com.swiftdrop.delivery.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;

/** One status change of a delivery, oldest first in a list. */
public record CourierTimelineEntryResponse(
        ParcelStatusCode status,
        Instant changedAt,
        String note,
        String failureReason) {
}
