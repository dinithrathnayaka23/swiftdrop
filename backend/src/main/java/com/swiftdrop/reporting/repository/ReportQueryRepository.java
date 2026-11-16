package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.dto.response.DailyDeliveryStat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only report queries over the parcel tables (owned by the parcel module). Reports only ever
 * read, so plain SQL is used instead of the parcel module's entities or repositories.
 * <p>
 * A parcel counts as:
 * <ul>
 *   <li><b>total</b> on the day it was created ({@code parcels.created_at});</li>
 *   <li><b>delivered</b> / <b>failed</b> on the day it reached DELIVERED / FAILED
 *       ({@code parcel_status_history.changed_at}).</li>
 * </ul>
 * Days follow Sri Lankan time, and a range runs from the start of {@code from} to the end of
 * {@code to}.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryRepository {

    /** The time zone the business runs in; report days start at midnight here. */
    public static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Colombo");

    private static final String DAILY_SQL = """
            WITH days AS (
                SELECT CAST(d AS date) AS day
                FROM generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp), INTERVAL '1 day') AS d
            ),
            created AS (
                SELECT CAST(created_at AT TIME ZONE :zone AS date) AS day, COUNT(*) AS total
                FROM parcels
                WHERE created_at >= :start AND created_at < :end
                GROUP BY 1
            ),
            finished AS (
                SELECT CAST(changed_at AT TIME ZONE :zone AS date) AS day,
                       COUNT(DISTINCT parcel_id) FILTER (WHERE status_code = 'DELIVERED') AS delivered,
                       COUNT(DISTINCT parcel_id) FILTER (WHERE status_code = 'FAILED') AS failed
                FROM parcel_status_history
                WHERE status_code IN ('DELIVERED', 'FAILED')
                  AND changed_at >= :start AND changed_at < :end
                GROUP BY 1
            )
            SELECT days.day,
                   COALESCE(created.total, 0)       AS total,
                   COALESCE(finished.delivered, 0)  AS delivered,
                   COALESCE(finished.failed, 0)     AS failed
            FROM days
            LEFT JOIN created  ON created.day = days.day
            LEFT JOIN finished ON finished.day = days.day
            ORDER BY days.day
            """;

    private static final String COUNTS_SQL = """
            SELECT
                (SELECT COUNT(*)
                 FROM parcels
                 WHERE created_at >= :start AND created_at < :end) AS total,
                (SELECT COUNT(DISTINCT parcel_id)
                 FROM parcel_status_history
                 WHERE status_code = 'DELIVERED'
                   AND changed_at >= :start AND changed_at < :end) AS delivered,
                (SELECT COUNT(DISTINCT parcel_id)
                 FROM parcel_status_history
                 WHERE status_code = 'FAILED'
                   AND changed_at >= :start AND changed_at < :end) AS failed
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** One row per day from {@code from} to {@code to} (inclusive), including days with no deliveries. */
    public List<DailyDeliveryStat> deliveriesPerDay(LocalDate from, LocalDate to) {
        return jdbc.query(DAILY_SQL, params(from, to), (rs, rowNum) -> new DailyDeliveryStat(
                rs.getObject("day", LocalDate.class),
                rs.getLong("total"),
                rs.getLong("delivered"),
                rs.getLong("failed")));
    }

    /** Created, delivered and failed counts for the whole range. */
    public DeliveryCounts counts(LocalDate from, LocalDate to) {
        return jdbc.queryForObject(COUNTS_SQL, params(from, to), (rs, rowNum) -> new DeliveryCounts(
                rs.getLong("total"),
                rs.getLong("delivered"),
                rs.getLong("failed")));
    }

    private static MapSqlParameterSource params(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("zone", REPORT_ZONE.getId())
                .addValue("start", startOf(from))
                .addValue("end", startOf(to.plusDays(1)));
    }

    /** Midnight at the start of the day in the report time zone, as a UTC timestamp. */
    static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(REPORT_ZONE).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }
}
