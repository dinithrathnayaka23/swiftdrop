package com.swiftdrop.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CourierDayTest {

    private final SystemSettingsService settings = mock(SystemSettingsService.class);
    private final SystemSettingsView view = mock(SystemSettingsView.class);

    private CourierDay at(String instant, String timeZone) {
        when(settings.current()).thenReturn(view);
        when(view.timeZone()).thenReturn(timeZone);
        return new CourierDay(settings, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void todayStartsAtMidnightInTheAdminsTimeZone() {
        // 20:00 UTC on 14 Nov is already 01:30 on 15 Nov in Colombo (UTC+5:30).
        CourierDay day = at("2026-11-14T20:00:00Z", "Asia/Colombo");

        assertThat(day.today()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(day.startOfToday()).isEqualTo(Instant.parse("2026-11-14T18:30:00Z"));
        assertThat(day.startOf(LocalDate.of(2026, 11, 1))).isEqualTo(Instant.parse("2026-10-31T18:30:00Z"));
    }

    @Test
    void fallsBackToUtcWhenTheSettingIsNotATimeZone() {
        CourierDay day = at("2026-11-14T20:00:00Z", "Not/AZone");

        assertThat(day.zone()).isEqualTo(ZoneOffset.UTC);
        assertThat(day.today()).isEqualTo(LocalDate.of(2026, 11, 14));
    }
}
