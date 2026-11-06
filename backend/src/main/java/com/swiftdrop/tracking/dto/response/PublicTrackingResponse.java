package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Body of {@code GET /api/v1/public/tracking/{trackingNumber}}. Matches the frontend's
 * {@code TrackingResult} (frontend/src/types/tracking.ts) field for field.
 *
 * <p>Public, so no personal data: no names, phone numbers, street addresses, notes, failure
 * reasons or proof. Add a field only if it is safe for anyone who knows the tracking number.
 *
 * @param fromCity city of the pickup address
 * @param toCity   city of the drop-off address
 * @param history  oldest first
 */
public record PublicTrackingResponse(
        String trackingNumber,
        ParcelStatusCode currentStatus,
        Instant lastUpdatedAt,
        Instant bookedAt,
        String fromCity,
        String toCity,
        BigDecimal weightKg,
        List<PublicTrackingEventResponse> history) {
}
