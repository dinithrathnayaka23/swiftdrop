package com.swiftdrop.reporting.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Delivery totals and success/failure rates for a date range.
 * <p>
 * Rates are percentages (0-100, two decimals) of <em>finished</em> deliveries, i.e. delivered + failed.
 * Parcels still on the way are counted in {@code total} but not in the rates, so they don't make the
 * success rate look worse than it is. With no finished deliveries both rates are 0.
 *
 * @param from        first day of the range (inclusive)
 * @param to          last day of the range (inclusive)
 * @param total       parcels created in the range
 * @param delivered   parcels delivered in the range
 * @param failed      parcels that failed in the range
 * @param successRate delivered / (delivered + failed), as a percentage
 * @param failureRate failed / (delivered + failed), as a percentage
 */
public record ReportSummary(
        LocalDate from,
        LocalDate to,
        long total,
        long delivered,
        long failed,
        BigDecimal successRate,
        BigDecimal failureRate) {

    /** Builds a summary from the counts, calculating both rates. */
    public static ReportSummary of(LocalDate from, LocalDate to, long total, long delivered, long failed) {
        long finished = delivered + failed;
        return new ReportSummary(from, to, total, delivered, failed,
                percentage(delivered, finished), percentage(failed, finished));
    }

    private static BigDecimal percentage(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }
}
