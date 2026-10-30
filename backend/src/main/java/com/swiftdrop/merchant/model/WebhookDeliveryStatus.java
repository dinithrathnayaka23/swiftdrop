package com.swiftdrop.merchant.model;

/** Values of {@code webhook_deliveries.delivery_status}. */
public enum WebhookDeliveryStatus {
    /** not yet delivered; may be waiting for a retry ({@code next_retry_at}) */
    PENDING,
    DELIVERED,
    /** gave up after the maximum number of retries */
    FAILED
}
