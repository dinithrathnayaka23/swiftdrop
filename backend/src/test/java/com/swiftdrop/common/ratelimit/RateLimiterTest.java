package com.swiftdrop.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Decision logic and failure handling. Real Redis behaviour is covered by RedisStoresIT. */
@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    private static final RateLimit FIVE_PER_MINUTE = new RateLimit(5, Duration.ofMinutes(1));

    @Mock private StringRedisTemplate redis;

    private RateLimiter rateLimiter() {
        return new RateLimiter(redis);
    }

    @SuppressWarnings("unchecked")
    private void scriptReturns(long count, long millisLeft) {
        when(redis.execute(any(RedisScript.class), eq(List.of("swiftdrop:rl:k")), anyString()))
                .thenReturn(List.of(count, millisLeft));
    }

    @Test
    void allowsUpToTheLimit() {
        scriptReturns(5, 30_000);

        assertThat(rateLimiter().hit("k", FIVE_PER_MINUTE).allowed()).isTrue();
    }

    @Test
    void blocksAfterTheLimitAndSaysHowLongToWait() {
        scriptReturns(6, 30_000);

        var decision = rateLimiter().hit("k", FIVE_PER_MINUTE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsOpenWhenRedisIsDown() {
        when(redis.execute(any(RedisScript.class), any(List.class), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(rateLimiter().hit("k", FIVE_PER_MINUTE).allowed()).isTrue();
        assertThat(rateLimiter().blockedFor("k", FIVE_PER_MINUTE)).isEmpty();
        assertThatCode(() -> rateLimiter().reset("k")).doesNotThrowAnyException();
    }

    @Test
    void anonymiseIsStableIgnoresCaseAndHidesTheValue() {
        String key = RateLimiter.anonymise("Nimal@Example.com ");

        assertThat(key).isEqualTo(RateLimiter.anonymise("nimal@example.com")).hasSize(32).doesNotContain("nimal");
    }

    @Test
    void limitsMustBePositive() {
        assertThatThrownBy(() -> new RateLimit(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimit(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
