package com.swiftdrop.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.reporting.dto.response.DailyDeliveryStat;
import com.swiftdrop.reporting.dto.response.ReportSummary;
import com.swiftdrop.reporting.repository.DeliveryCounts;
import com.swiftdrop.reporting.repository.ReportQueryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    /** "Today" for every test: 14 Nov 2026 in Sri Lanka. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    @Mock private ReportQueryRepository reportQueryRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        ZonedDateTime noon = TODAY.atTime(12, 0).atZone(ReportQueryRepository.REPORT_ZONE);
        reportService = new ReportService(reportQueryRepository,
                Clock.fixed(noon.toInstant(), ReportQueryRepository.REPORT_ZONE));
    }

    @Test
    void summaryCalculatesRatesFromTheCounts() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 10);
        when(reportQueryRepository.counts(from, to)).thenReturn(new DeliveryCounts(50, 36, 4));

        ReportSummary summary = reportService.summary(from, to);

        assertThat(summary.from()).isEqualTo(from);
        assertThat(summary.to()).isEqualTo(to);
        assertThat(summary.total()).isEqualTo(50);
        assertThat(summary.delivered()).isEqualTo(36);
        assertThat(summary.failed()).isEqualTo(4);
        assertThat(summary.successRate()).isEqualByComparingTo("90.00");
        assertThat(summary.failureRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void deliveriesPerDayComesFromTheRepository() {
        LocalDate day = LocalDate.of(2026, 11, 13);
        List<DailyDeliveryStat> stats = List.of(new DailyDeliveryStat(day, 5, 3, 1));
        when(reportQueryRepository.deliveriesPerDay(day, day)).thenReturn(stats);

        assertThat(reportService.deliveriesPerDay(day, day)).isEqualTo(stats);
    }

    @Test
    void withoutDatesTheLastSevenDaysAreUsed() {
        when(reportQueryRepository.counts(TODAY.minusDays(6), TODAY)).thenReturn(new DeliveryCounts(0, 0, 0));

        ReportSummary summary = reportService.summary(null, null);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(summary.to()).isEqualTo(TODAY);
    }

    @Test
    void withOnlyToTheSevenDaysEndOnThatDay() {
        LocalDate to = LocalDate.of(2026, 10, 31);

        reportService.deliveriesPerDay(null, to);

        verify(reportQueryRepository).deliveriesPerDay(LocalDate.of(2026, 10, 25), to);
    }

    @Test
    void withOnlyFromTheRangeRunsUntilToday() {
        LocalDate from = LocalDate.of(2026, 11, 1);

        reportService.deliveriesPerDay(from, null);

        verify(reportQueryRepository).deliveriesPerDay(from, TODAY);
    }

    @Test
    void aSingleDayIsAllowed() {
        reportService.deliveriesPerDay(TODAY, TODAY);

        verify(reportQueryRepository).deliveriesPerDay(TODAY, TODAY);
    }

    @Test
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> reportService.summary(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("from must be on or before to");
        verifyNoInteractions(reportQueryRepository);
    }

    @Test
    void aRangeStartingInTheFutureIsRejected() {
        assertThatThrownBy(() -> reportService.summary(TODAY.plusDays(1), TODAY.plusDays(5)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("from cannot be in the future");
        verifyNoInteractions(reportQueryRepository);
    }

    @Test
    void theLongestAllowedRangeIs366Days() {
        LocalDate to = TODAY;

        reportService.deliveriesPerDay(to.minusDays(ReportService.MAX_RANGE_DAYS - 1L), to);
        verify(reportQueryRepository).deliveriesPerDay(to.minusDays(365), to);

        assertThatThrownBy(() -> reportService.deliveriesPerDay(to.minusDays(ReportService.MAX_RANGE_DAYS), to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("366 days");
    }
}
