package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Changes a courier's vehicle and service areas. Every field is optional; a field left out is not
 * changed, but at least one must be given. {@code serviceAreaIds} replaces the whole set, so an
 * empty set removes every area.
 */
public record UpdateCourierRequest(
        @Size(max = 50, message = "Vehicle type must be at most 50 characters")
        @Pattern(regexp = "\\S.*", message = "Vehicle type cannot be blank")
        String vehicleType,

        @Size(max = 30, message = "Vehicle registration must be at most 30 characters")
        @Pattern(regexp = CourierRules.VEHICLE_REGISTRATION, message = CourierRules.VEHICLE_REGISTRATION_MESSAGE)
        String vehicleRegistration,

        @Size(max = CourierRules.MAX_SERVICE_AREAS, message = "Too many service areas")
        Set<UUID> serviceAreaIds) {
}
