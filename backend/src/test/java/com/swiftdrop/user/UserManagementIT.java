package com.swiftdrop.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.notification.service.EmailService;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The admin's User Management API end to end: listing, changing roles and statuses, and their effect on sign-in. */
class UserManagementIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String USERS = "/api/v1/admin/users";
    private static final String PASSWORD = "parcel2026";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    private record SignedIn(String id, String email, String token, String sessionCookie) {
    }

    private String adminToken() throws Exception {
        return json.readTree(login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN").body()).get("token").asString();
    }

    private HttpResponse<String> login(String email, String password, String portal) throws Exception {
        return post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal));
    }

    /** A new, verified, signed-in customer called "{@code name}". */
    private SignedIn customer(String name) throws Exception {
        String email = "users-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = post("/api/v1/auth/register/customer", """
                {"fullName":"%s","email":"%s","phone":"+94712345678","password":"%s"}"""
                .formatted(name, email, PASSWORD));
        assertThat(registered.statusCode()).isEqualTo(201);
        verifyEmail(email);
        HttpResponse<String> response = login(email, PASSWORD, "PUBLIC");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        return new SignedIn(body.get("user").get("id").asString(), email, body.get("token").asString(),
                sessionCookie(response));
    }

    private HttpResponse<String> changeRole(String admin, String userId, String role) throws Exception {
        return patch(USERS + "/" + userId + "/role", "{\"role\":\"" + role + "\"}", admin);
    }

    private HttpResponse<String> changeStatus(String admin, String userId, String status) throws Exception {
        return patch(USERS + "/" + userId + "/status", "{\"status\":\"" + status + "\"}", admin);
    }

    @Test
    void theAdminSearchesFiltersAndPagesTheUsers() throws Exception {
        String admin = adminToken();
        // A surname no other test uses; names may only contain letters.
        String marker = "Zx" + UUID.randomUUID().toString().replaceAll("[^a-f]", "").replace('a', 'q');
        SignedIn older = customer("Kasun " + marker);
        SignedIn newer = customer("Amali " + marker);

        JsonNode found = json.readTree(get(USERS + "?search=" + marker.toLowerCase(), admin).body());
        assertThat(found.get("page").get("totalElements").asInt()).isEqualTo(2);
        JsonNode first = found.get("content").get(0);
        assertThat(first.get("id").asString()).isEqualTo(newer.id()); // newest first
        assertThat(first.get("role").asString()).isEqualTo("CUSTOMER");
        assertThat(first.get("emailVerified").asBoolean()).isTrue();
        assertThat(first.has("passwordHash")).isFalse();

        JsonNode paged = json.readTree(get(USERS + "?search=" + marker + "&size=1&page=1", admin).body());
        assertThat(paged.get("content").get(0).get("id").asString()).isEqualTo(older.id());
        assertThat(paged.get("page").get("totalPages").asInt()).isEqualTo(2);

        assertThat(json.readTree(get(USERS + "?search=" + marker + "&role=COURIER", admin).body())
                .get("page").get("totalElements").asInt()).isZero();
        assertThat(json.readTree(get(USERS + "?search=" + newer.email(), admin).body())
                .get("page").get("totalElements").asInt()).isEqualTo(1);
        // LIKE wildcards are searched for literally, not as "match anything".
        assertThat(json.readTree(get(USERS + "?search=%25" + marker, admin).body())
                .get("page").get("totalElements").asInt()).isZero();

        JsonNode one = json.readTree(get(USERS + "/" + older.id(), admin).body());
        assertThat(one.get("email").asString()).isEqualTo(older.email());
    }

    @Test
    void aCustomerMadeACourierSignsInAgainOnTheCourierApp() throws Exception {
        String admin = adminToken();
        SignedIn user = customer("Ruwan Silva");

        HttpResponse<String> changed = changeRole(admin, user.id(), "COURIER");

        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(json.readTree(changed.body()).get("role").asString()).isEqualTo("COURIER");
        // The old session is over; the courier app is now the way in.
        assertThat(postWithSession("/api/v1/auth/refresh", user.sessionCookie()).statusCode()).isEqualTo(401);
        assertThat(login(user.email(), PASSWORD, "PUBLIC").statusCode()).isEqualTo(403);
        HttpResponse<String> courier = login(user.email(), PASSWORD, "COURIER");
        assertThat(courier.statusCode()).isEqualTo(200);
        assertThat(json.readTree(courier.body()).get("user").get("role").asString()).isEqualTo("COURIER");
    }

    @Test
    void theAdminAndMerchantRolesCannotBeHandedOut() throws Exception {
        String admin = adminToken();
        String adminId = json.readTree(get("/api/v1/users/me", admin).body()).get("id").asString();
        SignedIn user = customer("Dilani Fernando");

        HttpResponse<String> makeAdmin = changeRole(admin, user.id(), "DISPATCHER_ADMIN");
        assertThat(makeAdmin.statusCode()).isEqualTo(409);
        assertThat(json.readTree(makeAdmin.body()).get("code").asString()).isEqualTo("CONFLICT");
        assertThat(changeRole(admin, user.id(), "MERCHANT").statusCode()).isEqualTo(409);
        assertThat(changeRole(admin, adminId, "CUSTOMER").statusCode()).isEqualTo(409);
        assertThat(changeStatus(admin, adminId, "DEACTIVATED").statusCode()).isEqualTo(409);
        // Nothing changed.
        assertThat(json.readTree(get(USERS + "/" + user.id(), admin).body()).get("role").asString()).isEqualTo("CUSTOMER");
        assertThat(login(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN").statusCode()).isEqualTo(200);
    }

    @Test
    void aDeactivatedUserIsSignedOutUntilReactivated() throws Exception {
        String admin = adminToken();
        SignedIn user = customer("Sahan Jayasuriya");

        HttpResponse<String> deactivated = changeStatus(admin, user.id(), "DEACTIVATED");

        assertThat(deactivated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(deactivated.body()).get("accountStatus").asString()).isEqualTo("DEACTIVATED");
        assertThat(postWithSession("/api/v1/auth/refresh", user.sessionCookie()).statusCode()).isEqualTo(401);
        assertThat(login(user.email(), PASSWORD, "PUBLIC").statusCode()).isEqualTo(403);
        assertThat(json.readTree(get(USERS + "?status=DEACTIVATED&search=" + user.email(), admin).body())
                .get("page").get("totalElements").asInt()).isEqualTo(1);

        assertThat(changeStatus(admin, user.id(), "ACTIVE").statusCode()).isEqualTo(200);
        assertThat(login(user.email(), PASSWORD, "PUBLIC").statusCode()).isEqualTo(200);
    }

    @Test
    void badRequestsAreExplained() throws Exception {
        String admin = adminToken();
        SignedIn user = customer("Tharindu Wijesinghe");

        assertThat(changeRole(admin, user.id(), "PILOT").statusCode()).isEqualTo(400);
        assertThat(patch(USERS + "/" + user.id() + "/role", "{}", admin).statusCode()).isEqualTo(400);
        assertThat(changeStatus(admin, user.id(), "PENDING_APPROVAL").statusCode()).isEqualTo(400);
        assertThat(get(USERS + "?size=500", admin).statusCode()).isEqualTo(400);
        assertThat(get(USERS + "/" + UUID.randomUUID(), admin).statusCode()).isEqualTo(404);
        assertThat(changeRole(admin, UUID.randomUUID().toString(), "COURIER").statusCode()).isEqualTo(404);
    }

    @Test
    void onlyTheAdminManagesUsers() throws Exception {
        SignedIn user = customer("Malsha Gunawardena");

        assertThat(get(USERS, user.token()).statusCode()).isEqualTo(403);
        assertThat(changeRole(user.token(), user.id(), "COURIER").statusCode()).isEqualTo(403);
        assertThat(get(USERS).statusCode()).isEqualTo(401);
    }
}
