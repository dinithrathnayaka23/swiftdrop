package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A row of the customer's dashboard and "My Deliveries" tables. */
public record CustomerParcelSummaryResponse(
        UUID id,
        String trackingNumber,
        String orderReference,
        String destinationCity,
        String destinationDistrict,
        Instant bookedAt,
        ParcelStatusCode status,
        BigDecimal quotedFee,
        String feeCurrency) {
}
