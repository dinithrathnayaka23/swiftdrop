package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One of the customer's own parcels (Delivery Details screen).
 *
 * @param finalFee      {@code null} until payments exist; the screen shows the quote
 * @param paymentMethod {@code null} until payments exist
 * @param paymentStatus {@code null} until payments exist
 * @param history       oldest first
 */
public record CustomerParcelDetailResponse(
        UUID id,
        String trackingNumber,
        String orderReference,
        Instant bookedAt,
        Instant lastUpdatedAt,
        ParcelStatusCode status,
        AddressResponse pickupAddress,
        AddressResponse dropoffAddress,
        ParcelType parcelType,
        String contentDescription,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String deliveryInstructions,
        BigDecimal quotedFee,
        BigDecimal finalFee,
        String feeCurrency,
        String paymentMethod,
        String paymentStatus,
        List<StatusHistoryItemResponse> history) {
}
