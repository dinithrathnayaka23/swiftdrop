package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin Deliveries Tracking table ({@code GET /api/v1/admin/live/deliveries}).
 * Admin only: contains the recipient's name and phone, as in the design.
 *
 * @param statusVersion 1 for CREATED, +1 per change; a live update with a higher version replaces the row
 * @param merchantName  {@code null} for a customer booking
 * @param courierName   {@code null} until the dispatch module provides {@code CourierLookup}
 * @param lastUpdatedAt when the status last changed
 */
public record LiveDeliveryResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatusCode status,
        long statusVersion,
        String recipientName,
        String recipientPhone,
        String merchantName,
        String courierName,
        String destinationCity,
        Instant bookedAt,
        Instant lastUpdatedAt) {
}
