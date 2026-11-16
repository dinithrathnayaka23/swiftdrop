package com.swiftdrop.reporting.dto.response;

import java.time.LocalDate;

/**
 * Deliveries for one day of the "deliveries per day" report.
 *
 * @param date      the day (in the reporting time zone)
 * @param total     parcels created that day
 * @param delivered parcels that reached DELIVERED that day
 * @param failed    parcels that reached FAILED that day
 */
public record DailyDeliveryStat(
        LocalDate date,
        long total,
        long delivered,
        long failed) {
}
