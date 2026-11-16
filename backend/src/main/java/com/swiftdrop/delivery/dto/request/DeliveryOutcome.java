package com.swiftdrop.delivery.dto.request;

/** How a completed delivery ended, for the Completed Deliveries filter and rows. */
public enum DeliveryOutcome {
    /** Delivered with proof (a {@code delivery_proofs} row). */
    DELIVERED,
    /** Reported as a failed attempt (a {@code delivery_failures} row). */
    FAILED_ATTEMPT
}
