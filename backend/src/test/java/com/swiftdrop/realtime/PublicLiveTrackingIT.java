package com.swiftdrop.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.realtime.service.LiveStreamHub;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/public/tracking/{trackingNumber}/stream} end to end: a committed status change
 * goes through RabbitMQ to the open stream. Short timeouts so streams from one test have closed
 * before the next one starts.
 */
@TestPropertySource(properties = {
        "swiftdrop.tracking.stream-rate-limit.requests=" + PublicLiveTrackingIT.STREAM_RATE_LIMIT,
        "swiftdrop.tracking.stream-rate-limit.window=1m",
        "swiftdrop.tracking.max-streams-per-client=" + PublicLiveTrackingIT.MAX_STREAMS,
        "swiftdrop.tracking.stream-timeout=5s",
        "swiftdrop.tracking.heartbeat-interval=1s"})
class PublicLiveTrackingIT extends IntegrationTestBase {

    static final int STREAM_RATE_LIMIT = 4;
    static final int MAX_STREAMS = 2;

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;
    @Autowired private LiveStreamHub streamHub;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private JsonMapper json;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID customerId;

    /** An open event stream; its lines are read on a background thread. */
    private static final class OpenStream implements AutoCloseable {

        final HttpResponse<Stream<String>> response;
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

        OpenStream(HttpResponse<Stream<String>> response) {
            this.response = response;
            Thread.ofVirtual().start(() -> {
                try {
                    response.body().forEach(lines::add);
                } catch (RuntimeException closed) {
                    // The test closed the stream.
                }
            });
        }

        /** The next line matching {@code condition}, or {@code null} after {@code wait}. */
        String next(Predicate<String> condition, Duration wait) throws InterruptedException {
            long deadline = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < deadline) {
                String line = lines.poll(100, TimeUnit.MILLISECONDS);
                if (line != null && condition.test(line)) {
                    return line;
                }
            }
            return null;
        }

        @Override
        public void close() {
            response.body().close();
        }
    }

    @BeforeEach
    void setUp() {
        redis.delete(redis.keys("swiftdrop:rl:tracking:stream:*"));
        String email = "live-" + UUID.randomUUID() + "@test.swiftdrop.local";
        customerId = userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567", "x",
                Role.CUSTOMER, AccountStatus.ACTIVE)).id();
    }

    /** Waits for this test's streams to time out, and empties the queues nothing consumes yet. */
    @AfterEach
    void cleanUp() throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (streamHub.openStreamCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    private ParcelDetails parcel() {
        return parcelService.createParcel(new NewParcel(customerId, null, null,
                new NewAddress("Ayesha Khan", "+94771234567", "12 Galle Road", null, "Colombo", "Western", "00300",
                        null),
                new NewAddress("Hassan Ali", "+94719876543", "45 Peradeniya Road", null, "Kandy", "Central", "20000",
                        null),
                ParcelType.PARCEL, new BigDecimal("2.5"), null, null, null, "Books", null, null,
                new BigDecimal("750.00")));
    }

    private URI streamUri(String trackingNumber) {
        return URI.create("http://localhost:" + port + "/api/v1/public/tracking/" + trackingNumber + "/stream");
    }

    private HttpResponse<Stream<String>> request(String trackingNumber, String accept) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(streamUri(trackingNumber)).header("Accept", accept).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    private OpenStream open(String trackingNumber) throws Exception {
        return new OpenStream(request(trackingNumber, "text/event-stream"));
    }

    /** The status code and body of a request that is answered at once (an error). */
    private HttpResponse<String> answer(String trackingNumber, String accept) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(streamUri(trackingNumber)).header("Accept", accept).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aCommittedStatusChangeReachesTheOpenStream() throws Exception {
        ParcelDetails parcel = parcel();

        try (OpenStream stream = open(parcel.trackingNumber())) {
            assertThat(stream.response.statusCode()).isEqualTo(200);
            assertThat(stream.next(":connected"::equals, WAIT)).isNotNull();

            statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));

            assertThat(stream.next("event:status"::equals, WAIT)).isNotNull();
            String data = stream.next(line -> line.startsWith("data:"), WAIT);
            assertThat(data).isNotNull();
            JsonNode update = json.readTree(data.substring("data:".length()));
            assertThat(update.get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
            assertThat(update.get("previousStatus").asString()).isEqualTo("CREATED");
            assertThat(update.get("status").asString()).isEqualTo("PICKED_UP");
            assertThat(update.get("statusVersion").asLong()).isEqualTo(2);
            assertThat(update.get("changedAt").asString()).isNotBlank();
            // Public data only: no ids of the parcel or the people involved.
            assertThat(data).doesNotContain(parcel.id().toString(), customerId.toString(), "Ayesha", "Galle");
        }
    }

    @Test
    void theStreamIsSetUpForProxiesAndHeartbeats() throws Exception {
        ParcelDetails parcel = parcel();

        try (OpenStream stream = open(parcel.trackingNumber())) {
            var headers = stream.response.headers();
            assertThat(headers.firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");
            assertThat(headers.firstValue("X-Accel-Buffering")).hasValue("no");
            assertThat(headers.firstValue("Cache-Control").orElseThrow()).contains("no-cache");
            assertThat(stream.next(":ping"::equals, WAIT)).isNotNull();
        }
    }

    @Test
    void lowerCaseAndSpacesInTheNumberFollowTheSameParcel() throws Exception {
        ParcelDetails parcel = parcel();
        String typed = "%20" + parcel.trackingNumber().toLowerCase(Locale.ROOT) + "%20";

        try (OpenStream stream = open(typed)) {
            assertThat(stream.next(":connected"::equals, WAIT)).isNotNull();

            statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));

            assertThat(stream.next(line -> line.contains("\"status\":\"PICKED_UP\""), WAIT)).isNotNull();
        }
    }

    @Test
    void anotherParcelsChangeIsNotSent() throws Exception {
        ParcelDetails watched = parcel();
        ParcelDetails other = parcel();

        try (OpenStream stream = open(watched.trackingNumber())) {
            assertThat(stream.next(":connected"::equals, WAIT)).isNotNull();

            statusService.changeStatus(other.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));

            assertThat(stream.next(line -> line.startsWith("event:"), Duration.ofSeconds(2))).isNull();
        }
    }

    @Test
    void anUnknownNumberGetsTheSame404AsTheLookUp() throws Exception {
        HttpResponse<String> response = answer("SD00000000", "text/event-stream, application/json");

        assertThat(response.statusCode()).isEqualTo(404);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(body.get("message").asString())
                .isEqualTo(json.readTree(get("/api/v1/public/tracking/SD00000000").body()).get("message").asString());
        // A browser that only accepts the event stream still gets the status code.
        assertThat(answer("SD00000001", "text/event-stream").statusCode()).isEqualTo(404);
    }

    @Test
    void tooManyNewStreamsFromOneIpGet429WithRetryAfter() throws Exception {
        for (int i = 0; i < STREAM_RATE_LIMIT; i++) {
            assertThat(answer("SD0000000" + i, "application/json").statusCode()).isEqualTo(404);
        }

        HttpResponse<String> blocked = answer("SD00000009", "text/event-stream, application/json");

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValueAsLong("Retry-After").orElseThrow()).isBetween(1L, 60L);
        assertThat(json.readTree(blocked.body()).get("message").asString())
                .startsWith("Too many live tracking requests.");
    }

    @Test
    void oneIpCanHoldOnlyItsMaximumNumberOfOpenStreams() throws Exception {
        String number = parcel().trackingNumber();

        try (OpenStream first = open(number); OpenStream second = open(number)) {
            assertThat(first.response.statusCode()).isEqualTo(200);
            assertThat(second.response.statusCode()).isEqualTo(200);

            HttpResponse<String> third = answer(number, "text/event-stream, application/json");

            assertThat(third.statusCode()).isEqualTo(429);
            assertThat(third.headers().firstValue("Retry-After")).isPresent();
            assertThat(json.readTree(third.body()).get("message").asString())
                    .startsWith("Too many live updates are open from this connection.");
        }
    }
}
