package com.swiftdrop.dispatch.service;

import java.time.Instant;
import java.time.ZoneId;

/** The dispatch module's "today": whole days in Sri Lanka time, for every daily count. */
final class SriLankaDay {

    static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private SriLankaDay() {
    }

    /** Midnight (Sri Lanka time) at the start of the day {@code now} falls in. */
    static Instant startOfDay(Instant now) {
        return now.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant();
    }
}
