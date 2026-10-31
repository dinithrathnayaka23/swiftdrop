package com.swiftdrop.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters in Redis, shared by every backend instance and kept across restarts.
 *
 * <p>Fails open: if Redis can't be reached, requests are allowed (and a warning is logged), so a
 * Redis outage degrades protection instead of blocking every user.
 */
@Slf4j
@Component
public class RateLimiter {

    private static final String PREFIX = "swiftdrop:rl:";

    /** Atomically counts one hit; the first hit starts the window. Returns {count, ms left}. */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HIT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Whether a request is allowed, and if not, how long until the window resets. */
    public record Decision(boolean allowed, Duration retryAfter) {

        static final Decision ALLOWED = new Decision(true, Duration.ZERO);
    }

    /** Counts this request against {@code limit} and says whether it is still within it. */
    public Decision hit(String key, RateLimit limit) {
        try {
            List<?> result = redis.execute(HIT, List.of(PREFIX + key), String.valueOf(limit.window().toMillis()));
            long count = ((Number) result.get(0)).longValue();
            long millisLeft = ((Number) result.get(1)).longValue();
            return count <= limit.requests()
                    ? Decision.ALLOWED
                    : new Decision(false, Duration.ofMillis(Math.max(millisLeft, 1000)));
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getClass().getSimpleName());
            return Decision.ALLOWED;
        }
    }

    /** Checks {@code limit} without counting a request (e.g. "too many failed attempts?"). */
    public Optional<Duration> blockedFor(String key, RateLimit limit) {
        try {
            String value = redis.opsForValue().get(PREFIX + key);
            if (value == null || Long.parseLong(value) < limit.requests()) {
                return Optional.empty();
            }
            Long millisLeft = redis.getExpire(PREFIX + key, TimeUnit.MILLISECONDS);
            return Optional.of(Duration.ofMillis(Math.max(millisLeft == null ? 0 : millisLeft, 1000)));
        } catch (DataAccessException | NumberFormatException ex) {
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void reset(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (DataAccessException ex) {
            log.warn("Could not reset rate limit: {}", ex.getClass().getSimpleName());
        }
    }

    /**
     * Stable, non-reversible key part for personal data (e.g. an email), so Redis keys never
     * contain it in plain text. Case and surrounding spaces are ignored.
     */
    public static String anonymise(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
