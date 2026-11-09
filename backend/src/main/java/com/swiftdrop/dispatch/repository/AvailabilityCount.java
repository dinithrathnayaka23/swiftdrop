package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.model.CourierAvailability;

/** Number of active couriers with one availability, from {@link CourierRepository#countActiveByAvailability()}. */
public record AvailabilityCount(CourierAvailability availability, long total) {
}
