package com.swiftdrop.tracking.dto.response;

/** Body of the dev-only {@code POST /api/v1/admin/dev/tracking/sample-parcel}. */
public record DevSampleParcelResponse(String trackingNumber) {
}
