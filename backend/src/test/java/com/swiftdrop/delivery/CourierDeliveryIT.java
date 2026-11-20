package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.storage.LocalFileProofStorage;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The courier app's whole journey through the real API, as a courier uses it: from an assigned
 * parcel to a delivered one with proof, plus the rules around it (only your own deliveries, a failed
 * attempt needs a reason, the proof photo is private, the profile card). The detailed per-endpoint
 * cases are in {@link CourierDeliveriesIT}.
 */
class CourierDeliveryIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String PASSWORD = "courier2026";
    private static final String DELIVERIES = "/api/v1/courier/deliveries";
    private static final Duration LISTENER_WAIT = Duration.ofSeconds(10);

    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate db;
    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AmqpAdmin amqpAdmin;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Courier(UUID courierId, UUID userId, String token) {
    }

    private record Customer(UUID userId, String token) {
    }

    /** The status events go to the notification and webhook queues, which nothing reads in tests. */
    @BeforeEach
    @AfterEach
    void emptyQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    /** No test photos are left in the storage folder. */
    @AfterAll
    static void removeStoredPhotos(@Autowired JdbcTemplate db, @Autowired LocalFileProofStorage storage) {
        db.queryForList("SELECT photo_storage_key FROM delivery_proofs WHERE photo_storage_key IS NOT NULL",
                String.class).forEach(storage::delete);
    }

    @Test
    void theWholeJourneyFromAssignedToDeliveredWritesHistoryProofAndEndsTheAssignment() throws Exception {
        Courier courier = newCourier();
        Customer customer = newCustomer();
        ParcelDetails parcel = newParcel(customer.userId());
        assign(parcel, courier);

        JsonNode today = body(get(DELIVERIES + "/today", courier.token()));
        assertThat(today.get("deliveries").get(0).get("trackingNumber").asString())
                .isEqualTo(parcel.trackingNumber());

        for (String step : List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY")) {
            HttpResponse<String> moved = post(DELIVERIES + "/" + parcel.id() + "/status",
                    "{\"status\":\"%s\"}".formatted(step), courier.token());
            assertThat(moved.statusCode()).as(step).isEqualTo(200);
        }
        HttpResponse<String> delivered = deliver(parcel, courier.token(), "Kasun Perera", png(800, 600));
        assertThat(delivered.statusCode()).isEqualTo(200);
        assertThat(body(delivered).get("currentStatus").asString()).isEqualTo("DELIVERED");

        // The parcel module's history: every step, by this courier, in order.
        List<Map<String, Object>> history = db.queryForList("""
                SELECT status_code, changed_by_user_id FROM parcel_status_history
                WHERE parcel_id = ? ORDER BY changed_at, status_history_id""", parcel.id());
        assertThat(history).extracting(row -> row.get("status_code"))
                .containsExactly("CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(history.subList(1, 5)).extracting(row -> row.get("changed_by_user_id"))
                .containsOnly(courier.userId());

        // The proof, pointing at the DELIVERED entry, with the photo stored.
        Map<String, Object> proof = db.queryForMap("""
                SELECT p.recipient_name, p.photo_storage_key, h.status_code
                FROM delivery_proofs p JOIN parcel_status_history h USING (status_history_id)
                WHERE p.parcel_id = ?""", parcel.id());
        assertThat(proof.get("recipient_name")).isEqualTo("Kasun Perera");
        assertThat(proof.get("photo_storage_key")).isNotNull();
        assertThat(proof.get("status_code")).isEqualTo("DELIVERED");

        // Dispatch hears the event and ends the assignment as COMPLETED.
        Map<String, Object> assignment = awaitEndedAssignment(parcel);
        assertThat(assignment.get("assignment_status")).isEqualTo("COMPLETED");

        // The delivery leaves Today's Deliveries and appears in Completed Deliveries.
        JsonNode after = body(get(DELIVERIES + "/today", courier.token()));
        assertThat(after.get("deliveries")).isEmpty();
        assertThat(after.get("counts").get("completedToday").asInt()).isEqualTo(1);
        JsonNode completed = body(get(DELIVERIES + "/completed", courier.token())).get("content");
        assertThat(completed.get(0).get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
        assertThat(completed.get(0).get("outcome").asString()).isEqualTo("DELIVERED");
    }

    @Test
    void courierACannotSeeOrChangeCourierBsParcel() throws Exception {
        Courier courierA = newCourier();
        Courier courierB = newCourier();
        ParcelDetails parcelOfB = newParcel(newCustomer().userId());
        assign(parcelOfB, courierB);
        String path = DELIVERIES + "/" + parcelOfB.id();

        // 404, not 403: courier A can't even tell the parcel exists.
        assertThat(get(path, courierA.token()).statusCode()).isEqualTo(404);
        assertThat(get(DELIVERIES + "/by-tracking-number/" + parcelOfB.trackingNumber(), courierA.token())
                .statusCode()).isEqualTo(404);
        assertThat(post(path + "/status", "{\"status\":\"PICKED_UP\"}", courierA.token()).statusCode())
                .isEqualTo(404);
        assertThat(post(path + "/fail", "{\"reasonCode\":\"REFUSED\"}", courierA.token()).statusCode())
                .isEqualTo(404);
        assertThat(deliver(parcelOfB, courierA.token(), "Someone", null).statusCode()).isEqualTo(404);

        assertThat(parcelService.getParcel(parcelOfB.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(get(path, courierB.token()).statusCode()).isEqualTo(200);
    }

    @Test
    void aFailedAttemptWithoutAReasonIsRefused() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel(newCustomer().userId());
        assign(parcel, courier);
        assertThat(post(DELIVERIES + "/" + parcel.id() + "/status", "{\"status\":\"PICKED_UP\"}", courier.token())
                .statusCode()).isEqualTo(200);

        assertThat(post(DELIVERIES + "/" + parcel.id() + "/fail", "{}", courier.token()).statusCode())
                .isEqualTo(400);
        assertThat(post(DELIVERIES + "/" + parcel.id() + "/fail", "{\"reasonCode\":\"\"}", courier.token())
                .statusCode()).isEqualTo(400);

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.PICKED_UP);
        Integer failures = db.queryForObject("SELECT count(*) FROM delivery_failures WHERE parcel_id = ?",
                Integer.class, parcel.id());
        assertThat(failures).isZero();
    }

    @Test
    void theProofPhotoIsVisibleToTheOwningCustomerOnly() throws Exception {
        Courier courier = newCourier();
        Courier otherCourier = newCourier();
        Customer owner = newCustomer();
        Customer otherCustomer = newCustomer();
        ParcelDetails parcel = newParcel(owner.userId());
        assign(parcel, courier);
        for (String step : List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY")) {
            post(DELIVERIES + "/" + parcel.id() + "/status", "{\"status\":\"%s\"}".formatted(step), courier.token());
        }
        assertThat(deliver(parcel, courier.token(), "Kasun Perera", png(40, 30)).statusCode()).isEqualTo(200);
        UUID proofId = db.queryForObject("SELECT proof_id FROM delivery_proofs WHERE parcel_id = ?", UUID.class,
                parcel.id());
        String photo = "/api/v1/proofs/" + proofId + "/photo";

        assertThat(getBytes(photo, owner.token()).statusCode()).isEqualTo(200);
        assertThat(getBytes(photo, otherCustomer.token()).statusCode()).isEqualTo(404);
        assertThat(getBytes(photo, otherCourier.token()).statusCode()).isEqualTo(404);
        assertThat(getBytes(photo, null).statusCode()).isEqualTo(401);
    }

    @Test
    void theProfileCardShowsTheCourierAndChangesTheirOwnAvailability() throws Exception {
        Courier courier = newCourier();
        String profile = "/api/v1/courier/profile";

        JsonNode mine = body(get(profile, courier.token()));
        assertThat(mine.get("courierId").asString()).isEqualTo(courier.courierId().toString());
        assertThat(mine.get("fullName").asString()).isEqualTo("Ramesh Perera");
        assertThat(mine.get("vehicleType").asString()).isEqualTo("Motorbike");
        assertThat(mine.get("status").asString()).isEqualTo("ONLINE");
        assertThat(mine.get("active").asBoolean()).isTrue();

        HttpResponse<String> onBreak = patch(profile + "/status", "{\"status\":\"ON_BREAK\"}", courier.token());
        assertThat(onBreak.statusCode()).isEqualTo(200);
        assertThat(body(onBreak).get("status").asString()).isEqualTo("ON_BREAK");
        assertThat(db.queryForObject("SELECT availability_status FROM couriers WHERE courier_id = ?", String.class,
                courier.courierId())).isEqualTo("ON_BREAK");

        assertThat(patch(profile + "/status", "{\"status\":\"ASLEEP\"}", courier.token()).statusCode())
                .isEqualTo(400);
        assertThat(get(profile, adminToken()).statusCode()).isEqualTo(403);

        // A deactivated courier can't put themselves back online (dispatch's rule).
        db.update("UPDATE couriers SET is_active = FALSE WHERE courier_id = ?", courier.courierId());
        assertThat(patch(profile + "/status", "{\"status\":\"ONLINE\"}", courier.token()).statusCode())
                .isEqualTo(409);
        assertThat(patch(profile + "/status", "{\"status\":\"OFFLINE\"}", courier.token()).statusCode())
                .isEqualTo(200);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private String adminToken() throws Exception {
        HttpResponse<String> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"ADMIN"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD));
        return body(login).get("token").asString();
    }

    /** Adds a courier through the admin Couriers API, sets them Online and signs them in. */
    private Courier newCourier() throws Exception {
        String admin = adminToken();
        String email = "courier-" + UUID.randomUUID() + "@example.com";
        String registration = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HttpResponse<String> created = post("/api/v1/admin/couriers", """
                {"fullName":"Ramesh Perera","email":"%s","phone":"+94771234567","password":"%s",
                 "vehicleType":"Motorbike","vehicleRegistration":"%s"}""".formatted(email, PASSWORD, registration),
                admin);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID courierId = UUID.fromString(body(created).get("id").asString());
        assertThat(patch("/api/v1/admin/couriers/" + courierId + "/availability", "{\"availability\":\"ONLINE\"}",
                admin).statusCode()).isEqualTo(200);
        HttpResponse<String> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"COURIER"}""".formatted(email, PASSWORD));
        return new Courier(courierId, UUID.fromString(body(created).get("userId").asString()),
                body(login).get("token").asString());
    }

    /** A customer who can sign in (real password hash, confirmed email). */
    private Customer newCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@test.swiftdrop.local";
        UUID id = userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567",
                passwordEncoder.encode(PASSWORD), Role.CUSTOMER, AccountStatus.ACTIVE)).id();
        verifyEmail(email);
        HttpResponse<String> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD));
        return new Customer(id, body(login).get("token").asString());
    }

    private ParcelDetails newParcel(UUID customerUserId) {
        NewAddress pickup = new NewAddress("Nimal Silva", "+94771234568", "5 Temple Road", null, "Kandy", "Central",
                null, null);
        NewAddress dropoff = new NewAddress("Kasun Perera", "+94771234567", "12 Galle Road", null, "Colombo",
                "Western", null, null);
        return parcelService.createParcel(new NewParcel(customerUserId, null, null, pickup, dropoff,
                ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, null, BigDecimal.TEN));
    }

    /** Through the admin Assign API (Member 6), as the dispatcher does it. */
    private void assign(ParcelDetails parcel, Courier courier) throws Exception {
        HttpResponse<String> assigned = post("/api/v1/admin/dispatch/assignments", """
                {"parcelId":"%s","courierId":"%s"}""".formatted(parcel.id(), courier.courierId()), adminToken());
        assertThat(assigned.statusCode()).isEqualTo(201);
    }

    /** Waits for dispatch's status listener (asynchronous) to end the parcel's assignment. */
    private Map<String, Object> awaitEndedAssignment(ParcelDetails parcel) throws InterruptedException {
        Instant deadline = Instant.now().plus(LISTENER_WAIT);
        while (true) {
            List<Map<String, Object>> ended = db.queryForList("""
                    SELECT assignment_status, ended_at FROM delivery_assignments
                    WHERE parcel_id = ? AND ended_at IS NOT NULL""", parcel.id());
            if (!ended.isEmpty()) {
                return ended.get(0);
            }
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for dispatch to end the assignment");
            }
            Thread.sleep(200);
        }
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    /** POST /deliver as multipart/form-data, as the courier app sends it. */
    private HttpResponse<String> deliver(ParcelDetails parcel, String token, String recipientName, byte[] photo)
            throws Exception {
        String boundary = "----swiftdrop" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"recipientName\"\r\n\r\n"
                + recipientName + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (photo != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"p.png\""
                    + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(photo);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri(DELIVERIES + "/" + parcel.id() + "/deliver"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
