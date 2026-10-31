package com.swiftdrop.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

    @Test
    void apiExceptionsKeepTheirStatusCodeAndFieldErrors() {
        var response = handler.handleApiException(
                ConflictException.forField("email", "An account with this email already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/auth/login");
        assertThat(response.getBody().fieldErrors())
                .containsExactly(new ErrorResponse.FieldError("email", "An account with this email already exists"));
    }

    @Test
    void databaseConstraintErrorsDoNotLeakTheDatabaseMessage() {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key value (email)=(nimal@example.com)"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).doesNotContain("nimal@example.com");
    }

    @Test
    void accessDeniedIs403() {
        var response = handler.handleAccessDenied(new AccessDeniedException("no"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void rateLimitsAre429WithARetryAfterHeader() {
        var response = handler.handleRateLimit(
                new RateLimitExceededException("Too many requests.", Duration.ofMillis(4_200)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5"); // rounded up
        assertThat(response.getBody().message()).isEqualTo("Too many requests. Please try again in 5 seconds.");
    }

    @Test
    void unreachableDatabaseOrRedisIs503NotABug() {
        for (Exception ex : new Exception[] {
                new RedisConnectionFailureException("Unable to connect to redis:6379"),
                new CannotCreateTransactionException("Could not open JPA EntityManager")}) {
            var response = handler.handleUnavailable(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getBody().message()).doesNotContain("redis", "JPA");
        }
    }

    @Test
    void unexpectedErrorsAre500WithoutInternals() {
        var response = handler.handleUnexpected(new IllegalStateException("db password=secret"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
