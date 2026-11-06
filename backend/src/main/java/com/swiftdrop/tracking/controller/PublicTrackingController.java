package com.swiftdrop.tracking.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.ErrorResponse;
import com.swiftdrop.common.exception.RateLimitExceededException;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.tracking.config.TrackingProperties;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.service.PublicTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public parcel tracking: no sign-in, rate limited per client IP to slow down guessing. */
@Tag(name = "Public tracking", description = "Track a parcel by its tracking number, without signing in")
@RestController
@RequestMapping(ApiPaths.PUBLIC_TRACKING)
@RequiredArgsConstructor
public class PublicTrackingController {

    static final String RATE_LIMIT_KEY_PREFIX = "tracking:lookup:";

    private final PublicTrackingService trackingService;
    private final RateLimiter rateLimiter;
    private final TrackingProperties properties;

    @Operation(summary = "Track a parcel",
            description = "Status, timeline, from/to city and weight only; no personal data. Case and "
                    + "surrounding spaces in the number are ignored.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "The parcel's public tracking")
    @ApiResponse(responseCode = "404", description = "No parcel has this tracking number",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Too many look-ups from this IP; see Retry-After",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{trackingNumber}")
    public PublicTrackingResponse track(@PathVariable String trackingNumber, HttpServletRequest request) {
        // With server.forward-headers-strategy=native this is the visitor's IP, not nginx's.
        var decision = rateLimiter.hit(RATE_LIMIT_KEY_PREFIX + request.getRemoteAddr(), properties.lookupRateLimit());
        if (!decision.allowed()) {
            throw new RateLimitExceededException("Too many tracking look-ups.", decision.retryAfter());
        }
        return trackingService.track(trackingNumber);
    }
}
