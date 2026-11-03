package com.swiftdrop.parcel.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The parcel statuses, one per row of {@code parcel_statuses} (see {@code V4_001}), and the
 * allowed changes between them (report section 3.6, Figure 8). Other modules use these values
 * instead of raw strings.
 *
 * <pre>
 *   CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
 *              PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY  -> FAILED
 * </pre>
 *
 * A parcel moves forward one step at a time. DELIVERED and FAILED are end states.
 */
public enum ParcelStatusCode {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED;

    /** The statuses a parcel with this status may change to; empty for an end state. */
    public Set<ParcelStatusCode> nextStatuses() {
        Set<ParcelStatusCode> next = switch (this) {
            case CREATED -> EnumSet.of(PICKED_UP);
            case PICKED_UP -> EnumSet.of(IN_TRANSIT, FAILED);
            case IN_TRANSIT -> EnumSet.of(OUT_FOR_DELIVERY, FAILED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, FAILED);
            case DELIVERED, FAILED -> EnumSet.noneOf(ParcelStatusCode.class);
        };
        return Collections.unmodifiableSet(next);
    }

    public boolean canChangeTo(ParcelStatusCode next) {
        return nextStatuses().contains(next);
    }

    /** {@code true} for DELIVERED and FAILED: no further changes are allowed. */
    public boolean isTerminal() {
        return nextStatuses().isEmpty();
    }
}
