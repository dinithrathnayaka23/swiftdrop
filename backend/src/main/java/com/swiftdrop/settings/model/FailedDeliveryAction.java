package com.swiftdrop.settings.model;

/**
 * What dispatchers do by default with a parcel whose delivery failed (status FAILED).
 * Column {@code system_settings.failed_delivery_action}; read by the failed deliveries queue.
 */
public enum FailedDeliveryAction {
    /** Book another delivery attempt. */
    RETRY_DELIVERY,
    /** Send the parcel back to the sender or merchant. */
    RETURN_TO_SENDER,
    /** Keep the parcel at the hub for the recipient to collect. */
    HOLD_FOR_COLLECTION
}
