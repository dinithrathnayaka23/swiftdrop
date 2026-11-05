package com.swiftdrop.booking.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The result of a confirmed booking. */
public record BookingConfirmationResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatusCode status,
        BigDecimal totalFee,
        String currency,
        Instant bookedAt) {
}

/*what the customer sees after pressing Confirm:
 the tracking number and the fee that was 
 charged. It has no addresses or phone numbers,
  so no personal data comes back. */