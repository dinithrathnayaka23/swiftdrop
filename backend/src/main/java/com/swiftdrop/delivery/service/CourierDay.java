package com.swiftdrop.delivery.service;

import com.swiftdrop.settings.service.SystemSettingsService;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Days as the courier sees them: in the time zone the admin set in System Settings (not the
 * server's), so "today" and a date filter start at local midnight.
 */
@Component
public class CourierDay {

    private final SystemSettingsService settings;
    private final Clock clock;

    @Autowired
    public CourierDay(SystemSettingsService settings) {
        this(settings, Clock.systemUTC());
    }

    CourierDay(SystemSettingsService settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /** The admin's time zone; UTC if the setting can't be read as one. */
    public ZoneId zone() {
        try {
            return ZoneId.of(settings.current().timeZone());
        } catch (DateTimeException | NullPointerException e) {
            return ZoneOffset.UTC;
        }
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    /** Local midnight at the start of this date. */
    public Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone()).toInstant();
    }

    public Instant startOfToday() {
        return startOf(today());
    }
}
