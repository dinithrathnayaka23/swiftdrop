package com.swiftdrop.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Admin Deliveries Tracking end to end: table, counts, detail, the admin stream, and admins only. */
class AdminLiveTrackingIT extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/live";

    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private JsonMapper json;

    @Value("${local.server.port}")
    private int port;

    private UUID customerId;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void signIn() {
        String email = "admin-live-" + UUID.randomUUID() + "@test.swiftdrop.local";
        customerId = userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567", "x",
                Role.CUSTOMER, AccountStatus.ACTIVE)).id();
        customerToken = jwtService.generateToken(customerId.toString(), Set.of(Role.CUSTOMER));
        UUID adminId = userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id();
        adminToken = jwtService.generateToken(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));
    }

    /** Status changes publish events that nothing else consumes yet. */
    @AfterEach
    void emptyQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    private ParcelDetails parcel() {
        return parcelService.createParcel(new NewParcel(customerId, null, null,
                new NewAddress("Ayesha Khan", "+94771234567", "12 Galle Road", null, "Colombo", "Western", null, null),
                new NewAddress("Kasun Perera", "+94771234567", "15 Park Road", null, "Kandy", "Central", "20000",
                        null),
                ParcelType.PARCEL, new BigDecimal("2.5"), null, null, null, "Clothing", "Handle with care", null,
                new BigDecimal("750.00")));
    }

    private JsonNode body(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    @Test
    void anAdminSeesTheTableRowCountsAndDetailOfADelivery() throws Exception {
        ParcelDetails parcel = parcel();
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));

        JsonNode page = body(get(BASE + "/deliveries?status=PICKED_UP&search=" + parcel.trackingNumber(), adminToken));
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(1);
        JsonNode row = page.get("content").get(0);
        assertThat(row.get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
        assertThat(row.get("status").asString()).isEqualTo("PICKED_UP");
        assertThat(row.get("statusVersion").asLong()).isEqualTo(2);
        assertThat(row.get("recipientName").asString()).isEqualTo("Kasun Perera");
        assertThat(row.get("destinationCity").asString()).isEqualTo("Kandy");

        // Search by recipient name finds it too; another status filter doesn't.
        assertThat(body(get(BASE + "/deliveries?search=kasun%20perera&size=100", adminToken)).get("content")
                .findValuesAsString("trackingNumber")).contains(parcel.trackingNumber());
        assertThat(body(get(BASE + "/deliveries?status=DELIVERED&search=" + parcel.trackingNumber(), adminToken))
                .get("page").get("totalElements").asLong()).isZero();

        assertThat(body(get(BASE + "/counts", adminToken)).get("PICKED_UP").asLong()).isPositive();

        JsonNode detail = body(get(BASE + "/deliveries/" + parcel.trackingNumber().toLowerCase(Locale.ROOT),
                adminToken));
        assertThat(detail.get("dropoffAddress").get("addressLine1").asString()).isEqualTo("15 Park Road");
        assertThat(detail.get("deliveryInstructions").asString()).isEqualTo("Handle with care");
        assertThat(detail.get("history").findValuesAsString("status")).containsExactly("CREATED", "PICKED_UP");
        assertThat(get(BASE + "/deliveries/SD00000000", adminToken).statusCode()).isEqualTo(404);
    }

    @Test
    void onlyAdminsCanUseTheBoard() throws Exception {
        for (String path : new String[] {"/deliveries", "/counts", "/deliveries/SD00000000", "/stream"}) {
            assertThat(get(BASE + path).statusCode()).as("signed out " + path).isEqualTo(401);
            assertThat(get(BASE + path, customerToken).statusCode()).as("customer " + path).isEqualTo(403);
        }
    }

    @Test
    void theAdminStreamGetsEveryParcelsUpdates() throws Exception {
        ParcelDetails parcel = parcel();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + BASE + "/stream"))
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + adminToken)
                .build();
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofLines()).get(10, TimeUnit.SECONDS);
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread.ofVirtual().start(() -> {
            try {
                response.body().forEach(lines::add);
            } catch (RuntimeException closed) {
                // The test closed the stream.
            }
        });

        try {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(lines.poll(10, TimeUnit.SECONDS)).isEqualTo(":connected");

            statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));

            String update = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (update == null && System.nanoTime() < deadline) {
                String line = lines.poll(200, TimeUnit.MILLISECONDS);
                if (line != null && line.startsWith("data:") && line.contains(parcel.trackingNumber())) {
                    update = line;
                }
            }
            assertThat(update).contains("\"status\":\"PICKED_UP\"");
        } finally {
            response.body().close();
        }
    }
}
