package com.swiftdrop.delivery.repository;

import com.swiftdrop.delivery.dto.request.DeliveryOutcome;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A courier's finished deliveries: their proofs (DELIVERED) and failed attempts, newest first.
 * Private to the delivery module; reads only its own tables, so it keeps working after dispatch has
 * ended the assignments. Uses the {@code (courier_user_id, time)} indexes from V7_001 and V7_002.
 */
@Repository
@RequiredArgsConstructor
public class CompletedDeliveryQueries {

    /** Both kinds as one list; the filter and paging are added around it. */
    private static final String COMPLETED = """
            SELECT p.parcel_id, 'DELIVERED' AS outcome, p.captured_at AS completed_at,
                   p.recipient_name AS received_by, CAST(NULL AS VARCHAR) AS reason_label,
                   CAST(NULL AS TEXT) AS note,
                   CASE WHEN p.photo_storage_key IS NOT NULL THEN p.proof_id END AS proof_id
            FROM delivery_proofs p
            WHERE p.courier_user_id = :courier AND p.captured_at >= :from AND p.captured_at < :to
              AND :outcome IN ('ALL', 'DELIVERED')
            UNION ALL
            SELECT f.parcel_id, 'FAILED_ATTEMPT', f.recorded_at, NULL, r.label, f.note, NULL
            FROM delivery_failures f JOIN failure_reasons r ON r.reason_code = f.reason_code
            WHERE f.courier_user_id = :courier AND f.recorded_at >= :from AND f.recorded_at < :to
              AND :outcome IN ('ALL', 'FAILED_ATTEMPT')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param from    inclusive
     * @param to      exclusive
     * @param outcome {@code null} for both kinds
     */
    public Page<CompletedDeliveryRow> find(UUID courierUserId, Instant from, Instant to, DeliveryOutcome outcome,
                                           Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courier", courierUserId)
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to))
                .addValue("outcome", outcome == null ? "ALL" : outcome.name())
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + COMPLETED + ") c", params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }
        List<CompletedDeliveryRow> rows = jdbc.query("SELECT * FROM (" + COMPLETED + ") c"
                        + " ORDER BY c.completed_at DESC, c.parcel_id LIMIT :limit OFFSET :offset",
                params, CompletedDeliveryQueries::row);
        return new PageImpl<>(rows, pageable, total);
    }

    private static CompletedDeliveryRow row(ResultSet rs, int rowNum) throws SQLException {
        return new CompletedDeliveryRow(
                rs.getObject("parcel_id", UUID.class),
                DeliveryOutcome.valueOf(rs.getString("outcome")),
                rs.getObject("completed_at", OffsetDateTime.class).toInstant(),
                rs.getString("received_by"),
                rs.getString("reason_label"),
                rs.getString("note"),
                rs.getObject("proof_id", UUID.class));
    }
}
