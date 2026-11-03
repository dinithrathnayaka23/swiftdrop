package com.swiftdrop.parcel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrackingNumberGeneratorTest {

    private final TrackingNumberGenerator generator = new TrackingNumberGenerator();

    @Test
    void numbersStartWithSdAndUseOnlyEasyToReadCharacters() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.next()).matches("SD[2-9A-HJKMNP-Z]{12}");
        }
    }

    @Test
    void numbersDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSize(10_000);
    }
}
