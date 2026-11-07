package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin Deliveries Tracking board ({@code GET /api/v1/admin/live/deliveries}).
 * Matches the frontend's {@code LiveDeliveryRow} (features/admin-live-tracking/types.ts). Admin
 * only, but still no contact details: the board needs none.
 *
 * @param statusVersion 1 for CREATED, +1 per change; live updates with a higher version replace the row
 * @param courierName   {@code null} until the dispatch module provides {@code CourierLookup}
 * @param lastUpdatedAt when the status last changed
 */
public record LiveDeliveryResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatusCode status,
        long statusVersion,
        String fromCity,
        String toCity,
        String courierName,
        Instant lastUpdatedAt) {
}
