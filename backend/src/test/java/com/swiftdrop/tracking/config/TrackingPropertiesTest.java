package com.swiftdrop.tracking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.ratelimit.RateLimit;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TrackingPropertiesTest {

    @Test
    void missingValuesFallBackToDefaults() {
        var properties = new TrackingProperties(null, null, 0, null, null);

        assertThat(properties.lookupRateLimit()).isEqualTo(TrackingProperties.DEFAULT_LOOKUP_RATE_LIMIT);
        assertThat(properties.streamRateLimit()).isEqualTo(TrackingProperties.DEFAULT_STREAM_RATE_LIMIT);
        assertThat(properties.maxStreamsPerClient()).isEqualTo(TrackingProperties.DEFAULT_MAX_STREAMS_PER_CLIENT);
        assertThat(properties.streamTimeout()).isEqualTo(TrackingProperties.DEFAULT_STREAM_TIMEOUT);
        assertThat(properties.heartbeatInterval()).isEqualTo(TrackingProperties.DEFAULT_HEARTBEAT_INTERVAL);
    }

    @Test
    void configuredValuesAreKept() {
        var lookup = new RateLimit(5, Duration.ofSeconds(10));
        var properties = new TrackingProperties(lookup, lookup, 2, Duration.ofMinutes(1), Duration.ofSeconds(5));

        assertThat(properties.lookupRateLimit()).isEqualTo(lookup);
        assertThat(properties.maxStreamsPerClient()).isEqualTo(2);
        assertThat(properties.streamTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void defaultHeartbeatStaysBelowTheNginxReadTimeout() {
        assertThat(TrackingProperties.DEFAULT_HEARTBEAT_INTERVAL).isLessThan(Duration.ofSeconds(60));
    }
}
