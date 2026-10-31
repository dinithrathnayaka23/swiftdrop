package com.swiftdrop.common.exception;

import java.time.Duration;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 429: too many requests. {@link #getRetryAfter()} becomes the Retry-After header. */
@Getter
public class RateLimitExceededException extends ApiException {

    private final Duration retryAfter;

    public RateLimitExceededException(String what, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS,
                what + " Please try again in " + humanise(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds, rounded up, for the Retry-After header. */
    public long getRetryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    private static String humanise(Duration duration) {
        long seconds = Math.max(1, (duration.toMillis() + 999) / 1000);
        if (seconds < 60) {
            return seconds == 1 ? "1 second" : seconds + " seconds";
        }
        long minutes = (seconds + 59) / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
