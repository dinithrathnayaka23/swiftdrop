package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.model.AssignmentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * One delivery assignment.
 *
 * @param courierUserId    the courier's account ({@code users.user_id})
 * @param assignedByUserId the dispatcher who made it
 * @param endedAt          {@code null} while the assignment is open
 * @param reason           why it was reassigned or cancelled, if given
 */
public record AssignmentResponse(
        UUID id,
        UUID parcelId,
        UUID courierId,
        UUID courierUserId,
        AssignmentStatus status,
        UUID assignedByUserId,
        Instant assignedAt,
        Instant endedAt,
        String reason) {
}
