package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared set-up for the merchant integration tests: merchants signed in to the portal, their
 * orders, and calls to the order API with an API key or an OAuth token.
 */
abstract class MerchantIntegrationTestBase extends IntegrationTestBase {

    protected static final String UNKNOWN_ORDER = "/api/v1/orders/SDNOSUCHORDER1/status";

    @Autowired protected UserService userService;
    @Autowired protected MerchantService merchantService;
    @Autowired protected ParcelService parcelService;
    @Autowired protected JwtService jwtService;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JsonMapper json;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    /** A merchant's contact person, signed in to the portal with {@code token}. */
    protected record SignedInMerchant(UUID userId, UUID merchantId, String token) {
    }

    /** A new merchant, approved by the admin, whose contact person is signed in. */
    protected SignedInMerchant approvedMerchant(String businessName) {
        SignedInMerchant merchant = pendingMerchant(businessName);
        jdbc.update("UPDATE merchants SET approval_status = 'APPROVED', reviewed_at = now() WHERE merchant_id = ?",
                merchant.merchantId());
        return merchant;
    }

    /** A new merchant still waiting for the admin's approval. */
    protected SignedInMerchant pendingMerchant(String businessName) {
        UUID userId = merchantUser();
        UUID merchantId = merchantService.registerPending(new NewMerchant(userId, businessName,
                "PV" + UUID.randomUUID().toString().substring(0, 8), "shop-" + UUID.randomUUID()
                + "@test.swiftdrop.local", "+94771234567", "1 Main Street, Colombo")).merchantId();
        return new SignedInMerchant(userId, merchantId, tokenFor(userId, Role.MERCHANT));
    }

    /** A signed-in user with the MERCHANT role, without a merchant. */
    protected UUID merchantUser() {
        return userService.createUser(NewUser.withPassword("Sunil Fernando", "merchant-" + UUID.randomUUID()
                + "@test.swiftdrop.local", "+94771234567", "x", Role.MERCHANT, AccountStatus.ACTIVE)).id();
    }

    protected String tokenFor(UUID userId, Role role) {
        return jwtService.generateToken(userId.toString(), Set.of(role));
    }

    protected String adminToken() {
        UUID adminId = userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id();
        return tokenFor(adminId, Role.DISPATCHER_ADMIN);
    }

    protected void setMerchantStatus(UUID merchantId, String status) {
        jdbc.update("UPDATE merchants SET approval_status = ? WHERE merchant_id = ?", status, merchantId);
    }

    /** An order of the merchant, created straight through the parcel module. */
    protected ParcelDetails order(UUID merchantId, String reference, String recipient, String city) {
        NewAddress pickup = new NewAddress("BrightMart Warehouse", "+94771234567", "1 Depot Road", null,
                "Colombo", "Western", null, null);
        NewAddress dropoff = new NewAddress(recipient, "+94772222222", "25 Temple Street", null, city,
                "Central", null, null);
        return parcelService.createParcel(new NewParcel(null, merchantId, reference, pickup, dropoff,
                ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, null, new BigDecimal("450.00")));
    }

    protected JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    /** The response's body, after checking its status. */
    protected JsonNode ok(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return body(response);
    }

    /** Checks an error response's status and code. */
    protected void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(body(response).get("code").asString()).isEqualTo(code);
    }

    /** Creates an API key in the portal and returns the response (the only one with the raw key). */
    protected JsonNode createApiKey(SignedInMerchant merchant) throws Exception {
        return ok(post("/api/v1/merchant/api-keys", "", merchant.token()), 201);
    }

    /** A call to the order API with the {@code X-API-Key} header ({@code jsonBody} null for a GET). */
    protected HttpResponse<String> withApiKey(String path, String jsonBody, String apiKey) throws Exception {
        return send(request(path, jsonBody).header("X-API-Key", apiKey));
    }

    /** A call to the order API with an OAuth access token ({@code jsonBody} null for a GET). */
    protected HttpResponse<String> withAccessToken(String path, String jsonBody, String accessToken)
            throws Exception {
        return send(request(path, jsonBody).header("Authorization", "Bearer " + accessToken));
    }

    /** {@code POST /api/v1/oauth/token} with a form body. */
    protected HttpResponse<String> oauthToken(Map<String, String> form) throws Exception {
        String encoded = form.entrySet().stream()
                .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(uri("/api/v1/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded)));
    }

    /** Calls {@code check} until its result passes {@code done}, for up to 15 seconds. */
    protected static <T> T eventually(Callable<T> check, Predicate<T> done) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        T last = check.call();
        while (!done.test(last)) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met in time; last value: " + last);
            }
            Thread.sleep(100);
            last = check.call();
        }
        return last;
    }

    private HttpRequest.Builder request(String path, String jsonBody) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path));
        if (jsonBody == null) return request.GET();
        return request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
