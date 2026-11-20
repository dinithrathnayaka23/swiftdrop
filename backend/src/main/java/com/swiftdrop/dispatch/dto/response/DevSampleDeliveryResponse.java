package com.swiftdrop.dispatch.dto.response;

import java.util.UUID;

/** Dev profile only: the sample parcel just given to the signed-in courier. */
public record DevSampleDeliveryResponse(UUID parcelId, String trackingNumber) {
}
