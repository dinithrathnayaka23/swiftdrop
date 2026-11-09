package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A courier as the dispatcher sees them: a row of the Couriers list and its detail drawer.
 * Contains contact details, so it is only returned to the dispatcher/admin.
 *
 * @param id             the courier profile ({@code couriers.courier_id})
 * @param userId         the courier's sign-in account
 * @param active         {@code false} once the courier has been deactivated
 * @param accountStatus  status of the sign-in account
 * @param serviceAreaIds delivery zone ids; the zone names come from the Service Areas screen's API
 */
public record CourierResponse(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String phone,
        String vehicleType,
        String vehicleRegistration,
        CourierAvailability availability,
        boolean active,
        AccountStatus accountStatus,
        Set<UUID> serviceAreaIds,
        CourierWorkload workload,
        Instant createdAt) {
}
