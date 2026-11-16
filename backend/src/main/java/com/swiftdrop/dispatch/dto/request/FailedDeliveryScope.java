package com.swiftdrop.dispatch.dto.request;

/** Which failed deliveries the queue lists. */
public enum FailedDeliveryScope {
    /** Parcels in FAILED_ATTEMPT: waiting for the dispatcher to retry or close them. */
    ATTENTION,
    /** Parcels closed as FAILED (an end state). */
    CLOSED
}
