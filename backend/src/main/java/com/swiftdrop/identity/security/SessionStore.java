package com.swiftdrop.identity.security;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Server-side sign-in sessions in Redis. Each refresh token names its session; a token is only
 * accepted while its session exists, so signing out (or a password reset) takes effect at once.
 * Sessions expire on their own with the token.
 *
 * <p>Keys: {@code swiftdrop:session:<id>} → user id, and {@code swiftdrop:user-sessions:<user id>}
 * → that user's session ids (to sign out everywhere). Redis errors are not caught here: without
 * the session store sign-in can't be checked, so callers get 503 from the global handler.
 */
@Component
public class SessionStore {

    private static final String SESSION_KEY = "swiftdrop:session:";
    private static final String USER_SESSIONS_KEY = "swiftdrop:user-sessions:";

    private final StringRedisTemplate redis;

    public SessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Starts a session for {@code userId} that ends after {@code lifetime}. Returns its id. */
    public String create(UUID userId, Duration lifetime) {
        String sessionId = UUID.randomUUID().toString();
        String userKey = USER_SESSIONS_KEY + userId;
        redis.opsForValue().set(SESSION_KEY + sessionId, userId.toString(), lifetime);
        redis.opsForSet().add(userKey, sessionId);
        // The index must outlive the user's longest session; never shorten it.
        Long ttl = redis.getExpire(userKey, TimeUnit.SECONDS);
        if (ttl == null || ttl < lifetime.toSeconds()) {
            redis.expire(userKey, lifetime);
        }
        return sessionId;
    }

    /** True while the session exists and belongs to {@code userId}. */
    public boolean isActive(String sessionId, UUID userId) {
        return sessionId != null && userId.toString().equals(redis.opsForValue().get(SESSION_KEY + sessionId));
    }

    /**
     * Ends a session after a short grace period, used when its refresh token is swapped for a new
     * one: a second tab refreshing with the same token at the same moment still succeeds.
     */
    public void retire(String sessionId, Duration grace) {
        String key = SESSION_KEY + sessionId;
        Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (ttl != null && ttl > grace.toMillis()) {
            redis.expire(key, grace);
        }
    }

    /** Signs out one session (this device) immediately. */
    public void revoke(String sessionId) {
        String key = SESSION_KEY + sessionId;
        String userId = redis.opsForValue().getAndDelete(key);
        if (userId != null) {
            redis.opsForSet().remove(USER_SESSIONS_KEY + userId, sessionId);
        }
    }

    /** Signs the user out on every device (e.g. after a password reset). */
    public void revokeAll(UUID userId) {
        String userKey = USER_SESSIONS_KEY + userId;
        Set<String> sessionIds = redis.opsForSet().members(userKey);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            redis.delete(sessionIds.stream().map(id -> SESSION_KEY + id).toList());
        }
        redis.delete(userKey);
    }
}
