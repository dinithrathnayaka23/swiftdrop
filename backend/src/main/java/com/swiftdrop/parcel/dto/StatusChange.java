package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to move a parcel to its next status, built by the courier app, dispatch or the
 * merchant module after they have checked that the caller may change this parcel.
 *
 * @param changedByUserId the user who made the change, or {@code null} for a system change
 * @param note            optional free text
 * @param recipientName   who received the parcel; only for DELIVERED
 * @param proofFileUrl    stored proof photo; only for DELIVERED. DELIVERED needs this or {@code recipientName}
 * @param failureReason   why the delivery failed; required for FAILED_ATTEMPT and FAILED, and only
 *                        allowed there
 */
public record StatusChange(
        ParcelStatusCode newStatus,
        UUID changedByUserId,
        String note,
        String recipientName,
        String proofFileUrl,
        String failureReason) {

    public StatusChange {
        Objects.requireNonNull(newStatus, "newStatus");
    }

    /** A plain step such as PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY. */
    public static StatusChange to(ParcelStatusCode newStatus, UUID changedByUserId, String note) {
        return new StatusChange(newStatus, changedByUserId, note, null, null, null);
    }

    public static StatusChange delivered(UUID changedByUserId, String recipientName, String proofFileUrl,
                                         String note) {
        return new StatusChange(ParcelStatusCode.DELIVERED, changedByUserId, note, recipientName, proofFileUrl, null);
    }

    /** The courier couldn't deliver this time (from PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY). */
    public static StatusChange failedAttempt(UUID changedByUserId, String failureReason, String note) {
        return new StatusChange(ParcelStatusCode.FAILED_ATTEMPT, changedByUserId, note, null, null, failureReason);
    }

    /** The dispatcher closes a failed attempt for good (from FAILED_ATTEMPT only); an end state. */
    public static StatusChange failed(UUID changedByUserId, String failureReason, String note) {
        return new StatusChange(ParcelStatusCode.FAILED, changedByUserId, note, null, null, failureReason);
    }
}
