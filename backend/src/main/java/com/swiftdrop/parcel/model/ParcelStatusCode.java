package com.swiftdrop.parcel.model;

/**
 * The parcel statuses, one per row of {@code parcel_statuses} (see {@code V4_001}). Other modules
 * use these values instead of raw strings.
 *
 * <p>Normal flow: CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED. A failed attempt
 * from PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY ends in FAILED. DELIVERED and FAILED are end states.
 */
public enum ParcelStatusCode {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED
}
