package com.swiftdrop.dispatch;

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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A failed delivery end to end: the courier's failed attempt, the queue, retry (and assigning again),
 * a second failed attempt, closing it, and the Operations Overview numbers.
 */
class FailedFlowIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String COURIERS = "/api/v1/admin/couriers";
    private static final String DISPATCH = "/api/v1/admin/dispatch";
    private static final String COURIER_PASSWORD = "courier2026";
    private static final Duration LISTENER_WAIT = Duration.ofSeconds(10);

    @Autowired private JsonMapper json;
    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;
    @Autowired private AmqpAdmin amqpAdmin;

    // ---- Helpers -----------------------------------------------------------------------------

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private String token(String email, String password, String portal) throws Exception {
        return body(post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal)))
                .get("token").asString();
    }

    /** Adds an online courier named Ramesh Perera through the API. */
    private JsonNode addOnlineCourier(String admin, String email) throws Exception {
        HttpResponse<String> created = post(COURIERS, """
                {"fullName":"Ramesh Perera","email":"%s","phone":"+94771234567","password":"%s",
                 "vehicleType":"Motorbike","vehicleRegistration":"IT-%s"}"""
                .formatted(email, COURIER_PASSWORD, UUID.randomUUID().toString().substring(0, 8).toUpperCase()),
                admin);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode courier = body(created);
        assertThat(patch(COURIERS + "/" + id(courier) + "/availability", "{\"availability\":\"ONLINE\"}", admin)
                .statusCode()).isEqualTo(200);
        return courier;
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

    private static String id(JsonNode node) {
        return node.get("id").asString();
    }

    private void assign(ParcelDetails parcel, JsonNode courier, String admin) throws Exception {
        HttpResponse<String> assigned = post(DISPATCH + "/assignments", """
                {"parcelId":"%s","courierId":"%s"}""".formatted(parcel.id(), id(courier)), admin);
        assertThat(assigned.statusCode()).as(assigned.body()).isEqualTo(201);
    }

    /** The courier picks the parcel up and reports a failed attempt; waits until dispatch ended the assignment. */
    private void failAttempt(ParcelDetails parcel, JsonNode courier, String reason, String admin) throws Exception {
        UUID courierUserId = UUID.fromString(courier.get("userId").asString());
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, courierUserId, null));
        statusService.changeStatus(parcel.id(), StatusChange.failedAttempt(courierUserId, reason, null));
        await("the failed attempt to end the assignment",
                () -> body(get(COURIERS + "/" + id(courier) + "/assignments?open=true", admin)).get("content"),
                JsonNode::isEmpty);
    }

    /** The queue row of this parcel (it was booked last, so it is on the first page), or null. */
    private JsonNode queueRow(String scope, ParcelDetails parcel, String admin) throws Exception {
        HttpResponse<String> response = get(DISPATCH + "/failed-deliveries?scope=" + scope + "&size=50", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        for (JsonNode row : body(response).get("content")) {
            if (row.get("parcelId").asString().equals(parcel.id().toString())) {
                return row;
            }
        }
        return null;
    }

    private boolean inUnassignedTab(ParcelDetails parcel, String city, String admin) throws Exception {
        JsonNode rows = body(get("/api/v1/admin/parcels?tab=UNASSIGNED&area=" + city.replace(" ", "%20"), admin))
                .get("content");
        for (JsonNode row : rows) {
            if (id(row).equals(parcel.id().toString())) {
                return true;
            }
        }
        return false;
    }

    private void await(String what, CheckedSupplier<JsonNode> read, Predicate<JsonNode> condition)
            throws Exception {
        Instant deadline = Instant.now().plus(LISTENER_WAIT);
        JsonNode last = read.get();
        while (!condition.test(last)) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for " + what + "; last value: " + last);
            }
            Thread.sleep(200);
            last = read.get();
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    /** Leave the queues nothing consumes in tests empty for later tests (see AssignmentFlowIT). */
    @AfterEach
    void leaveTheUnconsumedQueuesEmpty() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE, false);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE, false);
    }

    // ---- Tests -------------------------------------------------------------------------------

    @Test
    void aFailedDeliveryIsRetriedFailsAgainAndIsClosed() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        String city = "Town " + UUID.randomUUID();
        JsonNode courier = addOnlineCourier(admin, "courier-" + UUID.randomUUID() + "@example.com");
        ParcelDetails parcel = parcelTo(city);
        assign(parcel, courier, admin);

        // First failed attempt: the queue shows the reason, the courier and one attempt.
        failAttempt(parcel, courier, "Recipient not available", admin);
        JsonNode row = queueRow("ATTENTION", parcel, admin);
        assertThat(row).isNotNull();
        assertThat(row.get("parcelStatus").asString()).isEqualTo("FAILED_ATTEMPT");
        assertThat(row.get("failureReason").asString()).isEqualTo("Recipient not available");
        assertThat(row.get("courierName").asString()).isEqualTo("Ramesh Perera");
        assertThat(row.get("courierId").asString()).isEqualTo(id(courier));
        assertThat(row.get("attemptCount").asInt()).isEqualTo(1);
        assertThat(row.get("destinationCity").asString()).isEqualTo(city);

        // Retry: back to CREATED and in the Unassigned tab; doing it again is a 409.
        HttpResponse<String> retried = post(DISPATCH + "/parcels/" + parcel.id() + "/retry",
                "{\"note\":\"Second attempt tomorrow\"}", admin);
        assertThat(retried.statusCode()).isEqualTo(200);
        assertThat(body(retried).get("status").asString()).isEqualTo("CREATED");
        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(inUnassignedTab(parcel, city, admin)).isTrue();
        assertThat(queueRow("ATTENTION", parcel, admin)).isNull();
        assertThat(post(DISPATCH + "/parcels/" + parcel.id() + "/retry", "{}", admin).statusCode()).isEqualTo(409);
        assertThat(post(DISPATCH + "/parcels/" + parcel.id() + "/close", "{\"reason\":\"x\"}", admin).statusCode())
                .isEqualTo(409);

        // Assign again, fail again: two attempts, the latest reason.
        assign(parcel, courier, admin);
        failAttempt(parcel, courier, "Shop closed", admin);
        row = queueRow("ATTENTION", parcel, admin);
        assertThat(row.get("attemptCount").asInt()).isEqualTo(2);
        assertThat(row.get("failureReason").asString()).isEqualTo("Shop closed");

        // Close: a reason is required; then the parcel is FAILED, out of ATTENTION and in CLOSED.
        assertThat(post(DISPATCH + "/parcels/" + parcel.id() + "/close", "{\"reason\":\" \"}", admin).statusCode())
                .isEqualTo(400);
        HttpResponse<String> closed = post(DISPATCH + "/parcels/" + parcel.id() + "/close",
                "{\"reason\":\"Address does not exist\"}", admin);
        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(body(closed).get("status").asString()).isEqualTo("FAILED");
        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.FAILED);
        assertThat(queueRow("ATTENTION", parcel, admin)).isNull();
        JsonNode closedRow = queueRow("CLOSED", parcel, admin);
        assertThat(closedRow.get("failureReason").asString()).isEqualTo("Address does not exist");
        assertThat(closedRow.get("attemptCount").asInt()).isEqualTo(2);
        assertThat(post(DISPATCH + "/parcels/" + parcel.id() + "/retry", "{}", admin).statusCode()).isEqualTo(409);

        // The overview agrees with the parcel module and with itself.
        JsonNode overview = body(get(DISPATCH + "/overview", admin));
        JsonNode kpis = overview.get("kpis");
        JsonNode counts = body(get("/api/v1/admin/parcels/counts", admin));
        assertThat(kpis.get("unassignedParcels").asLong()).isEqualTo(counts.get("UNASSIGNED").asLong());
        assertThat(kpis.get("needsDecision").asLong())
                .isEqualTo(parcelService.countByStatus().get(ParcelStatusCode.FAILED_ATTEMPT));
        assertThat(kpis.get("failedToday").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(overview.get("todayStatus").get("failed").asLong()).isEqualTo(kpis.get("failedToday").asLong());
        assertThat(overview.get("todayStatus").get("completed").asLong())
                .isEqualTo(kpis.get("completedToday").asLong());
        assertThat(kpis.get("couriersOnline").asLong()).isPositive();
        assertThat(overview.get("courierWorkload").size()).isBetween(1, 5);
        assertThat(overview.get("defaultAction").asString()).isNotBlank();
        assertThat(body(get(DISPATCH + "/failed-deliveries/default-action", admin)).get("action").asString())
                .isEqualTo(overview.get("defaultAction").asString());
    }

    @Test
    void theNewEndpointsAreForTheDispatcherAdminOnly() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        String email = "courier-" + UUID.randomUUID() + "@example.com";
        addOnlineCourier(admin, email);
        String courier = token(email, COURIER_PASSWORD, "COURIER");
        UUID unknown = UUID.randomUUID();

        assertThat(get(DISPATCH + "/failed-deliveries").statusCode()).isEqualTo(401);
        assertThat(get(DISPATCH + "/overview").statusCode()).isEqualTo(401);
        assertThat(get(DISPATCH + "/failed-deliveries", courier).statusCode()).isEqualTo(403);
        assertThat(get(DISPATCH + "/active-deliveries", courier).statusCode()).isEqualTo(403);
        assertThat(post(DISPATCH + "/parcels/" + unknown + "/retry", "{}", courier).statusCode()).isEqualTo(403);

        assertThat(get(DISPATCH + "/failed-deliveries?scope=ALL", admin).statusCode()).isEqualTo(400);
        assertThat(get(DISPATCH + "/failed-deliveries?size=51", admin).statusCode()).isEqualTo(400);
        assertThat(get(DISPATCH + "/active-deliveries", admin).statusCode()).isEqualTo(200);
        assertThat(post(DISPATCH + "/parcels/" + unknown + "/retry", "{}", admin).statusCode()).isEqualTo(404);
        assertThat(post(DISPATCH + "/parcels/" + unknown + "/close", "{\"reason\":\"x\"}", admin).statusCode())
                .isEqualTo(404);
    }
}
