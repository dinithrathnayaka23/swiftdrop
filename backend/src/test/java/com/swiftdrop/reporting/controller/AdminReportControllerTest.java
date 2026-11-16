package com.swiftdrop.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.reporting.dto.response.DailyDeliveryStat;
import com.swiftdrop.reporting.dto.response.ReportSummary;
import com.swiftdrop.reporting.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 7);

    @Mock private ReportService reportService;
    @InjectMocks private AdminReportController controller;

    @Test
    void onlyDispatcherAdminsMayUseIt() {
        assertThat(AdminReportController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void summaryPassesTheDatesThrough() {
        ReportSummary summary = ReportSummary.of(FROM, TO, 10, 8, 2);
        when(reportService.summary(FROM, TO)).thenReturn(summary);

        assertThat(controller.summary(FROM, TO)).isEqualTo(summary);
    }

    @Test
    void summaryWithoutDatesLeavesTheDefaultsToTheService() {
        ReportSummary summary = ReportSummary.of(FROM, TO, 0, 0, 0);
        when(reportService.summary(null, null)).thenReturn(summary);

        assertThat(controller.summary(null, null)).isEqualTo(summary);
    }

    @Test
    void deliveriesPerDayPassesTheDatesThrough() {
        List<DailyDeliveryStat> stats = List.of(new DailyDeliveryStat(FROM, 3, 2, 1));
        when(reportService.deliveriesPerDay(FROM, TO)).thenReturn(stats);

        assertThat(controller.deliveriesPerDay(FROM, TO)).isEqualTo(stats);
    }

    @Test
    void invalidRangesFromTheServiceAreNotSwallowed() {
        when(reportService.summary(TO, FROM)).thenThrow(new BadRequestException("from must be on or before to"));

        assertThatThrownBy(() -> controller.summary(TO, FROM)).isInstanceOf(BadRequestException.class);
    }
}
