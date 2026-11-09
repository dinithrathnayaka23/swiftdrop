package com.swiftdrop.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The admin Couriers API end to end, and the dispatch module's answers to the parcel module and the courier app. */
class CourierManagementIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String COURIERS = "/api/v1/admin/couriers";
    private static final String PASSWORD = "courier2026";

    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate db;
    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private DispatchApi dispatchApi;

    private HttpResponse<String> login(String email, String password, String portal) throws Exception {
        return post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal));
    }

    private String adminToken() throws Exception {
        return json.readTree(login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN").body()).get("token").asString();
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private static String uniqueEmail() {
        return "courier-" + UUID.randomUUID() + "@example.com";
    }

    private static String uniqueRegistration() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String courierJson(String email, String registration) {
        return """
                {"fullName":"Ramesh Perera","email":"%s","phone":"+94771234567","password":"%s",
                 "vehicleType":"Motorbike","vehicleRegistration":"%s"}""".formatted(email, PASSWORD, registration);
    }

    /** Adds a courier as the admin and returns the response body. */
    private JsonNode addCourier(String admin, String email, String registration) throws Exception {
        HttpResponse<String> created = post(COURIERS, courierJson(email, registration), admin);
        assertThat(created.statusCode()).isEqualTo(201);
        return body(created);
    }

    @Test
    void anAddedCourierStartsOfflineAndCanSignInToTheCourierApp() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();

        HttpResponse<String> created = post(COURIERS, courierJson(email, uniqueRegistration()), admin);

        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode courier = body(created);
        assertThat(created.headers().firstValue("Location").orElseThrow())
                .endsWith(COURIERS + "/" + courier.get("id").asString());
        assertThat(courier.get("availability").asString()).isEqualTo("OFFLINE");
        assertThat(courier.get("active").asBoolean()).isTrue();
        assertThat(courier.get("workload").get("todaysTotal").asInt()).isZero();

        assertThat(login(email, PASSWORD, "COURIER").statusCode()).isEqualTo(200);
        assertThat(login(email, PASSWORD, "PUBLIC").statusCode()).isNotEqualTo(200); // staff use their own page
    }

    @Test
    void refusesAnEmailOrVehicleThatIsAlreadyUsed() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String registration = uniqueRegistration();
        addCourier(admin, email, registration);

        HttpResponse<String> sameEmail = post(COURIERS, courierJson(email, uniqueRegistration()), admin);
        HttpResponse<String> sameVehicle = post(COURIERS, courierJson(uniqueEmail(), registration.toLowerCase()), admin);

        assertThat(sameEmail.statusCode()).isEqualTo(409);
        assertThat(sameVehicle.statusCode()).isEqualTo(409);
        assertThat(body(sameVehicle).get("fieldErrors").get(0).get("field").asString()).isEqualTo("vehicleRegistration");
    }

    @Test
    void refusesInvalidInputAndUnknownServiceAreas() throws Exception {
        String admin = adminToken();

        assertThat(post(COURIERS, courierJson("not-an-email", uniqueRegistration()), admin).statusCode()).isEqualTo(400);

        String withUnknownZone = courierJson(uniqueEmail(), uniqueRegistration()).replace("}",
                ",\"serviceAreaIds\":[\"" + UUID.randomUUID() + "\"]}");
        assertThat(post(COURIERS, withUnknownZone, admin).statusCode()).isEqualTo(409);
    }

    @Test
    void theListFiltersAndTheSummaryAddUp() throws Exception {
        String admin = adminToken();
        String registration = uniqueRegistration();
        JsonNode first = addCourier(admin, uniqueEmail(), registration);
        addCourier(admin, uniqueEmail(), uniqueRegistration());

        assertThat(patch(COURIERS + "/" + first.get("id").asString() + "/availability",
                "{\"availability\":\"ONLINE\"}", admin).statusCode()).isEqualTo(200);

        JsonNode found = body(get(COURIERS + "?search=" + registration.toLowerCase(), admin));
        assertThat(found.get("page").get("totalElements").asInt()).isEqualTo(1);
        assertThat(found.get("content").get(0).get("availability").asString()).isEqualTo("ONLINE");
        assertThat(body(get(COURIERS + "?search=" + registration + "&availability=OFFLINE", admin))
                .get("page").get("totalElements").asInt()).isZero();
        assertThat(body(get(COURIERS + "?search=" + registration + "&active=true&vehicleType=motorbike", admin))
                .get("page").get("totalElements").asInt()).isEqualTo(1);

        JsonNode summary = body(get(COURIERS + "/summary", admin));
        assertThat(summary.get("total").asLong()).isEqualTo(summary.get("online").asLong()
                + summary.get("onBreak").asLong() + summary.get("offline").asLong());
        assertThat(summary.get("online").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theVehicleAndAvailabilityCanBeChanged() throws Exception {
        String admin = adminToken();
        JsonNode courier = addCourier(admin, uniqueEmail(), uniqueRegistration());
        String url = COURIERS + "/" + courier.get("id").asString();
        String newRegistration = uniqueRegistration();

        JsonNode updated = body(patch(url, "{\"vehicleType\":\"Van\",\"vehicleRegistration\":\"" + newRegistration
                + "\"}", admin));
        assertThat(updated.get("vehicleType").asString()).isEqualTo("Van");
        assertThat(updated.get("vehicleRegistration").asString()).isEqualTo(newRegistration);

        assertThat(body(patch(url + "/availability", "{\"availability\":\"ON_BREAK\"}", admin))
                .get("availability").asString()).isEqualTo("ON_BREAK");
        assertThat(patch(url + "/availability", "{\"availability\":\"SLEEPING\"}", admin).statusCode()).isEqualTo(400);
        assertThat(patch(url, "{}", admin).statusCode()).isEqualTo(400);
        assertThat(get(COURIERS + "/" + UUID.randomUUID(), admin).statusCode()).isEqualTo(404);
    }

    @Test
    void onlyTheAdminMayUseTheCouriersApi() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        addCourier(admin, email, uniqueRegistration());
        String courierToken = body(login(email, PASSWORD, "COURIER")).get("token").asString();

        assertThat(get(COURIERS).statusCode()).isEqualTo(401);
        assertThat(get(COURIERS, courierToken).statusCode()).isEqualTo(403);
        assertThat(post(COURIERS, courierJson(uniqueEmail(), uniqueRegistration()), courierToken).statusCode())
                .isEqualTo(403);
    }

    @Test
    void openAssignmentsShowAsAssignedAndCountInTheWorkload() throws Exception {
        String admin = adminToken();
        JsonNode courier = addCourier(admin, uniqueEmail(), uniqueRegistration());
        UUID courierId = UUID.fromString(courier.get("id").asString());
        UUID courierUserId = UUID.fromString(courier.get("userId").asString());
        String city = "Town-" + UUID.randomUUID(); // no spaces: it goes into a URL
        ParcelDetails assigned = parcelTo(city);
        ParcelDetails waiting = parcelTo(city);
        UUID adminId = db.queryForObject("SELECT user_id FROM users WHERE email = ?", UUID.class, ADMIN_EMAIL);
        db.update("INSERT INTO delivery_assignments (parcel_id, courier_id, assigned_by_user_id) VALUES (?, ?, ?)",
                assigned.id(), courierId, adminId);

        // The parcel module's Assigned / Unassigned tabs now use the dispatch module's answer.
        assertThat(parcelIds(get("/api/v1/admin/parcels?tab=ASSIGNED&area=" + city, admin))).containsExactly(assigned.id());
        assertThat(parcelIds(get("/api/v1/admin/parcels?tab=UNASSIGNED&area=" + city, admin))).containsExactly(waiting.id());

        // The courier app's ownership check.
        assertThat(dispatchApi.isAssignedTo(assigned.id(), courierUserId)).isTrue();
        assertThat(dispatchApi.isAssignedTo(waiting.id(), courierUserId)).isFalse();
        assertThat(dispatchApi.openParcelIds(courierUserId)).containsExactly(assigned.id());
        assertThat(dispatchApi.findCourierId(courierUserId)).contains(courierId);

        JsonNode workload = body(get(COURIERS + "/" + courierId, admin)).get("workload");
        assertThat(workload.get("assigned").asInt()).isEqualTo(1);
        assertThat(workload.get("open").asInt()).isEqualTo(1);

        // Completing the assignment ends it: the parcel is no longer "assigned" and counts as done today.
        db.update("UPDATE delivery_assignments SET assignment_status = 'COMPLETED', ended_at = now() WHERE parcel_id = ?",
                assigned.id());
        JsonNode done = body(get(COURIERS + "/" + courierId, admin)).get("workload");
        assertThat(done.get("assigned").asInt()).isZero();
        assertThat(done.get("completedToday").asInt()).isEqualTo(1);
        assertThat(done.get("todaysTotal").asInt()).isEqualTo(1);
        assertThat(dispatchApi.openParcelIds(courierUserId)).isEmpty();
    }

    private ParcelDetails parcelTo(String city) {
        UUID customer = userService.createUser(NewUser.withPassword("Ayesha Khan",
                "customer-" + UUID.randomUUID() + "@test.swiftdrop.local", "+94771234567", "x", Role.CUSTOMER,
                AccountStatus.ACTIVE)).id();
        NewAddress address = new NewAddress("Kasun Perera", "+94771234567", "12 Galle Road", null, city, "Western",
                null, null);
        return parcelService.createParcel(new NewParcel(customer, null, null, address, address, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, null, BigDecimal.TEN));
    }

    private java.util.List<UUID> parcelIds(HttpResponse<String> response) {
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        body(response).get("content").forEach(row -> ids.add(UUID.fromString(row.get("id").asString())));
        return ids;
    }
}
