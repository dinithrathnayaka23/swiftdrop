package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An admin suspending a signed-in merchant's account (User Management) blocks the portal and the
 * order API at once, with the code the portal shows its "account not active" page for.
 */
class MerchantSuspensionIT extends IntegrationTestBase {

    @Autowired private UserService userService;
    @Autowired private MerchantService merchantService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private UUID userId;
    private UUID merchantId;
    private String merchantToken;
    private String adminToken;

    @BeforeEach
    void signInAsApprovedMerchant() {
        String email = "merchant-" + UUID.randomUUID() + "@test.swiftdrop.local";
        userId = userService.createUser(NewUser.withPassword("Sunil Fernando", email, "+94771234567", "x",
                Role.MERCHANT, AccountStatus.ACTIVE)).id();
        merchantId = merchantService.registerPending(new NewMerchant(userId, "BrightMart",
                "PV" + UUID.randomUUID().toString().substring(0, 8), email, "+94771234567",
                "1 Main Street, Colombo")).merchantId();
        jdbc.update("UPDATE merchants SET approval_status = 'APPROVED' WHERE merchant_id = ?", merchantId);
        merchantToken = jwtService.generateToken(userId.toString(), Set.of(Role.MERCHANT));
        UUID adminId = userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id();
        adminToken = jwtService.generateToken(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));
    }

    private HttpResponse<String> setAccountStatus(String status) throws Exception {
        return patch("/api/v1/admin/users/" + userId + "/status", "{\"status\":\"" + status + "\"}", adminToken);
    }

    private String createApiKey() throws Exception {
        HttpResponse<String> created = post("/api/v1/merchant/api-keys", "", merchantToken);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return json.readTree(created.body()).get("apiKey").asString();
    }

    /** An order API call with the key; an unknown tracking number answers 404 while the key works. */
    private HttpResponse<String> orderApiCall(String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/v1/orders/SDNOSUCHORDER1/status"))
                .header("X-API-Key", apiKey)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String merchantStatus() {
        return jdbc.queryForObject("SELECT approval_status FROM merchants WHERE merchant_id = ?", String.class,
                merchantId);
    }

    private void assertAccountDisabled(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        JsonNode error = json.readTree(response.body());
        assertThat(error.get("code").asString()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(error.get("message").asString())
                .isEqualTo("This merchant account is not active. Please contact support.");
    }

    @Test
    void suspendingTheAccountBlocksThePortalAndTheOrderApiAtOnceAndReactivatingRestoresThem() throws Exception {
        String apiKey = createApiKey();
        assertThat(get("/api/v1/merchant/profile", merchantToken).statusCode()).isEqualTo(200);
        assertThat(orderApiCall(apiKey).statusCode()).isEqualTo(404);

        assertThat(setAccountStatus("SUSPENDED").statusCode()).isEqualTo(200);

        assertThat(merchantStatus()).isEqualTo("SUSPENDED");
        // The access token issued before the suspension is still unexpired, but the portal refuses it.
        assertAccountDisabled(get("/api/v1/merchant/profile", merchantToken));
        assertAccountDisabled(get("/api/v1/merchant/orders", merchantToken));
        assertAccountDisabled(orderApiCall(apiKey));

        assertThat(setAccountStatus("ACTIVE").statusCode()).isEqualTo(200);

        assertThat(merchantStatus()).isEqualTo("APPROVED");
        assertThat(get("/api/v1/merchant/profile", merchantToken).statusCode()).isEqualTo(200);
        assertThat(orderApiCall(apiKey).statusCode()).isEqualTo(404);
    }

    @Test
    void deactivatingTheAccountSuspendsTheMerchant() throws Exception {
        assertThat(setAccountStatus("DEACTIVATED").statusCode()).isEqualTo(200);

        assertThat(merchantStatus()).isEqualTo("SUSPENDED");
        assertAccountDisabled(get("/api/v1/merchant/profile", merchantToken));
    }
}
