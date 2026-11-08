package com.swiftdrop.tracking.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One delivery on the admin delivery detail page
 * ({@code GET /api/v1/admin/live/deliveries/{trackingNumber}}). Admin only.
 *
 * @param merchantName {@code null} for a customer booking
 * @param courierName  {@code null} until the dispatch module provides {@code CourierLookup}
 * @param history      oldest first
 */
public record LiveDeliveryDetailResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatusCode status,
        long statusVersion,
        Instant bookedAt,
        Instant lastUpdatedAt,
        String recipientName,
        String recipientPhone,
        DeliveryAddress dropoffAddress,
        String fromCity,
        String merchantName,
        String courierName,
        ParcelType parcelType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String contentDescription,
        String deliveryInstructions,
        List<StatusStep> history) {

    /** @param addressLine2 optional; {@code postalCode} likewise */
    public record DeliveryAddress(String addressLine1, String addressLine2, String city, String district,
                                  String postalCode) {
    }

    /** One status change: the status and when it happened. */
    public record StatusStep(ParcelStatusCode status, Instant changedAt) {
    }
}
