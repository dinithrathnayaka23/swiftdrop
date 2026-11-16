package com.swiftdrop.usermanagement;

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

/** The User Management dashboard numbers end to end. */
class UserStatsIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String STATS = "/api/v1/admin/users/stats";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    private String token(String email, String password, String portal) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("token").asString();
    }

    private String newCustomer() throws Exception {
        String email = "stats-" + UUID.randomUUID() + "@example.com";
        assertThat(post("/api/v1/auth/register/customer", """
                {"fullName":"Stats Viewer","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email)).statusCode()).isEqualTo(201);
        verifyEmail(email);
        return token(email, "parcel2026", "PUBLIC");
    }

    private JsonNode stats(String admin) throws Exception {
        HttpResponse<String> response = get(STATS, admin);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    @Test
    void aNewCustomerIsCounted() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        JsonNode before = stats(admin);

        newCustomer();
        JsonNode after = stats(admin);

        assertThat(after.get("total").asLong()).isEqualTo(before.get("total").asLong() + 1);
        assertThat(after.get("byRole").get("CUSTOMER").asLong())
                .isEqualTo(before.get("byRole").get("CUSTOMER").asLong() + 1);
        assertThat(after.get("byStatus").get("ACTIVE").asLong())
                .isEqualTo(before.get("byStatus").get("ACTIVE").asLong() + 1);
        assertThat(after.get("byRole").get("DISPATCHER_ADMIN").asLong()).isEqualTo(1);
    }

    @Test
    void roleAndStatusCountsAddUpToTheTotal() throws Exception {
        JsonNode stats = stats(token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN"));

        long byRole = 0;
        for (JsonNode count : stats.get("byRole").values()) {
            byRole += count.asLong();
        }
        long byStatus = 0;
        for (JsonNode count : stats.get("byStatus").values()) {
            byStatus += count.asLong();
        }
        assertThat(byRole).isEqualTo(stats.get("total").asLong());
        assertThat(byStatus).isEqualTo(stats.get("total").asLong());
    }

    @Test
    void onlyTheAdminSeesTheStats() throws Exception {
        assertThat(get(STATS, newCustomer()).statusCode()).isEqualTo(403);
        assertThat(get(STATS).statusCode()).isEqualTo(401);
    }
}
