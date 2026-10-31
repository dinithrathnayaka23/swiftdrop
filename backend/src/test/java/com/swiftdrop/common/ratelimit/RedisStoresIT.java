package com.swiftdrop.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.identity.security.SessionStore;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** RateLimiter and SessionStore against a real Redis (no Spring context). Requires Docker. */
class RedisStoresIT {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private final RateLimiter rateLimiter = new RateLimiter(redis);
    private final SessionStore sessions = new SessionStore(redis);

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void emptyRedis() {
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    void countsHitsWithinAWindowAndExpiresTheCounter() {
        RateLimit threePerMinute = new RateLimit(3, Duration.ofMinutes(1));

        assertThat(rateLimiter.hit("k", threePerMinute).allowed()).isTrue();
        assertThat(rateLimiter.hit("k", threePerMinute).allowed()).isTrue();
        assertThat(rateLimiter.hit("k", threePerMinute).allowed()).isTrue();
        var blocked = rateLimiter.hit("k", threePerMinute);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfter()).isBetween(Duration.ofSeconds(50), Duration.ofMinutes(1));
        assertThat(redis.getExpire("swiftdrop:rl:k", TimeUnit.SECONDS)).isBetween(1L, 60L);
        assertThat(rateLimiter.hit("other", threePerMinute).allowed()).isTrue(); // separate counter
    }

    @Test
    void blockedForChecksWithoutCountingAndResetClears() {
        RateLimit two = new RateLimit(2, Duration.ofMinutes(15));
        rateLimiter.hit("fails", two);
        assertThat(rateLimiter.blockedFor("fails", two)).isEmpty();
        assertThat(rateLimiter.blockedFor("fails", two)).isEmpty(); // peeking doesn't count

        rateLimiter.hit("fails", two);
        assertThat(rateLimiter.blockedFor("fails", two)).hasValueSatisfying(wait ->
                assertThat(wait).isGreaterThan(Duration.ofMinutes(14)));

        rateLimiter.reset("fails");
        assertThat(rateLimiter.blockedFor("fails", two)).isEmpty();
    }

    @Test
    void windowEndsByItself() throws InterruptedException {
        RateLimit onePerSecond = new RateLimit(1, Duration.ofSeconds(1));
        rateLimiter.hit("short", onePerSecond);
        assertThat(rateLimiter.hit("short", onePerSecond).allowed()).isFalse();

        Thread.sleep(1_200);

        assertThat(rateLimiter.hit("short", onePerSecond).allowed()).isTrue();
    }

    @Test
    void sessionsBelongToOneUserAndExpire() {
        UUID user = UUID.randomUUID();
        String session = sessions.create(user, Duration.ofDays(1));

        assertThat(sessions.isActive(session, user)).isTrue();
        assertThat(sessions.isActive(session, UUID.randomUUID())).isFalse(); // someone else's id
        assertThat(sessions.isActive("unknown", user)).isFalse();
        assertThat(sessions.isActive(null, user)).isFalse();
        assertThat(redis.getExpire("swiftdrop:session:" + session, TimeUnit.HOURS)).isBetween(23L, 24L);
    }

    @Test
    void revokeEndsOneSessionAndRevokeAllEndsEverySession() {
        UUID user = UUID.randomUUID();
        String laptop = sessions.create(user, Duration.ofDays(30));
        String phone = sessions.create(user, Duration.ofDays(1));
        String otherUsers = sessions.create(UUID.randomUUID(), Duration.ofDays(1));

        sessions.revoke(laptop);
        assertThat(sessions.isActive(laptop, user)).isFalse();
        assertThat(sessions.isActive(phone, user)).isTrue();

        sessions.revokeAll(user);
        assertThat(sessions.isActive(phone, user)).isFalse();
        assertThat(redis.hasKey("swiftdrop:user-sessions:" + user)).isFalse();
        assertThat(redis.keys("swiftdrop:session:" + otherUsers)).hasSize(1); // other users untouched
    }

    @Test
    void retiringShortensTheSessionToTheGracePeriodOnly() {
        UUID user = UUID.randomUUID();
        String session = sessions.create(user, Duration.ofDays(30));

        sessions.retire(session, Duration.ofSeconds(30));

        assertThat(sessions.isActive(session, user)).isTrue();
        assertThat(redis.getExpire("swiftdrop:session:" + session, TimeUnit.SECONDS)).isBetween(1L, 30L);
        sessions.retire(session, Duration.ofDays(1)); // never lengthens
        assertThat(redis.getExpire("swiftdrop:session:" + session, TimeUnit.SECONDS)).isLessThanOrEqualTo(30L);
    }

    @Test
    void theUsersSessionIndexOutlivesTheirLongestSession() {
        UUID user = UUID.randomUUID();
        sessions.create(user, Duration.ofDays(30));
        sessions.create(user, Duration.ofDays(1));

        assertThat(redis.getExpire("swiftdrop:user-sessions:" + user, TimeUnit.DAYS)).isBetween(29L, 30L);
    }
}
