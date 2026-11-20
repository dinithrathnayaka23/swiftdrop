package com.swiftdrop.dispatch.api;

import java.util.List;
import java.util.UUID;

/**
 * A courier's own profile, for the courier app's profile card. Contains the courier's contact
 * details: return it only to that courier.
 *
 * @param active         {@code false} once the dispatcher has deactivated the courier
 * @param serviceAreas   names of the delivery zones the courier covers, sorted; empty means any area
 * @param assigned       open assignments not started yet
 * @param inProgress     open assignments being delivered
 * @param completedToday deliveries completed today (dispatch's count)
 */
public record CourierProfile(
        UUID courierId,
        String fullName,
        String email,
        String phone,
        String vehicleType,
        String vehicleRegistration,
        CourierStatus status,
        boolean active,
        List<String> serviceAreas,
        int assigned,
        int inProgress,
        int completedToday) {
}
