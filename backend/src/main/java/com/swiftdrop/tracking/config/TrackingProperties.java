package com.swiftdrop.tracking.config;

import com.swiftdrop.common.ratelimit.RateLimit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for public tracking and the live streams, under {@code swiftdrop.tracking}. Every value
 * has a default here, so nothing has to be added to the shared application.yml; override one with
 * an environment variable, e.g. {@code SWIFTDROP_TRACKING_LOOKUP_RATE_LIMIT_REQUESTS=120}.
 *
 * @param lookupRateLimit    public look-ups per client IP ({@code GET /public/tracking/{no}}).
 *                           Slows down guessing tracking numbers.
 * @param streamRateLimit    new live streams per client IP
 * @param maxStreamsPerClient open live streams one client IP may hold at once
 * @param streamTimeout      a stream is closed after this long; the browser reconnects
 * @param heartbeatInterval  comment line sent on idle streams. Must stay below the nginx
 *                           {@code proxy_read_timeout} (60s) or nginx cuts the connection.
 */
@ConfigurationProperties(prefix = "swiftdrop.tracking")
public record TrackingProperties(
        RateLimit lookupRateLimit,
        RateLimit streamRateLimit,
        int maxStreamsPerClient,
        Duration streamTimeout,
        Duration heartbeatInterval) {

    public static final RateLimit DEFAULT_LOOKUP_RATE_LIMIT = new RateLimit(60, Duration.ofMinutes(1));
    public static final RateLimit DEFAULT_STREAM_RATE_LIMIT = new RateLimit(30, Duration.ofMinutes(1));
    public static final int DEFAULT_MAX_STREAMS_PER_CLIENT = 5;
    public static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(30);
    public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

    public TrackingProperties {
        if (lookupRateLimit == null) {
            lookupRateLimit = DEFAULT_LOOKUP_RATE_LIMIT;
        }
        if (streamRateLimit == null) {
            streamRateLimit = DEFAULT_STREAM_RATE_LIMIT;
        }
        if (maxStreamsPerClient <= 0) {
            maxStreamsPerClient = DEFAULT_MAX_STREAMS_PER_CLIENT;
        }
        if (streamTimeout == null || !streamTimeout.isPositive()) {
            streamTimeout = DEFAULT_STREAM_TIMEOUT;
        }
        if (heartbeatInterval == null || !heartbeatInterval.isPositive()) {
            heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        }
    }
}
