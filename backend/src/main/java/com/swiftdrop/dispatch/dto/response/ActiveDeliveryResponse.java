package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.UUID;

/**
 * A delivery under way: an open IN_PROGRESS assignment and its parcel. No ETA: it isn't stored.
 *
 * @param destinationCity the drop-off city; {@code null} if the parcel can't be read
 * @param parcelStatus    PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY
 */
public record ActiveDeliveryResponse(
        UUID parcelId,
        String trackingNumber,
        UUID courierId,
        String courierName,
        String destinationCity,
        ParcelStatusCode parcelStatus,
        Instant assignedAt) {
}
