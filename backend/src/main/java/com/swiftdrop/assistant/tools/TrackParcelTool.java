package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.llm.ToolDefinition;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.tracking.config.TrackingProperties;
import com.swiftdrop.tracking.controller.PublicTrackingController;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.service.PublicTrackingService;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** {@code track_parcel}: public tracking, under the same per-IP limit as the tracking page. */
@Component
@RequiredArgsConstructor
public class TrackParcelTool implements AssistantTool {

    public static final String NAME = "track_parcel";
    static final Pattern TRACKING_NUMBER = Pattern.compile("^SD[2-9A-HJKMNP-Z]{12}$");

    static final String NOT_FOUND = "I couldn't find that number.";
    static final String RATE_LIMITED = "Too many look-ups, please wait a minute.";

    private static final ToolDefinition DEFINITION = new ToolDefinition(NAME,
            "Current status and history of one parcel by its tracking number. Call it for every status question.",
            Map.of("type", "object",
                    "properties", Map.of("trackingNumber", Map.of("type", "string",
                            "description", "SD followed by 12 letters and digits, e.g. SD7KQ2MX9TRB4H")),
                    "required", List.of("trackingNumber")));

    private final PublicTrackingService trackingService;
    private final RateLimiter rateLimiter;
    private final TrackingProperties trackingProperties;
    private final JsonMapper json;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolOutput run(ToolArguments arguments, ToolContext context) {
        String number = ParcelService.normalizeTrackingNumber(arguments.text("trackingNumber", 40));
        if (!TRACKING_NUMBER.matcher(number).matches()) {
            throw new InvalidToolArgumentException("trackingNumber", "is not a SwiftDrop tracking number");
        }
        var decision = rateLimiter.hit(PublicTrackingController.RATE_LIMIT_KEY_PREFIX + context.clientIp(),
                trackingProperties.lookupRateLimit());
        if (!decision.allowed()) {
            return ToolOutput.of(json.createObjectNode().put("error", "RATE_LIMITED").put("message", RATE_LIMITED));
        }
        PublicTrackingResponse tracking;
        try {
            tracking = trackingService.track(number);
        } catch (NotFoundException ex) {
            return ToolOutput.of(json.createObjectNode().put("found", false).put("message", NOT_FOUND));
        }
        return new ToolOutput(result(tracking), AssistantCard.tracking(tracking));
    }

    private ObjectNode result(PublicTrackingResponse tracking) {
        String label = StatusLabels.label(tracking.currentStatus());
        ObjectNode result = json.createObjectNode()
                .put("found", true)
                .put("trackingNumber", tracking.trackingNumber())
                .put("status", tracking.currentStatus().name())
                .put("statusLabel", label)
                .put("statusDescription", StatusLabels.description(tracking.currentStatus()))
                .put("lastUpdatedAt", String.valueOf(tracking.lastUpdatedAt()))
                .put("bookedAt", String.valueOf(tracking.bookedAt()))
                .put("fromCity", tracking.fromCity())
                .put("toCity", tracking.toCity())
                .put("weightKg", tracking.weightKg())
                .put("message", tracking.trackingNumber() + ": " + label + ". "
                        + StatusLabels.description(tracking.currentStatus()));
        ArrayNode history = result.putArray("history");
        tracking.history().forEach(event -> history.addObject()
                .put("status", event.statusCode().name())
                .put("at", event.changedAt().toString()));
        return result;
    }
}
