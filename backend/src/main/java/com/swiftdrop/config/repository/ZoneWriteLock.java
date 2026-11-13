package com.swiftdrop.config.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serialises every change to zones and coverage areas. The case-insensitive name rule and the
 * overlap rule can't be database constraints (the overlap depends on the zones' active flag), so
 * two dispatchers saving at the same moment could otherwise both pass the checks.
 *
 * <p>A PostgreSQL transaction-scoped advisory lock: taken first in each write, released when the
 * transaction ends. Zone changes are rare, so one constant key is enough.
 */
@Component
@RequiredArgsConstructor
public class ZoneWriteLock {

    /** Arbitrary, but unique to this lock in the application. */
    static final long KEY = 6_000_001L;

    private final JdbcTemplate jdbc;

    /** Waits until no other transaction holds the lock. Must run inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (ResultSetExtractor<Void>) rs -> null, KEY);
    }
}
