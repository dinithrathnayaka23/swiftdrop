package com.swiftdrop.dispatch.model;

/** Values of {@code delivery_assignments.assignment_status}. */
public enum AssignmentStatus {
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    /** ended because the parcel was given to another courier */
    REASSIGNED,
    CANCELLED
}
