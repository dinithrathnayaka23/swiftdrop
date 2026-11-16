package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.util.UUID;

/** The parcel after a retry (CREATED, waiting for a courier again) or a close (FAILED). */
public record FailedDecisionResponse(UUID parcelId, ParcelStatusCode status) {
}
