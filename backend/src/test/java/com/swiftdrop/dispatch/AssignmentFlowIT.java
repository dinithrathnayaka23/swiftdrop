package com.swiftdrop.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assigning parcels end to end: the admin endpoints, the Admin Parcels tabs (parcel module), and the
 * assignment following the parcel's status through RabbitMQ.
 */
class AssignmentFlowIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String COURIERS = "/api/v1/admin/couriers";
    private static final String DISPATCH = "/api/v1/admin/dispatch";
    private static final Duration LISTENER_WAIT = Duration.ofSeconds(10);

    @Autowired private JsonMapper json;
    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;

    // ---- Helpers -----------------------------------------------------------------------------

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private String adminToken() throws Exception {
        return body(post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"ADMIN"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .get("token").asString();
    }

    /** Adds a courier through the API and returns it (id, userId, ...). */
    private JsonNode addCourier(String admin, boolean online) throws Exception {
        String registration = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        HttpResponse<String> created = post(COURIERS, """
                {"fullName":"Ramesh Perera","email":"courier-%s@example.com","phone":"+94771234567",
                 "password":"courier2026","vehicleType":"Motorbike","vehicleRegistration":"%s"}"""
                .formatted(UUID.randomUUID(), registration), admin);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode courier = body(created);
        if (online) {
            HttpResponse<String> changed = patch(COURIERS + "/" + id(courier) + "/availability",
                    "{\"availability\":\"ONLINE\"}", admin);
            assertThat(changed.statusCode()).isEqualTo(200);
        }
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

    private HttpResponse<String> assign(UUID parcelId, JsonNode courier, String admin) throws Exception {
        return post(DISPATCH + "/assignments", """
                {"parcelId":"%s","courierId":"%s"}""".formatted(parcelId, id(courier)), admin);
    }

    /** Parcel ids in an Admin Parcels tab, only the parcels going to {@code city}. */
    private List<String> parcelTab(String tab, String city, String admin) throws Exception {
        HttpResponse<String> response = get("/api/v1/admin/parcels?tab=" + tab + "&area="
                + city.replace(" ", "%20") + "&size=100", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        body(response).get("content").forEach(row -> ids.add(id(row)));
        return ids;
    }

    private JsonNode courierAssignments(JsonNode courier, String open, String admin) throws Exception {
        HttpResponse<String> response = get(COURIERS + "/" + id(courier) + "/assignments"
                + (open == null ? "" : "?open=" + open), admin);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).get("content");
    }

    private JsonNode workload(JsonNode courier, String admin) throws Exception {
        return body(get(COURIERS + "/" + id(courier), admin)).get("workload");
    }

    /** Polls until the condition holds (the listener works asynchronously) or fails after {@link #LISTENER_WAIT}. */
    private JsonNode await(String what, CheckedSupplier<JsonNode> read, Predicate<JsonNode> condition)
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
        return last;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static boolean onlyRowHasStatus(JsonNode rows, String status) {
        return rows.size() == 1 && rows.get(0).get("status").asString().equals(status);
    }

    // ---- Tests -------------------------------------------------------------------------------

    @Test
    void assignReassignAndUnassignFollowTheRules() throws Exception {
        String admin = adminToken();
        String city = "Town " + UUID.randomUUID();
        JsonNode first = addCourier(admin, true);
        JsonNode second = addCourier(admin, true);
        JsonNode offline = addCourier(admin, false);
        ParcelDetails parcel = parcelTo(city);
        ParcelDetails waiting = parcelTo(city);

        // Assign: 201 + Location, the parcel stays CREATED and moves to the Assigned tab.
        HttpResponse<String> assigned = assign(parcel.id(), first, admin);
        assertThat(assigned.statusCode()).isEqualTo(201);
        JsonNode assignment = body(assigned);
        assertThat(assigned.headers().firstValue("Location")).hasValue(DISPATCH + "/assignments/" + id(assignment));
        assertThat(assignment.get("status").asString()).isEqualTo("ASSIGNED");
        assertThat(get(DISPATCH + "/assignments/" + id(assignment), admin).statusCode()).isEqualTo(200);
        assertThat(parcelService.getParcel(parcel.id()).currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(parcelTab("ASSIGNED", city, admin)).containsExactly(parcel.id().toString());
        assertThat(parcelTab("UNASSIGNED", city, admin)).containsExactly(waiting.id().toString());

        // Twice: 409. Offline courier: 409. Unknown parcel: 404.
        assertThat(assign(parcel.id(), second, admin).statusCode()).isEqualTo(409);
        HttpResponse<String> toOffline = assign(waiting.id(), offline, admin);
        assertThat(toOffline.statusCode()).isEqualTo(409);
        assertThat(body(toOffline).get("message").asString()).contains("offline");
        assertThat(assign(UUID.randomUUID(), first, admin).statusCode()).isEqualTo(404);

        // Reassign: the first courier's assignment ends with the reason, the second courier has the parcel.
        assertThat(post(DISPATCH + "/parcels/" + parcel.id() + "/reassign", """
                {"courierId":"%s"}""".formatted(id(second)), admin).statusCode()).isEqualTo(400); // reason required
        HttpResponse<String> reassigned = post(DISPATCH + "/parcels/" + parcel.id() + "/reassign", """
                {"courierId":"%s","reason":"Vehicle broke down"}""".formatted(id(second)), admin);
        assertThat(reassigned.statusCode()).isEqualTo(200);
        assertThat(body(reassigned).get("courierId").asString()).isEqualTo(id(second));
        JsonNode firstHistory = courierAssignments(first, "false", admin);
        assertThat(onlyRowHasStatus(firstHistory, "REASSIGNED")).isTrue();
        assertThat(firstHistory.get(0).get("reason").asString()).isEqualTo("Vehicle broke down");
        assertThat(courierAssignments(first, "true", admin)).isEmpty();
        assertThat(onlyRowHasStatus(courierAssignments(second, "true", admin), "ASSIGNED")).isTrue();

        // Unassign: the parcel is waiting again; doing it twice is a 409.
        HttpResponse<String> unassigned = post(DISPATCH + "/parcels/" + parcel.id() + "/unassign", "{}", admin);
        assertThat(unassigned.statusCode()).isEqualTo(200);
        assertThat(body(unassigned).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(post(DISPATCH + "/parcels/" + parcel.id() + "/unassign", "{}", admin).statusCode()).isEqualTo(409);
        assertThat(parcelTab("UNASSIGNED", city, admin))
                .containsExactlyInAnyOrder(parcel.id().toString(), waiting.id().toString());

        // Bulk: one parcel assigned, one unknown parcel reported, still 200.
        UUID unknown = UUID.randomUUID();
        HttpResponse<String> bulk = post(DISPATCH + "/assignments/bulk", """
                {"courierId":"%s","parcelIds":["%s","%s"]}""".formatted(id(first), waiting.id(), unknown), admin);
        assertThat(bulk.statusCode()).isEqualTo(200);
        assertThat(body(bulk).get("assigned")).hasSize(1);
        assertThat(body(bulk).get("failed").get(0).get("parcelId").asString()).isEqualTo(unknown.toString());

        // Only the dispatcher admin may assign.
        assertThat(assign(parcel.id(), first, null).statusCode()).isEqualTo(401);
    }

    @Test
    void theAssignmentFollowsTheParcelUntilItIsDeliveredOrFailed() throws Exception {
        String admin = adminToken();
        String city = "Town " + UUID.randomUUID();
        JsonNode courier = addCourier(admin, true);
        UUID courierUserId = UUID.fromString(courier.get("userId").asString());
        ParcelDetails delivered = parcelTo(city);
        ParcelDetails failed = parcelTo(city);
        assertThat(assign(delivered.id(), courier, admin).statusCode()).isEqualTo(201);
        assertThat(assign(failed.id(), courier, admin).statusCode()).isEqualTo(201);
        assertThat(workload(courier, admin).get("assigned").asInt()).isEqualTo(2);

        // Picked up: the assignment starts (parcel leaves the Assigned tab: it is no longer CREATED).
        statusService.changeStatus(delivered.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, courierUserId, null));
        await("the delivery to start", () -> workload(courier, admin),
                workload -> workload.get("inProgress").asInt() == 1);
        assertThat(parcelTab("ASSIGNED", city, admin)).containsExactly(failed.id().toString());

        // Delivered: the assignment is completed and counts for today.
        statusService.changeStatus(delivered.id(), StatusChange.to(ParcelStatusCode.IN_TRANSIT, courierUserId, null));
        statusService.changeStatus(delivered.id(),
                StatusChange.to(ParcelStatusCode.OUT_FOR_DELIVERY, courierUserId, null));
        statusService.changeStatus(delivered.id(), StatusChange.delivered(courierUserId, "Kamala Silva", null, null));
        JsonNode done = await("the delivery to complete", () -> workload(courier, admin),
                workload -> workload.get("completedToday").asInt() == 1);
        assertThat(done.get("inProgress").asInt()).isZero();
        assertThat(done.get("assigned").asInt()).isEqualTo(1);

        JsonNode history = courierAssignments(courier, "false", admin);
        assertThat(onlyRowHasStatus(history, "COMPLETED")).isTrue();
        assertThat(history.get(0).get("trackingNumber").asString()).isEqualTo(delivered.trackingNumber());
        assertThat(history.get(0).get("parcelStatus").asString()).isEqualTo("DELIVERED");
        assertThat(history.get(0).get("destinationCity").asString()).isEqualTo(city);
        assertThat(history.get(0).get("endedAt").isNull()).isFalse();

        // Failed: the assignment ends as FAILED and does not count as completed.
        statusService.changeStatus(failed.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, courierUserId, null));
        statusService.changeStatus(failed.id(), StatusChange.failed(courierUserId, "Recipient not available", null));
        await("the failed delivery to end", () -> courierAssignments(courier, "true", admin), JsonNode::isEmpty);
        JsonNode after = workload(courier, admin);
        assertThat(after.get("completedToday").asInt()).isEqualTo(1);
        assertThat(after.get("open").asInt()).isZero();
        assertThat(courierAssignments(courier, null, admin)).extracting(row -> row.get("status").asString())
                .containsExactlyInAnyOrder("COMPLETED", "FAILED");

        // A delivered parcel can't be reassigned.
        JsonNode other = addCourier(admin, true);
        assertThat(post(DISPATCH + "/parcels/" + delivered.id() + "/reassign", """
                {"courierId":"%s","reason":"Late"}""".formatted(id(other)), admin).statusCode()).isEqualTo(409);
    }
}
