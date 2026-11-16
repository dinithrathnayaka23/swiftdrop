package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.dto.request.DeliveryOutcome;
import java.time.Instant;
import java.util.UUID;

/**
 * One finished delivery of a courier, from {@code delivery_proofs} or {@code delivery_failures}.
 *
 * @param receivedBy  DELIVERED only: who took the parcel
 * @param reasonLabel FAILED_ATTEMPT only: the failure reason's label
 * @param note        the courier's note on a failed attempt; {@code null} for deliveries
 * @param proofId     DELIVERED with a photo only
 */
public record CompletedDeliveryRow(
        UUID parcelId,
        DeliveryOutcome outcome,
        Instant completedAt,
        String receivedBy,
        String reasonLabel,
        String note,
        UUID proofId) {
}
