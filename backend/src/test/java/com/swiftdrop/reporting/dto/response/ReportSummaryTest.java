package com.swiftdrop.reporting.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReportSummaryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 7);

    @Test
    void ratesAreShareOfFinishedDeliveries() {
        // 120 created, but only 100 finished: the 20 still on the way don't count against the rate.
        ReportSummary summary = ReportSummary.of(FROM, TO, 120, 95, 5);

        assertThat(summary.successRate()).isEqualByComparingTo("95.00");
        assertThat(summary.failureRate()).isEqualByComparingTo("5.00");
        assertThat(summary.total()).isEqualTo(120);
        assertThat(summary.from()).isEqualTo(FROM);
        assertThat(summary.to()).isEqualTo(TO);
    }

    @Test
    void ratesAreRoundedToTwoDecimals() {
        ReportSummary summary = ReportSummary.of(FROM, TO, 3, 2, 1);

        assertThat(summary.successRate()).isEqualTo(new BigDecimal("66.67"));
        assertThat(summary.failureRate()).isEqualTo(new BigDecimal("33.33"));
    }

    @Test
    void noFinishedDeliveriesGivesZeroRatesInsteadOfDividingByZero() {
        ReportSummary summary = ReportSummary.of(FROM, TO, 4, 0, 0);

        assertThat(summary.successRate()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.failureRate()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void allDeliveredIsAHundredPercent() {
        ReportSummary summary = ReportSummary.of(FROM, TO, 10, 10, 0);

        assertThat(summary.successRate()).isEqualByComparingTo("100");
        assertThat(summary.failureRate()).isEqualByComparingTo("0");
    }
}
