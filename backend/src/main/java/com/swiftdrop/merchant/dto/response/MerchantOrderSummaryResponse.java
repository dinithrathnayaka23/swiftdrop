package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the merchant portal's orders list. */
public record MerchantOrderSummaryResponse(
        UUID parcelId,
        String trackingNumber,
        String externalOrderReference,
        String recipientName,
        String deliveryCity,
        ParcelType parcelType,
        ParcelStatusCode status,
        BigDecimal quotedFee,
        String feeCurrency,
        Instant createdAt) {
}
