package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SriLankaDayTest {

    /** Sri Lanka is UTC+5:30, so local midnight of 14 Nov is 18:30 UTC on 13 Nov. */
    private static final Instant MIDNIGHT = Instant.parse("2026-11-13T18:30:00Z");

    @Test
    void todayStartsAtSriLankanMidnight() {
        assertThat(SriLankaDay.startOfDay(MIDNIGHT)).isEqualTo(MIDNIGHT);
        assertThat(SriLankaDay.startOfDay(MIDNIGHT.plusSeconds(1))).isEqualTo(MIDNIGHT);
        assertThat(SriLankaDay.startOfDay(Instant.parse("2026-11-14T18:29:59Z"))).isEqualTo(MIDNIGHT);
    }

    @Test
    void aSecondBeforeMidnightIsStillYesterday() {
        assertThat(SriLankaDay.startOfDay(MIDNIGHT.minusSeconds(1))).isEqualTo(MIDNIGHT.minusSeconds(86_400));
    }
}
