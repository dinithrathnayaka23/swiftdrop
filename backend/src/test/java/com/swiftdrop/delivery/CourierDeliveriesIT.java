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

/** The courier app's read endpoints end to end: own deliveries only, in assignment order. */
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
    void onlyCouriersMayUseTheCourierApp() throws Exception {
        assertThat(get(DELIVERIES + "/today").statusCode()).isEqualTo(401);
        assertThat(get(DELIVERIES + "/today", adminToken()).statusCode()).isEqualTo(403);
    }

    // ---- Helpers -------------------------------------------------------------------------------

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
     * Assigning parcels isn't built yet (dispatch, Member 6), so the test writes the open assignment
     * the way {@code CourierManagementIT} does.
     */
    private void assign(ParcelDetails parcel, Courier courier, Instant assignedAt) {
        UUID adminId = db.queryForObject("SELECT user_id FROM users WHERE email = ?", UUID.class, ADMIN_EMAIL);
        db.update("""
                INSERT INTO delivery_assignments (parcel_id, courier_id, assigned_by_user_id, assigned_at)
                VALUES (?, ?, ?, ?)""", parcel.id(), courier.courierId(), adminId, java.sql.Timestamp.from(assignedAt));
    }
}
