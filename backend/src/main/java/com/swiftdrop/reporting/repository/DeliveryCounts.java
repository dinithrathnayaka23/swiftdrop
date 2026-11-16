package com.swiftdrop.reporting.repository;

/**
 * Raw delivery counts for a date range, before rates are worked out.
 *
 * @param total     parcels created in the range
 * @param delivered parcels that reached DELIVERED in the range
 * @param failed    parcels that reached FAILED in the range
 */
public record DeliveryCounts(long total, long delivered, long failed) {
}
