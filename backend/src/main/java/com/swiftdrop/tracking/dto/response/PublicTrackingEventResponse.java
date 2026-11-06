package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;

/** One step of the public timeline: the status and when it happened, nothing else. */
public record PublicTrackingEventResponse(ParcelStatusCode statusCode, Instant changedAt) {
}
