package com.swiftdrop.realtime.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ErrorResponse;
import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.realtime.service.LiveStreamHub;
import com.swiftdrop.tracking.config.TrackingProperties;
import com.swiftdrop.tracking.service.PublicTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live status updates for one parcel, as server-sent events, for the public tracking page. No
 * sign-in, like the tracking look-up (SecurityConfig permits {@code /public/tracking/**}).
 *
 * <p>No {@code produces} on purpose, and errors (404, 429) are answered by {@link #handleError}
 * as the standard JSON error body, whatever the browser accepts.
 */
@Tag(name = "Public tracking", description = "Track a parcel by its tracking number, without signing in")
@RestController
@RequestMapping(ApiPaths.PUBLIC_TRACKING)
@RequiredArgsConstructor
public class PublicLiveTrackingController {

    static final String RATE_LIMIT_KEY_PREFIX = "tracking:stream:";

    private final PublicTrackingService trackingService;
    private final LiveStreamHub streamHub;
    private final RateLimiter rateLimiter;
    private final TrackingProperties properties;

    @Operation(summary = "Follow a parcel's status live",
            description = "Server-sent events: `event: status` with a LiveStatusUpdate when the status changes, "
                    + "and a `: ping` comment every 25 seconds. Load the current state with GET "
                    + "/public/tracking/{trackingNumber} first; apply only updates with a newer statusVersion.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "The event stream (text/event-stream)")
    @ApiResponse(responseCode = "404", description = "No parcel has this tracking number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Too many new or open streams from this IP; see Retry-After",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{trackingNumber}/stream")
    public ResponseEntity<SseEmitter> stream(@PathVariable String trackingNumber, HttpServletRequest request) {
        // With server.forward-headers-strategy=native this is the visitor's IP, not nginx's.
        String clientIp = request.getRemoteAddr();
        var decision = rateLimiter.hit(RATE_LIMIT_KEY_PREFIX + clientIp, properties.streamRateLimit());
        if (!decision.allowed()) {
            throw new RateLimitExceededException("Too many live tracking requests.", decision.retryAfter());
        }
        String knownNumber = trackingService.requireKnownTrackingNumber(trackingNumber);
        SseEmitter emitter = streamHub.openParcelStream(knownNumber, clientIp);
        return StreamResponses.stream(emitter);
    }

    /** This endpoint's errors (404, 429) as the standard JSON error body, whatever the browser accepts. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleError(ApiException ex, HttpServletRequest request) {
        return StreamResponses.error(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(),
                ex.getFieldErrors(), ex);
    }
}
