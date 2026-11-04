package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a parcel's timeline. {@code recipientName} and {@code proofFileUrl} are personal
 * data: the public tracking page must show only the status and the time.
 *
 * @param previousStatus  {@code null} for the first entry (CREATED)
 * @param changedByUserId {@code null} for system and merchant API changes
 */
public record StatusHistoryEntry(
        UUID id,
        UUID parcelId,
        ParcelStatusCode previousStatus,
        ParcelStatusCode status,
        UUID changedByUserId,
        String note,
        String failureReason,
        String recipientName,
        String proofFileUrl,
        Instant changedAt) {
}
