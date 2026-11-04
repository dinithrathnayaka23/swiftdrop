package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a parcel for other modules (booking, tracking, dispatch, merchant).
 *
 * <p>It contains personal data (names, phone numbers, addresses). Never return it from a public
 * endpoint as it is: check that the caller may see the parcel, and map it to your own response
 * with only the fields that caller needs.
 *
 * @param customerUserId set for customer bookings, otherwise {@code null}
 * @param merchantId     set for merchant orders, otherwise {@code null}
 */
public record ParcelDetails(
        UUID id,
        String trackingNumber,
        UUID customerUserId,
        UUID merchantId,
        String externalOrderReference,
        AddressDetails pickupAddress,
        AddressDetails dropoffAddress,
        ParcelType parcelType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String contentDescription,
        String deliveryInstructions,
        ParcelStatusCode currentStatus,
        UUID pricingRuleId,
        BigDecimal quotedFee,
        String feeCurrency,
        Instant createdAt,
        Instant updatedAt) {
}
