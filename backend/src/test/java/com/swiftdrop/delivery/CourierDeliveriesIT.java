package com.swiftdrop.delivery;

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
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The courier app end to end: own deliveries only, in assignment order, status updates and failures. */
class CourierDeliveriesIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String PASSWORD = "courier2026";
    private static final String DELIVERIES = "/api/v1/courier/deliveries";

    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate db;
    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService parcelStatusService;
    @Autowired private UserService userService;
    @Autowired private AmqpAdmin amqpAdmin;

    /** A signed-in courier: their profile id, account id and access token. */
    private record Courier(UUID courierId, UUID userId, String token) {
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
    void reportingAFailureWritesTheHistoryAndTheFailureRow() throws Exception {
        Courier courier = newCourier();
        ParcelDetails parcel = newParcel();
        assign(parcel, courier, Instant.now());
        parcelStatusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, courier.userId(), null));

        HttpResponse<String> response = post(DELIVERIES + "/" + parcel.id() + "/fail", """
                {"reasonCode":"OTHER","note":"Gate locked, no answer"}""", courier.token());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("currentStatus").asString()).isEqualTo("FAILED");
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
        assertThat(row.get("status_code")).isEqualTo("FAILED");

        // FAILED is an end state: a second report is refused and nothing more is written.
        assertThat(post(DELIVERIES + "/" + parcel.id() + "/fail", """
                {"reasonCode":"REFUSED"}""", courier.token()).statusCode()).isIn(404, 409);
        assertThat(failureCount(parcel)).isEqualTo(1);
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
        // Still CREATED: the parcel module doesn't allow CREATED -> FAILED.
        assertThat(post(path, body, owner.token()).statusCode()).isEqualTo(409);

        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(failureCount(parcel)).isZero();
    }

    @Test
    void onlyCouriersMayUseTheCourierApp() throws Exception {
        assertThat(get(DELIVERIES + "/today").statusCode()).isEqualTo(401);
        assertThat(get(DELIVERIES + "/today", adminToken()).statusCode()).isEqualTo(403);
    }

    // ---- Helpers -------------------------------------------------------------------------------

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
