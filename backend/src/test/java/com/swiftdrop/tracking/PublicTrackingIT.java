package com.swiftdrop.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** {@code GET /api/v1/public/tracking/{trackingNumber}} over HTTP, against the real database and Redis. */
@TestPropertySource(properties = {
        "swiftdrop.tracking.lookup-rate-limit.requests=" + PublicTrackingIT.LOOKUP_LIMIT,
        "swiftdrop.tracking.lookup-rate-limit.window=1m"})
class PublicTrackingIT extends IntegrationTestBase {

    static final int LOOKUP_LIMIT = 5;

    private static final String TRACKING = "/api/v1/public/tracking/";
    private static final String SETTINGS = "/api/v1/admin/settings";

    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private JsonMapper json;

    private UUID customerId;

    /** Every test starts with a fresh look-up allowance for this client IP. */
    @BeforeEach
    void resetLookupLimit() {
        redis.delete(redis.keys("swiftdrop:rl:tracking:lookup:*"));
        String email = "tracking-" + UUID.randomUUID() + "@test.swiftdrop.local";
        customerId = userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567", "x",
                Role.CUSTOMER, AccountStatus.ACTIVE)).id();
    }

    /** Status changes publish events nothing consumes yet; keep them from reaching later tests. */
    @AfterEach
    void emptyQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    private ParcelDetails parcel() {
        return parcelService.createParcel(new NewParcel(customerId, null, null,
                new NewAddress("Ayesha Khan", "+94771234567", "12 Galle Road", "Flat 4B", "Colombo", "Western",
                        "00300", null),
                new NewAddress("Hassan Ali", "+94719876543", "45 Peradeniya Road", null, "Kandy", "Central",
                        "20000", null),
                ParcelType.ELECTRONICS, new BigDecimal("2.5"), null, null, null, "Laptop", "Ring the bell twice",
                null, new BigDecimal("750.00")));
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    @Test
    void anyoneCanTrackAParcelWithoutSigningIn() throws Exception {
        ParcelDetails parcel = parcel();
        statusService.changeStatus(parcel.id(),
                StatusChange.to(ParcelStatusCode.PICKED_UP, customerId, "Collected from reception"));

        HttpResponse<String> response = get(TRACKING + parcel.trackingNumber());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
        assertThat(body.get("currentStatus").asString()).isEqualTo("PICKED_UP");
        assertThat(body.get("fromCity").asString()).isEqualTo("Colombo");
        assertThat(body.get("toCity").asString()).isEqualTo("Kandy");
        assertThat(body.get("weightKg").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(body.get("bookedAt").asString()).isNotBlank();
        assertThat(body.get("history")).hasSize(2);
        assertThat(body.get("history").get(0).get("statusCode").asString()).isEqualTo("CREATED");
        assertThat(body.get("history").get(1).get("statusCode").asString()).isEqualTo("PICKED_UP");
        assertThat(body.get("lastUpdatedAt").asString())
                .isEqualTo(body.get("history").get(1).get("changedAt").asString());
    }

    @Test
    void theResponseHasNoPersonalOrInternalData() throws Exception {
        ParcelDetails parcel = parcel();

        String body = get(TRACKING + parcel.trackingNumber()).body();

        // Postal codes and the fee are checked in PublicTrackingMapperTest: here random timestamps
        // and tracking numbers could contain the same digits by chance.
        assertThat(body).doesNotContain("Ayesha", "Hassan", "+9477", "+9471", "Galle Road", "Peradeniya",
                "Flat 4B", "Western", "Central", "Laptop", "Ring the bell", "ELECTRONICS", "LKR",
                parcel.id().toString(), customerId.toString(), "customerUserId", "postalCode", "quotedFee", "note");
    }

    @Test
    void lowerCaseAndSpacesInTheNumberAreIgnored() throws Exception {
        ParcelDetails parcel = parcel();

        HttpResponse<String> response = get(TRACKING + "%20" + parcel.trackingNumber().toLowerCase(Locale.ROOT)
                + "%20");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response).get("trackingNumber").asString()).isEqualTo(parcel.trackingNumber());
    }

    @Test
    void everyUnknownNumberGetsTheSame404() throws Exception {
        HttpResponse<String> first = get(TRACKING + "SD00000000");
        HttpResponse<String> second = get(TRACKING + "not-a-number");

        assertThat(first.statusCode()).isEqualTo(404);
        assertThat(second.statusCode()).isEqualTo(404);
        assertThat(body(first).get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(body(first).get("message").asString()).isEqualTo(body(second).get("message").asString());
        // The standard error body echoes the requested path; the message itself never names the number.
        assertThat(body(first).get("message").asString()).doesNotContain("SD00000000");
    }

    @Test
    void tooManyLookUpsFromOneIpGet429WithRetryAfter() throws Exception {
        String number = parcel().trackingNumber();
        for (int i = 0; i < LOOKUP_LIMIT; i++) {
            assertThat(get(TRACKING + (i % 2 == 0 ? number : "SD0000000" + i)).statusCode()).isIn(200, 404);
        }

        HttpResponse<String> blocked = get(TRACKING + number);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValueAsLong("Retry-After").orElseThrow()).isBetween(1L, 60L);
        JsonNode body = body(blocked);
        assertThat(body.get("code").asString()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(body.get("message").asString()).startsWith("Too many tracking look-ups.");
    }

    @Test
    void maintenanceModeReturns503() throws Exception {
        String number = parcel().trackingNumber();
        UUID adminId = userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id();
        String admin = jwtService.generateToken(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));

        assertThat(saveSettings(admin, form -> form.put("maintenanceMode", true)).statusCode()).isEqualTo(200);
        try {
            HttpResponse<String> blocked = get(TRACKING + number);

            assertThat(blocked.statusCode()).isEqualTo(503);
            assertThat(body(blocked).get("code").asString()).isEqualTo("MAINTENANCE_MODE");
        } finally {
            assertThat(saveSettings(admin, form -> form.put("maintenanceMode", false)).statusCode()).isEqualTo(200);
        }
        assertThat(get(TRACKING + number).statusCode()).isEqualTo(200);
    }

    /** Saves the current system settings with the given changes, as SystemSettingsIT does. */
    private HttpResponse<String> saveSettings(String token, Consumer<ObjectNode> changes) throws Exception {
        ObjectNode form = (ObjectNode) json.readTree(get(SETTINGS, token).body());
        form.remove("updatedAt");
        changes.accept(form);
        return put(SETTINGS, json.writeValueAsString(form), token);
    }
}
