package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.storage.LocalFileProofStorage;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
 * The courier app end to end: own deliveries only, in assignment order, status updates, failures and
 * proof of delivery.
 */
class CourierDeliveriesIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String PASSWORD = "courier2026";
    private static final String DELIVERIES = "/api/v1/courier/deliveries";
    /** How long to wait for dispatch's status listener (asynchronous). */
    private static final Duration LISTENER_WAIT = Duration.ofSeconds(10);

    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate db;
    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService parcelStatusService;
    @Autowired private UserService userService;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LocalFileProofStorage proofStorage;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    /** A signed-in courier: their profile id, account id and access token. */
    private record Courier(UUID courierId, UUID userId, String token) {
    }

    /** A signed-in customer: their account id and access token. */
    private record Customer(UUID userId, String token) {
    }

    /**
     * Status changes publish events to the shared notification and webhook queues, which nothing
     * reads yet. Empty them so this test's events can't reach another test (e.g. InfrastructureIT).
     */
    @BeforeEach
    @AfterEach
    void emptyQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    @Test
    void todayListsOnlyTheCouriersOwnOpenDeliveriesOldestFirst() throws Exception {
        Courier courier = newCourier();
        Courier other = newCourier();
        ParcelDetails newer = newParcel();
        ParcelDetails older = newParcel();
        ParcelDetails othersParcel = newParcel();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assign(newer, courier, now.minus(1, ChronoUnit.HOURS));
        assign(older, courier, now.minus(3, ChronoUnit.HOURS));
        assign(othersParcel, other, now.minus(2, ChronoUnit.HOURS));
        parcelStatusService.changeStatus(newer.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courier.userId(), null));

        HttpResponse<String> response = get(DELIVERIES + "/today", courier.token());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        List<String> ids = new ArrayList<>();
        body.get("deliveries").forEach(row -> ids.add(row.get("parcelId").asString()));
        assertThat(ids).containsExactly(older.id().toString(), newer.id().toString());

        JsonNode first = body.get("deliveries").get(0);
        assertThat(first.get("currentStatus").asString()).isEqualTo("CREATED");
        assertThat(first.get("allowedActions").get(0).asString()).isEqualTo("PICKED_UP");
        assertThat(first.get("dropoff").get("contactName").asString()).isEqualTo("Kasun Perera");
        assertThat(first.get("pickup").get("city").asString()).isEqualTo("Kandy");
        assertThat(body.get("deliveries").get(1).get("pickup").isNull()).isTrue();

        assertThat(body.get("counts").get("total").asInt()).isEqualTo(2);
        assertThat(body.get("counts").get("assigned").asInt()).isEqualTo(1);
        assertThat(body.get("counts").get("inProgress").asInt()).isEqualTo(1);
    }

    @Test
    void aCourierCannotSeeAnotherCouriersParcel() throws Exception {
        Courier owner = newCourier();
        Courier other = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, owner, Instant.now());

        HttpResponse<String> mine = get(DELIVERIES + "/" + parcel.id(), owner.token());
        assertThat(mine.statusCode()).isEqualTo(200);
        JsonNode detail = json.readTree(mine.body());
        assertThat(detail.get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
        assertThat(detail.get("timeline").get(0).get("status").asString()).isEqualTo("CREATED");

        // 404, not 403: the other courier can't tell the parcel exists.
        assertThat(get(DELIVERIES + "/" + parcel.id(), other.token()).statusCode()).isEqualTo(404);
        assertThat(get(DELIVERIES + "/by-tracking-number/" + parcel.trackingNumber(), other.token()).statusCode())
                .isEqualTo(404);
        assertThat(get(DELIVERIES + "/" + UUID.randomUUID(), owner.token()).statusCode()).isEqualTo(404);
    }

    @Test
    void theScannerLookUpIgnoresCaseAndSpaces() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        String tn = parcel.trackingNumber().toLowerCase();
        String spaced = tn.substring(0, 4) + "%20" + tn.substring(4);

        HttpResponse<String> response = get(DELIVERIES + "/by-tracking-number/" + spaced, courier.token());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("parcelId").asString()).isEqualTo(parcel.id().toString());
        assertThat(get(DELIVERIES + "/by-tracking-number/SD000000000", courier.token()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void updateStatusRecordsTheStepWithTheNoteAndReturnsTheNewState() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());

        HttpResponse<String> response = post(DELIVERIES + "/" + parcel.id() + "/status", """
                {"status":"PICKED_UP","note":"Collected from the front desk"}""", courier.token());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("currentStatus").asString()).isEqualTo("PICKED_UP");
        assertThat(body.get("allowedActions").get(0).asString()).isEqualTo("IN_TRANSIT");
        JsonNode last = body.get("timeline").get(body.get("timeline").size() - 1);
        assertThat(last.get("status").asString()).isEqualTo("PICKED_UP");
        assertThat(last.get("note").asString()).isEqualTo("Collected from the front desk");

        UUID changedBy = db.queryForObject("""
                SELECT changed_by_user_id FROM parcel_status_history
                WHERE parcel_id = ? AND status_code = 'PICKED_UP'""", UUID.class, parcel.id());
        assertThat(changedBy).isEqualTo(courier.userId());
    }

    @Test
    void theSameStepTwiceIsAConflict() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        String body = """
                {"status":"PICKED_UP"}""";

        assertThat(post(DELIVERIES + "/" + parcel.id() + "/status", body, courier.token()).statusCode())
                .isEqualTo(200);
        assertThat(post(DELIVERIES + "/" + parcel.id() + "/status", body, courier.token()).statusCode())
                .isEqualTo(409);
        // Skipping a step is refused by the parcel module's rules too.
        assertThat(post(DELIVERIES + "/" + parcel.id() + "/status", """
                {"status":"OUT_FOR_DELIVERY"}""", courier.token()).statusCode()).isEqualTo(409);
        assertThat(parcelStatusService.getHistory(parcel.id())).hasSize(2);
    }

    @Test
    void updateStatusOnlyForTheCouriersOwnParcelAndOnlyForPlainSteps() throws Exception {
        Courier owner = newCourier();
        Courier other = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, owner, Instant.now());
        String path = DELIVERIES + "/" + parcel.id() + "/status";

        assertThat(post(path, """
                {"status":"PICKED_UP"}""", other.token()).statusCode()).isEqualTo(404);
        assertThat(post(path, """
                {"status":"DELIVERED"}""", owner.token()).statusCode()).isEqualTo(400);
        assertThat(post(path, """
                {"status":"FAILED"}""", owner.token()).statusCode()).isEqualTo(400);
        assertThat(post(path, "{}", owner.token()).statusCode()).isEqualTo(400);
        assertThat(post(path, """
                {"status":"LOST"}""", owner.token()).statusCode()).isEqualTo(400);
        assertThat(post(path, """
                {"status":"PICKED_UP","note":"%s"}""".formatted("x".repeat(501)), owner.token()).statusCode())
                .isEqualTo(400);

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
    }

    @Test
    void theFailureReasonsComeInDisplayOrder() throws Exception {
        Courier courier = newCourier();

        HttpResponse<String> response = get("/api/v1/courier/failure-reasons", courier.token());

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> codes = new ArrayList<>();
        JsonNode body = json.readTree(response.body());
        body.forEach(reason -> codes.add(reason.get("code").asString()));
        assertThat(codes).containsExactly("RECIPIENT_UNAVAILABLE", "WRONG_ADDRESS", "REFUSED", "ACCESS_ISSUE",
                "DAMAGED", "OTHER");
        assertThat(body.get(5).get("label").asString()).isEqualTo("Other");
        assertThat(body.get(5).get("noteRequired").asBoolean()).isTrue();
        assertThat(body.get(0).get("noteRequired").asBoolean()).isFalse();
    }

    @Test
    void reportingAFailedAttemptWritesTheHistoryAndTheFailureRow() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        parcelStatusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courier.userId(), null));

        HttpResponse<String> response = post(DELIVERIES + "/" + parcel.id() + "/fail", """
                {"reasonCode":"OTHER","note":"Gate locked, no answer"}""", courier.token());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("currentStatus").asString()).isEqualTo("FAILED_ATTEMPT");
        // Retrying or closing the attempt is the dispatcher's.
        assertThat(body.get("allowedActions").isEmpty()).isTrue();
        JsonNode last = body.get("timeline").get(body.get("timeline").size() - 1);
        assertThat(last.get("failureReason").asString()).isEqualTo("Other: Gate locked, no answer");

        var row = db.queryForMap("""
                SELECT f.courier_user_id, f.reason_code, f.note, h.status_code
                FROM delivery_failures f JOIN parcel_status_history h USING (status_history_id)
                WHERE f.parcel_id = ?""", parcel.id());
        assertThat(row.get("courier_user_id")).isEqualTo(courier.userId());
        assertThat(row.get("reason_code")).isEqualTo("OTHER");
        assertThat(row.get("note")).isEqualTo("Gate locked, no answer");
        assertThat(row.get("status_code")).isEqualTo("FAILED_ATTEMPT");

        // Reporting it again is refused (409 now, 404 once dispatch has ended the assignment).
        assertThat(post(DELIVERIES + "/" + parcel.id() + "/fail", """
                {"reasonCode":"REFUSED"}""", courier.token()).statusCode()).isIn(404, 409);
        assertThat(failureCount(parcel)).isEqualTo(1);

        // Dispatch ends the assignment, so the delivery leaves the courier's list.
        awaitNoOpenAssignment(parcel);
        assertThat(get(DELIVERIES + "/" + parcel.id(), courier.token()).statusCode()).isEqualTo(404);
    }

    @Test
    void aRetriedParcelCanFailAgainWithASecondFailureRow() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now().minus(1, ChronoUnit.HOURS));
        parcelStatusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courier.userId(), null));
        String path = DELIVERIES + "/" + parcel.id() + "/fail";
        assertThat(post(path, """
                {"reasonCode":"RECIPIENT_UNAVAILABLE"}""", courier.token()).statusCode()).isEqualTo(200);
        awaitNoOpenAssignment(parcel);

        // The dispatcher retries the parcel and gives it back to the same courier.
        parcelStatusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.CREATED, null, "Retry"));
        assign(parcel, courier, Instant.now());
        parcelStatusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courier.userId(), null));

        assertThat(post(path, """
                {"reasonCode":"WRONG_ADDRESS"}""", courier.token()).statusCode()).isEqualTo(200);

        assertThat(failureCount(parcel)).isEqualTo(2);
        assertThat(db.queryForList(
                "SELECT reason_code FROM delivery_failures WHERE parcel_id = ? ORDER BY recorded_at",
                String.class, parcel.id())).containsExactly("RECIPIENT_UNAVAILABLE", "WRONG_ADDRESS");
    }

    @Test
    void aFailureNeedsAKnownReasonAndANoteWhenTheReasonAsksForOne() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        parcelStatusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courier.userId(), null));
        String path = DELIVERIES + "/" + parcel.id() + "/fail";

        assertThat(post(path, "{}", courier.token()).statusCode()).isEqualTo(400);
        assertThat(post(path, """
                {"reasonCode":"LOST_IN_SPACE"}""", courier.token()).statusCode()).isEqualTo(400);
        assertThat(post(path, """
                {"reasonCode":"DAMAGED","note":"   "}""", courier.token()).statusCode()).isEqualTo(400);

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.PICKED_UP);
        assertThat(failureCount(parcel)).isZero();
    }

    @Test
    void aFailureOnlyForTheCouriersOwnParcelAndOnlyAfterPickup() throws Exception {
        Courier owner = newCourier();
        Courier other = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, owner, Instant.now());
        String path = DELIVERIES + "/" + parcel.id() + "/fail";
        String body = """
                {"reasonCode":"RECIPIENT_UNAVAILABLE"}""";

        assertThat(post(path, body, other.token()).statusCode()).isEqualTo(404);
        // Still CREATED: a courier can only report a failed attempt after pickup.
        assertThat(post(path, body, owner.token()).statusCode()).isEqualTo(409);

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(failureCount(parcel)).isZero();
    }

    // ---- Proof of delivery (Phase 4) -------------------------------------------------------------

    @Test
    void deliveringWithAPhotoWritesTheHistoryAndTheProofAndStoresACleanJpeg() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        moveToOutForDelivery(parcel, courier);

        HttpResponse<String> response = deliver(parcel, courier.token(), "Kasun Perera", "Left with the guard",
                png(2400, 1200));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("currentStatus").asString()).isEqualTo("DELIVERED");
        assertThat(body.get("allowedActions").isEmpty()).isTrue();

        var proof = db.queryForMap("""
                SELECT p.proof_id, p.courier_user_id, p.recipient_name, p.photo_storage_key, p.photo_content_type,
                       p.photo_size_bytes, h.status_code, h.recipient_name AS history_name, h.proof_file_url, h.note
                FROM delivery_proofs p JOIN parcel_status_history h USING (status_history_id)
                WHERE p.parcel_id = ?""", parcel.id());
        assertThat(proof.get("courier_user_id")).isEqualTo(courier.userId());
        assertThat(proof.get("recipient_name")).isEqualTo("Kasun Perera");
        assertThat(proof.get("status_code")).isEqualTo("DELIVERED");
        assertThat(proof.get("history_name")).isEqualTo("Kasun Perera");
        assertThat(proof.get("note")).isEqualTo("Left with the guard");
        assertThat(proof.get("proof_file_url")).isEqualTo("/proofs/" + proof.get("proof_id") + "/photo");
        assertThat(proof.get("photo_content_type")).isEqualTo("image/jpeg");

        // The stored file is the re-encoded JPEG, scaled down to 1600 px.
        byte[] stored = proofStorage.read((String) proof.get("photo_storage_key"));
        assertThat(stored.length).isEqualTo(((Number) proof.get("photo_size_bytes")).intValue());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(image.getWidth()).isEqualTo(1600);
        assertThat(image.getHeight()).isEqualTo(800);
    }

    @Test
    void deliveringWithoutAPhotoNeedsOnlyTheRecipientsName() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        moveToOutForDelivery(parcel, courier);

        assertThat(deliver(parcel, courier.token(), "", null, null).statusCode()).isEqualTo(400);
        assertThat(deliver(parcel, courier.token(), "Kasun Perera", null, null).statusCode()).isEqualTo(200);

        var proof = db.queryForMap("SELECT photo_storage_key FROM delivery_proofs WHERE parcel_id = ?", parcel.id());
        assertThat(proof.get("photo_storage_key")).isNull();
        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.DELIVERED);
    }

    @Test
    void deliveringIsRefusedForAnotherCourierBeforeOutForDeliveryOrWithAFileThatIsNotAPhoto() throws Exception {
        Courier owner = newCourier();
        Courier other = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, owner, Instant.now());
        parcelStatusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, owner.userId(), null));
        long filesBefore = storedPhotoFiles();

        assertThat(deliver(parcel, other.token(), "Kasun Perera", null, png(10, 10)).statusCode()).isEqualTo(404);
        // Still PICKED_UP: the photo is stored first, then the status change fails and the file is removed.
        assertThat(deliver(parcel, owner.token(), "Kasun Perera", null, png(10, 10)).statusCode()).isEqualTo(409);
        assertThat(deliver(parcel, owner.token(), "Kasun Perera", null,
                "<html>not a photo</html>".getBytes()).statusCode()).isEqualTo(400);

        assertThat(proofCount(parcel)).isZero();
        assertThat(storedPhotoFiles()).isEqualTo(filesBefore);
        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.PICKED_UP);
    }

    @Test
    void thePhotoIsVisibleToItsCourierTheOwningCustomerAndTheAdminOnly() throws Exception {
        Courier courier = newCourier();
        Courier otherCourier = newCourier();
        Customer owner = newCustomer();
        Customer stranger = newCustomer();
        ParcelDetails parcel = newParcel(owner.userId());
        assign(parcel, courier, Instant.now());
        moveToOutForDelivery(parcel, courier);
        assertThat(deliver(parcel, courier.token(), "Kasun Perera", null, png(30, 20)).statusCode()).isEqualTo(200);
        UUID proofId = db.queryForObject("SELECT proof_id FROM delivery_proofs WHERE parcel_id = ?", UUID.class,
                parcel.id());
        String photo = "/api/v1/proofs/" + proofId + "/photo";

        for (String token : List.of(courier.token(), owner.token(), adminToken())) {
            HttpResponse<byte[]> response = getBytes(photo, token);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("image/jpeg");
            assertThat(response.headers().firstValue("Cache-Control")).hasValue("private, no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
            assertThat(ImageIO.read(new ByteArrayInputStream(response.body())).getWidth()).isEqualTo(30);
        }
        for (String token : List.of(otherCourier.token(), stranger.token())) {
            assertThat(getBytes(photo, token).statusCode()).isEqualTo(404);
        }
        assertThat(getBytes(photo, null).statusCode()).isEqualTo(401);
        assertThat(getBytes("/api/v1/proofs/" + UUID.randomUUID() + "/photo", adminToken()).statusCode())
                .isEqualTo(404);
    }

    @Test
    void onlyCouriersMayUseTheCourierApp() throws Exception {
        assertThat(get(DELIVERIES + "/today").statusCode()).isEqualTo(401);
        assertThat(get(DELIVERIES + "/today", adminToken()).statusCode()).isEqualTo(403);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    /**
     * Waits until dispatch's status listener has ended the parcel's assignment (it runs
     * asynchronously after the status event), as {@code AssignmentFlowIT} does.
     */
    private void awaitNoOpenAssignment(ParcelDetails parcel) throws InterruptedException {
        Instant deadline = Instant.now().plus(LISTENER_WAIT);
        while (openAssignments(parcel) > 0) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for dispatch to end the assignment");
            }
            Thread.sleep(200);
        }
    }

    private int openAssignments(ParcelDetails parcel) {
        Integer count = db.queryForObject(
                "SELECT count(*) FROM delivery_assignments WHERE parcel_id = ? AND ended_at IS NULL",
                Integer.class, parcel.id());
        return count == null ? 0 : count;
    }

    private int failureCount(ParcelDetails parcel) {
        Integer count = db.queryForObject("SELECT count(*) FROM delivery_failures WHERE parcel_id = ?",
                Integer.class, parcel.id());
        return count == null ? 0 : count;
    }

    private String adminToken() throws Exception {
        return token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
    }

    private String token(String email, String password, String portal) throws Exception {
        HttpResponse<String> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal));
        assertThat(login.statusCode()).isEqualTo(200);
        return json.readTree(login.body()).get("token").asString();
    }

    /** Adds a courier through the admin Couriers API (Member 6) and signs them in. */
    private Courier newCourier() throws Exception {
        String email = "courier-" + UUID.randomUUID() + "@example.com";
        String registration = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HttpResponse<String> created = post("/api/v1/admin/couriers", """
                {"fullName":"Ramesh Perera","email":"%s","phone":"+94771234567","password":"%s",
                 "vehicleType":"Motorbike","vehicleRegistration":"%s"}""".formatted(email, PASSWORD, registration),
                adminToken());
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(created.body());
        return new Courier(UUID.fromString(body.get("id").asString()), UUID.fromString(body.get("userId").asString()),
                token(email, PASSWORD, "COURIER"));
    }

    private ParcelDetails newParcel() {
        UUID customer = userService.createUser(NewUser.withPassword("Ayesha Khan",
                "customer-" + UUID.randomUUID() + "@test.swiftdrop.local", "+94771234567", "x", Role.CUSTOMER,
                AccountStatus.ACTIVE)).id();
        return newParcel(customer);
    }

    /** A customer who can sign in (real password hash, confirmed email). */
    private Customer newCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@test.swiftdrop.local";
        UUID id = userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567",
                passwordEncoder.encode(PASSWORD), Role.CUSTOMER, AccountStatus.ACTIVE)).id();
        verifyEmail(email);
        HttpResponse<String> login = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        return new Customer(id, json.readTree(login.body()).get("token").asString());
    }

    private void moveToOutForDelivery(ParcelDetails parcel, Courier courier) {
        for (ParcelStatusCode step : List.of(ParcelStatusCode.PICKED_UP, ParcelStatusCode.IN_TRANSIT,
                ParcelStatusCode.OUT_FOR_DELIVERY)) {
            parcelStatusService.changeStatus(parcel.id(), StatusChange.to(step, courier.userId(), null));
        }
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    /** POST /deliver as multipart/form-data, as the courier app sends it. */
    private HttpResponse<String> deliver(ParcelDetails parcel, String token, String recipientName, String note,
                                         byte[] photo) throws Exception {
        String boundary = "----swiftdrop" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeField(body, boundary, "recipientName", recipientName);
        if (note != null) {
            writeField(body, boundary, "note", note);
        }
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

    private static void writeField(ByteArrayOutputStream body, String boundary, String name, String value)
            throws Exception {
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value
                + "\r\n").getBytes(StandardCharsets.UTF_8));
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

    private int proofCount(ParcelDetails parcel) {
        Integer count = db.queryForObject("SELECT count(*) FROM delivery_proofs WHERE parcel_id = ?", Integer.class,
                parcel.id());
        return count == null ? 0 : count;
    }

    /** Photo files under the storage folder (the .gitignore excluded). */
    private long storedPhotoFiles() throws Exception {
        Path root = proofStorage.root();
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jpg")).count();
        }
    }

    /** Removes the photos this test class stored, so no test files are left in the storage folder. */
    @AfterAll
    static void removeStoredPhotos(@Autowired JdbcTemplate db, @Autowired LocalFileProofStorage storage) {
        db.queryForList("SELECT photo_storage_key FROM delivery_proofs WHERE photo_storage_key IS NOT NULL",
                String.class).forEach(storage::delete);
    }

    private ParcelDetails newParcel(UUID customer) {
        NewAddress pickup = new NewAddress("Nimal Silva", "+94771234568", "5 Temple Road", null, "Kandy", "Central",
                null, null);
        NewAddress dropoff = new NewAddress("Kasun Perera", "+94771234567", "12 Galle Road", null, "Colombo",
                "Western", null, null);
        return parcelService.createParcel(new NewParcel(customer, null, null, pickup, dropoff, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, null, BigDecimal.TEN));
    }

    /**
     * Writes the open assignment directly (as {@code CourierManagementIT} does), so a test can set
     * {@code assigned_at} and needn't put the courier online first as the admin Assign API requires.
     */
    private void assign(ParcelDetails parcel, Courier courier, Instant assignedAt) {
        UUID adminId = db.queryForObject("SELECT user_id FROM users WHERE email = ?", UUID.class, ADMIN_EMAIL);
        db.update("""
                INSERT INTO delivery_assignments (parcel_id, courier_id, assigned_by_user_id, assigned_at)
                VALUES (?, ?, ?, ?)""", parcel.id(), courier.courierId(), adminId, java.sql.Timestamp.from(assignedAt));
    }
}
