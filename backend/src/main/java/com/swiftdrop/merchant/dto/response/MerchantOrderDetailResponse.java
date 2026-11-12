package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The order details page of the merchant portal: the order and its status timeline, oldest first. */
public record MerchantOrderDetailResponse(
        UUID parcelId,
        String trackingNumber,
        String externalOrderReference,
        Address pickupAddress,
        Address dropoffAddress,
        ParcelType parcelType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String contentDescription,
        String deliveryInstructions,
        ParcelStatusCode status,
        BigDecimal quotedFee,
        String feeCurrency,
        Instant createdAt,
        Instant updatedAt,
        List<TimelineEntry> timeline) {

    public record Address(
            String contactName,
            String contactPhone,
            String addressLine1,
            String addressLine2,
            String city,
            String district,
            String postalCode) {
    }

    /**
     * One status change. {@code failureReason} is set for FAILED, {@code recipientName} for
     * DELIVERED. Who made the change and the proof-of-delivery file are left out.
     */
    public record TimelineEntry(
            ParcelStatusCode status,
            Instant changedAt,
            String note,
            String failureReason,
            String recipientName) {
    }
}
