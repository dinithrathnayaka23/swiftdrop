package com.swiftdrop.dispatch.api;

/**
 * A courier's availability as other modules see it (the same values as dispatch's own
 * {@code CourierAvailability}, which stays private to the module).
 */
public enum CourierStatus {
    /** Can be given parcels. */
    ONLINE,
    /** Keeps their deliveries, but gets no new ones. */
    ON_BREAK,
    /** Keeps their deliveries, but gets no new ones. */
    OFFLINE
}
