package com.swiftdrop.parcel.dto.response;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of the admin Parcels list.
 *
 * @param merchantName  {@code null} for a customer booking
 * @param dispatchState UNASSIGNED / ASSIGNED for CREATED parcels once the dispatch module provides
 *                      it; {@code null} until then
 */
public record AdminParcelRowResponse(
        UUID id,
        String trackingNumber,
        String merchantName,
        String recipientName,
        String destination,
        BigDecimal weightKg,
        ParcelStatusCode status,
        String dispatchState,
        Instant createdAt) {
}
