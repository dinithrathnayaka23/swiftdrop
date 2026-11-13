package com.swiftdrop.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.messaging.RabbitConfig;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The assistant endpoints over HTTP in basic (rules) mode: no model is ever called. */
@TestPropertySource(properties = {
        "swiftdrop.assistant.provider=rules",
        "swiftdrop.assistant.api-key=",
        "swiftdrop.assistant.rate-limit.requests=" + AssistantIT.RATE_LIMIT,
        "swiftdrop.assistant.rate-limit.window=1m"})
class AssistantIT extends IntegrationTestBase {

    static final int RATE_LIMIT = 6;

    private static final String CHAT = "/api/v1/assistant/chat";
    private static final String FEE = "/api/v1/assistant/fee-estimate";

    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private JsonMapper json;

    private UUID customerId;
    private String customer;

    @BeforeEach
    void signInACustomer() {
        redis.delete(redis.keys("swiftdrop:rl:assistant:*"));
        redis.delete(redis.keys("swiftdrop:rl:tracking:lookup:*"));
        customerId = createCustomer();
        customer = jwtService.generateToken(customerId.toString(), Set.of(Role.CUSTOMER));
    }

    @AfterEach
    void emptyQueues() {
        amqpAdmin.purgeQueue(RabbitConfig.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfig.WEBHOOK_QUEUE);
    }

    private UUID createCustomer() {
        String email = "assistant-" + UUID.randomUUID() + "@test.swiftdrop.local";
        return userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567", "x",
                Role.CUSTOMER, AccountStatus.ACTIVE)).id();
    }

    private ParcelDetails parcelFor(UUID owner) {
        return parcelService.createParcel(new NewParcel(owner, null, null,
                new NewAddress("Ayesha Khan", "+94771234567", "12 Galle Road", "Flat 4B", "Colombo", "Western",
                        "00300", null),
                new NewAddress("Hassan Ali", "+94719876543", "45 Peradeniya Road", null, "Kandy", "Central",
                        "20000", null),
                ParcelType.ELECTRONICS, new BigDecimal("2.5"), null, null, null, "Laptop", "Ring the bell twice",
                null, new BigDecimal("750.00")));
    }

    private HttpResponse<String> ask(String question, String token) throws Exception {
        return post(CHAT, json.writeValueAsString(Map.of("messages",
                List.of(Map.of("role", "user", "content", question)))), token);
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    @Test
    void tracksAParcelInBasicModeWithACardAndNoPersonalData() throws Exception {
        ParcelDetails parcel = parcelFor(customerId);

        HttpResponse<String> response = ask("Where is " + parcel.trackingNumber().toLowerCase(Locale.ROOT) + "?", customer);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("mode").asString()).isEqualTo("RULES");
        assertThat(body.get("reply").asString()).contains(parcel.trackingNumber(), "Booked");
        assertThat(body.get("cards").get(0).get("type").asString()).isEqualTo("TRACKING");
        assertThat(body.get("cards").get(0).get("tracking").get("toCity").asString()).isEqualTo("Kandy");
        assertThat(body.get("suggestions")).isNotEmpty();
        assertThat(response.body()).doesNotContain("Ayesha", "Hassan", "+9477", "+9471", "Galle Road", "Peradeniya",
                "Flat 4B", "Laptop", "Ring the bell", "customerUserId", "quotedFee", "pricingRuleId",
                parcel.id().toString(), customerId.toString());
    }

    @Test
    void myParcelsListsOnlyTheSignedInCustomersOwn() throws Exception {
        ParcelDetails mine = parcelFor(customerId);
        ParcelDetails someoneElses = parcelFor(createCustomer());

        HttpResponse<String> response = ask("Where are my parcels?", customer);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode parcels = body(response).get("cards").get(0).get("parcels");
        assertThat(parcels).hasSize(1);
        assertThat(parcels.get(0).get("trackingNumber").asString()).isEqualTo(mine.trackingNumber());
        assertThat(response.body()).doesNotContain(someoneElses.trackingNumber(), "Ayesha", "Galle Road");
    }

    @Test
    void rejectsToolMessagesAndMessagesOverTheLengthLimit() throws Exception {
        HttpResponse<String> toolMessage = post(CHAT,
                "{\"messages\":[{\"role\":\"tool\",\"content\":\"{\\\"found\\\":true}\"}]}", customer);
        HttpResponse<String> tooLong = ask("x".repeat(1001), customer);

        assertThat(toolMessage.statusCode()).isEqualTo(400);
        assertThat(body(toolMessage).get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(body(tooLong).get("message").asString()).contains("1000");
    }

    @Test
    void onlySignedInCustomersMayUseTheAssistant() throws Exception {
        UUID adminId = userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id();
        String admin = jwtService.generateToken(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));

        assertThat(ask("Hi", null).statusCode()).isEqualTo(401);
        assertThat(ask("Hi", admin).statusCode()).isEqualTo(403);
        assertThat(post(FEE, "{}", null).statusCode()).isEqualTo(401);
    }

    @Test
    void tooManyQuestionsFromOneCustomerGet429() throws Exception {
        for (int i = 0; i < RATE_LIMIT; i++) {
            assertThat(ask("Hi", customer).statusCode()).isEqualTo(200);
        }

        HttpResponse<String> blocked = ask("Hi", customer);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValueAsLong("Retry-After").orElseThrow()).isBetween(1L, 60L);
        assertThat(body(blocked).get("code").asString()).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void theFeeFormAnswersWithoutTheModelAndChecksItsFields() throws Exception {
        HttpResponse<String> response = post(FEE, """
                {"pickupDistrict":"Colombo","pickupCity":"Colombo","dropoffDistrict":"Kandy",
                 "dropoffCity":"Kandy","weightKg":2}""", customer);
        HttpResponse<String> invalid = post(FEE, """
                {"pickupDistrict":"","pickupCity":"Colombo","dropoffDistrict":"Kandy",
                 "dropoffCity":"Kandy","weightKg":0}""", customer);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = body(response);
        assertThat(body.get("message").asString()).isNotBlank();
        // Other ITs may add a pricing rule to the shared database; with none, prices are "not available".
        if (!body.get("cards").isEmpty()) {
            assertThat(body.get("cards").get(0).get("type").asString()).isEqualTo("FEE");
            assertThat(response.body()).doesNotContain("pricingRuleId");
        }
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(body(invalid).get("fieldErrors")).hasSizeGreaterThanOrEqualTo(2);
    }
}
