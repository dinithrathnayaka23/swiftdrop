package com.swiftdrop.dispatch.dto.request;

import com.swiftdrop.dispatch.model.CourierAvailability;
import java.util.UUID;

/**
 * Filters of the Couriers list. Every filter is optional ({@code null} or blank matches all).
 *
 * @param search        part of the courier's name, email, phone number or vehicle registration
 * @param serviceAreaId only couriers that cover this delivery zone
 * @param vehicleType   exact vehicle type, ignoring case
 * @param active        {@code true} for working couriers, {@code false} for deactivated ones
 */
public record CourierFilter(String search, CourierAvailability availability, UUID serviceAreaId, String vehicleType,
                            Boolean active) {

    public static CourierFilter none() {
        return new CourierFilter(null, null, null, null, null);
    }
}
