package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.AddressResponse;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;

/** Everything printed on a delivery receipt. */
record ReceiptContent(
        String receiptNumber,
        Instant issuedAt,
        String trackingNumber,
        String orderReference,
        Instant bookedAt,
        ParcelStatusCode status,
        AddressResponse pickup,
        AddressResponse dropoff,
        ParcelType parcelType,
        String contentDescription,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal fee,
        String currency) {
}
