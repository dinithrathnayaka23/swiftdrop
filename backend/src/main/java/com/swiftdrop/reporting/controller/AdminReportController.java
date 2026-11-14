package com.swiftdrop.reporting.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.reporting.dto.response.DailyDeliveryStat;
import com.swiftdrop.reporting.dto.response.ReportSummary;
import com.swiftdrop.reporting.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational reports for dispatchers/admins. Both dates are optional ISO dates (yyyy-MM-dd); without
 * them the last 7 days are returned.
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/admin/reports")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    /** Totals and success/failure rates for the range. */
    @GetMapping("/summary")
    public ReportSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.summary(from, to);
    }

    /** Created, delivered and failed counts for each day in the range, oldest first. */
    @GetMapping("/deliveries-per-day")
    public List<DailyDeliveryStat> deliveriesPerDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.deliveriesPerDay(from, to);
    }
}
