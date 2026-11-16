package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of the Failed Deliveries queue.
 *
 * @param parcelStatus    FAILED_ATTEMPT (needs a decision) or FAILED (closed)
 * @param failureReason   from the latest FAILED_ATTEMPT / FAILED history entry
 * @param failedAt        when that entry was made
 * @param courierId       courier of the parcel's most recent failed attempt; {@code null} if none is recorded
 * @param courierName     that courier's name
 * @param attemptCount    how many delivery attempts of this parcel failed (assignments ended FAILED)
 * @param destinationCity the drop-off city
 */
public record FailedDeliveryResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatusCode parcelStatus,
        String failureReason,
        Instant failedAt,
        UUID courierId,
        String courierName,
        int attemptCount,
        String destinationCity) {
}
