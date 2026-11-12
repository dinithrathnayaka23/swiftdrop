package com.swiftdrop.dispatch.model;

/**
 * Values of {@code delivery_assignments.assignment_status}. ASSIGNED and IN_PROGRESS are open
 * ({@code ended_at IS NULL}); the others are end states.
 */
public enum AssignmentStatus {
    /** given to the courier, parcel not picked up yet */
    ASSIGNED,
    /** the courier has the parcel (picked up, in transit or out for delivery) */
    IN_PROGRESS,
    /** the parcel was delivered */
    COMPLETED,
    /** the parcel ended FAILED (V6_002); not a completed delivery */
    FAILED,
    /** ended because the parcel was given to another courier */
    REASSIGNED,
    /** ended because the dispatcher took the parcel back before pickup */
    CANCELLED;

    /** Whether an assignment in this status is still open. */
    public boolean isOpen() {
        return this == ASSIGNED || this == IN_PROGRESS;
    }
}
