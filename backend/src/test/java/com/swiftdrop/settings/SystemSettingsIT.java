package com.swiftdrop.settings;

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
import tools.jackson.databind.node.ObjectNode;

/** System settings end to end: migration row, admin read/save, public view and maintenance mode. */
class SystemSettingsIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String SETTINGS = "/api/v1/admin/settings";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    private String adminToken() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"ADMIN"}""".formatted(ADMIN_EMAIL, ADMIN_PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("token").asString();
    }

    private String customerToken() throws Exception {
        String email = "settings-" + UUID.randomUUID() + "@example.com";
        post("/api/v1/auth/register/customer", """
                {"fullName":"Nimal Perera","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        verifyEmail(email);
        HttpResponse<String> response = post("/api/v1/auth/login", """
                {"email":"%s","password":"parcel2026"}""".formatted(email));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("token").asString();
    }

    /** Saves the current settings with the given changes, based on the latest version. */
    private HttpResponse<String> save(String token, java.util.function.Consumer<ObjectNode> changes) throws Exception {
        ObjectNode form = (ObjectNode) json.readTree(get(SETTINGS, token).body());
        form.remove("updatedAt");
        changes.accept(form);
        return put(SETTINGS, json.writeValueAsString(form), token);
    }

    @Test
    void theAdminReadsAndSavesTheSettings() throws Exception {
        String admin = adminToken();

        JsonNode loaded = json.readTree(get(SETTINGS, admin).body());
        assertThat(loaded.get("timeZone").asString()).isNotBlank();
        long version = loaded.get("version").asLong();

        HttpResponse<String> saved = save(admin, form -> form
                .put("companyName", "SwiftDrop Lanka")
                .put("maxParcelWeightKg", 30.5)
                .put("dateFormat", "YYYY_MM_DD"));
        assertThat(saved.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(saved.body());
        assertThat(body.get("companyName").asString()).isEqualTo("SwiftDrop Lanka");
        assertThat(body.get("version").asLong()).isEqualTo(version + 1);

        // Visible to everyone straight away, without signing in.
        JsonNode publicSettings = json.readTree(get("/api/v1/public/settings").body());
        assertThat(publicSettings.get("companyName").asString()).isEqualTo("SwiftDrop Lanka");
        assertThat(publicSettings.get("dateFormat").asString()).isEqualTo("YYYY_MM_DD");
        assertThat(publicSettings.has("notifyFailedDeliveries")).isFalse();
    }

    @Test
    void aStaleFormIsRefusedWithConflict() throws Exception {
        String admin = adminToken();
        ObjectNode stale = (ObjectNode) json.readTree(get(SETTINGS, admin).body());
        stale.remove("updatedAt");
        assertThat(save(admin, form -> form.put("companyName", "First save")).statusCode()).isEqualTo(200);

        HttpResponse<String> second = put(SETTINGS, json.writeValueAsString(stale.put("companyName", "Second")), admin);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(json.readTree(get(SETTINGS, admin).body()).get("companyName").asString()).isEqualTo("First save");
    }

    @Test
    void invalidValuesAreRejectedPerField() throws Exception {
        String admin = adminToken();

        HttpResponse<String> response = save(admin, form -> form.put("contactEmail", "not-an-email")
                .put("maxParcelWeightKg", -1));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("contactEmail").contains("maxParcelWeightKg");
        assertThat(save(admin, form -> form.put("timeZone", "Mars/Olympus")).body()).contains("timeZone");
        // Accepted by @Email alone, but not a deliverable address.
        HttpResponse<String> noDomainEnding = save(admin, form -> form.put("contactEmail", "support@swiftdrop"));
        assertThat(noDomainEnding.statusCode()).isEqualTo(400);
        assertThat(noDomainEnding.body()).contains("contactEmail");
    }

    @Test
    void onlyTheAdminCanSeeOrChangeTheSettings() throws Exception {
        String customer = customerToken();

        assertThat(get(SETTINGS, customer).statusCode()).isEqualTo(403);
        assertThat(get(SETTINGS).statusCode()).isEqualTo(401);
    }

    @Test
    void maintenanceModeKeepsEveryoneButTheAdminOut() throws Exception {
        String customer = customerToken();
        String admin = adminToken();
        assertThat(get("/api/v1/users/me", customer).statusCode()).isEqualTo(200);

        assertThat(save(admin, form -> form.put("maintenanceMode", true)).statusCode()).isEqualTo(200);
        try {
            HttpResponse<String> blocked = get("/api/v1/users/me", customer);
            assertThat(blocked.statusCode()).isEqualTo(503);
            assertThat(json.readTree(blocked.body()).get("code").asString()).isEqualTo("MAINTENANCE_MODE");

            // New customer sign-ins are refused; the public settings say why.
            assertThat(post("/api/v1/auth/login", """
                    {"email":"someone@example.com","password":"parcel2026"}""").statusCode()).isEqualTo(503);
            assertThat(json.readTree(get("/api/v1/public/settings").body()).get("maintenanceMode").asBoolean())
                    .isTrue();

            // The admin keeps working and can sign in again.
            assertThat(get("/api/v1/users/me", admin).statusCode()).isEqualTo(200);
            assertThat(adminToken()).isNotBlank();
        } finally {
            assertThat(save(admin, form -> form.put("maintenanceMode", false)).statusCode()).isEqualTo(200);
        }

        assertThat(get("/api/v1/users/me", customer).statusCode()).isEqualTo(200);
    }
}
