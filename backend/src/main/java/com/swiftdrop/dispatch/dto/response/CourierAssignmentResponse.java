package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.model.AssignmentStatus;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import java.time.Instant;
import java.util.UUID;

/**
 * A row of the Courier drawer's "Current Deliveries" / "History" tabs: the assignment plus what the
 * row shows about the parcel. The parcel fields are {@code null} if the parcel can't be read.
 *
 * @param endedAt         {@code null} while the assignment is open
 * @param reason          why it was reassigned or cancelled, if given
 * @param destinationCity the drop-off city
 */
public record CourierAssignmentResponse(
        UUID id,
        UUID parcelId,
        AssignmentStatus status,
        Instant assignedAt,
        Instant endedAt,
        String reason,
        String trackingNumber,
        ParcelStatusCode parcelStatus,
        String destinationCity) {
}
