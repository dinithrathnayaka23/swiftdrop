package com.swiftdrop.delivery.dto.response;

import com.swiftdrop.delivery.dto.request.DeliveryOutcome;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of Completed Deliveries. Everything the courier needs is here: the delivery itself can no
 * longer be opened once dispatch has ended the assignment.
 *
 * @param recipientName  who the parcel was addressed to
 * @param dropoffArea    the drop-off's first address line
 * @param receivedBy     DELIVERED only: who took the parcel
 * @param failureReason  FAILED_ATTEMPT only: the reason's label
 * @param note           FAILED_ATTEMPT only: the courier's note
 * @param proofPhotoUrl  DELIVERED with a photo only; relative to {@code /api/v1}, needs the sign-in token
 */
public record CompletedDeliveryResponse(
        UUID parcelId,
        String trackingNumber,
        DeliveryOutcome outcome,
        Instant completedAt,
        String recipientName,
        String dropoffArea,
        String dropoffCity,
        String receivedBy,
        String failureReason,
        String note,
        String proofPhotoUrl) {
}
