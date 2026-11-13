package com.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.ratelimit.RateLimiter;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.tracking.config.TrackingProperties;
import com.swiftdrop.tracking.dto.response.PublicTrackingEventResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.service.PublicTrackingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TrackParcelToolTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ToolContext CONTEXT = new ToolContext(UUID.randomUUID(), "203.0.113.7");
    private static final String NUMBER = "SD7KQ2MX9TRB4H";

    private final PublicTrackingService trackingService = mock(PublicTrackingService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final TrackingProperties properties = new TrackingProperties(null, null, 0, null, null);
    private final TrackParcelTool tool = new TrackParcelTool(trackingService, rateLimiter, properties, JSON);

    private static ToolArguments number(String value) {
        return new ToolArguments(JSON.createObjectNode().put("trackingNumber", value));
    }

    private void allowLookups(boolean allowed) {
        when(rateLimiter.hit(eq("tracking:lookup:203.0.113.7"), eq(properties.lookupRateLimit())))
                .thenReturn(new RateLimiter.Decision(allowed, allowed ? Duration.ZERO : Duration.ofSeconds(30)));
    }

    @Test
    void returnsTheStatusWithServerWrittenWordsAndATrackingCard() {
        allowLookups(true);
        Instant now = Instant.parse("2026-11-12T08:00:00Z");
        var tracking = new PublicTrackingResponse(NUMBER, ParcelStatusCode.OUT_FOR_DELIVERY, now, now, "Colombo",
                "Kandy", new BigDecimal("2.5"), List.of(new PublicTrackingEventResponse(ParcelStatusCode.CREATED, now)));
        when(trackingService.track(NUMBER)).thenReturn(tracking);

        ToolOutput output = tool.run(number(" sd7kq2mx9trb4h "), CONTEXT);

        assertThat(output.result().get("statusLabel").asString()).isEqualTo("Out for delivery");
        assertThat(output.result().get("message").asString()).startsWith(NUMBER + ": Out for delivery.");
        assertThat(output.result().get("toCity").asString()).isEqualTo("Kandy");
        assertThat(output.card()).isEqualTo(AssistantCard.tracking(tracking));
    }

    @Test
    void unknownNumbersAndTheSharedRateLimitBecomeSafeResults() {
        allowLookups(true);
        when(trackingService.track(NUMBER)).thenThrow(new NotFoundException("No parcel"));
        ToolOutput notFound = tool.run(number(NUMBER), CONTEXT);
        assertThat(notFound.result().get("found").asBoolean()).isFalse();
        assertThat(notFound.result().get("message").asString()).isEqualTo(TrackParcelTool.NOT_FOUND);
        assertThat(notFound.card()).isNull();

        allowLookups(false);
        ToolOutput limited = tool.run(number(NUMBER), CONTEXT);
        assertThat(limited.result().get("error").asString()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void rejectsSomethingThatIsNotATrackingNumberWithoutALookUp() {
        assertThatThrownBy(() -> tool.run(number("SD0000000000IO"), CONTEXT))
                .isInstanceOf(InvalidToolArgumentException.class);
        verifyNoInteractions(trackingService);
        verifyNoInteractions(rateLimiter);
    }
}
