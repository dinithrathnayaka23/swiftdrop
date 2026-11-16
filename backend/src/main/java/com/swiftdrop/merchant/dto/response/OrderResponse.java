package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.math.BigDecimal;
import java.time.Instant;

/** The non-sensitive result returned after a merchant order is created. */
public record OrderResponse(
        String trackingNumber,
        String externalOrderReference,
        ParcelStatusCode status,
        BigDecimal quotedFee,
        String currency,
        Instant createdAt) {
}
