package com.swiftdrop.reporting.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.reporting.dto.response.DailyDeliveryStat;
import com.swiftdrop.reporting.dto.response.ReportSummary;
import com.swiftdrop.reporting.repository.DeliveryCounts;
import com.swiftdrop.reporting.repository.ReportQueryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Operational reports for admins: delivery totals, success/failure rates and deliveries per day.
 * Read-only. Validates the requested date range and fills in a default (the last 7 days) when the
 * caller gives none.
 */
@Service
public class ReportService {

    /** Days shown when no range is given: today and the 6 days before it. */
    static final int DEFAULT_RANGE_DAYS = 7;

    /** Longest range allowed in one request, to keep the queries cheap. */
    static final int MAX_RANGE_DAYS = 366;

    private final ReportQueryRepository reportQueryRepository;
    private final Clock clock;

    @Autowired
    public ReportService(ReportQueryRepository reportQueryRepository) {
        this(reportQueryRepository, Clock.system(ReportQueryRepository.REPORT_ZONE));
    }

    /** For tests: fixes "today". */
    ReportService(ReportQueryRepository reportQueryRepository, Clock clock) {
        this.reportQueryRepository = reportQueryRepository;
        this.clock = clock;
    }

    /** Totals and success/failure rates for the range. */
    public ReportSummary summary(LocalDate from, LocalDate to) {
        DateRange range = resolve(from, to);
        DeliveryCounts counts = reportQueryRepository.counts(range.from(), range.to());
        return ReportSummary.of(range.from(), range.to(), counts.total(), counts.delivered(), counts.failed());
    }

    /** One entry per day in the range, oldest first. */
    public List<DailyDeliveryStat> deliveriesPerDay(LocalDate from, LocalDate to) {
        DateRange range = resolve(from, to);
        return reportQueryRepository.deliveriesPerDay(range.from(), range.to());
    }

    /**
     * Fills in missing dates and checks the range. Either date may be {@code null}: {@code to}
     * defaults to today and {@code from} to {@value #DEFAULT_RANGE_DAYS} days ending on {@code to}.
     */
    DateRange resolve(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);

        if (start.isAfter(end)) {
            throw new BadRequestException("from must be on or before to");
        }
        if (start.isAfter(today)) {
            throw new BadRequestException("from cannot be in the future");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range cannot be longer than " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(start, end);
    }

    /** An inclusive range of days. */
    record DateRange(LocalDate from, LocalDate to) {
    }
}
