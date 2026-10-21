package com.swiftdrop.common.web;

import com.swiftdrop.common.api.ApiPaths;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight ping endpoint for the frontend and uptime checks.
 * Infrastructure health (DB, broker) lives at /actuator/health.
 */
@RestController
public class HealthController {

    @GetMapping(ApiPaths.HEALTH)
    public HealthResponse health() {
        return new HealthResponse("UP", "swiftdrop-backend", Instant.now());
    }

    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}
