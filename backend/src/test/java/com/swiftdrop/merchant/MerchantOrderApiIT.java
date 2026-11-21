package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelStatusService;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * The merchant order API ({@code /api/v1/orders}) end to end: an integration creates an order with
 * its API key, priced by the pricing module, then follows it; the order shows up in the portal.
 */
class MerchantOrderApiIT extends MerchantIntegrationTestBase {

    private static final String ORDERS = "/api/v1/orders";

    @Autowired private ParcelStatusService statusService;

    private SignedInMerchant merchant;
    private String apiKey;
    private String admin;
    private String pricingRuleId;

    /** The newest effective pricing rule is used, so orders cost 400 + 50 per kg over the first. */
    @BeforeEach
    void setUp() throws Exception {
        merchant = approvedMerchant("BrightMart");
        apiKey = createApiKey(merchant).get("apiKey").asString();
        admin = adminToken();
        pricingRuleId = ok(post("/api/v1/pricing-rules", """
                {"ruleName":"Order API IT %s","baseFee":"400.00","includedWeightKg":"1.000",
                 "additionalFeePerKg":"50.00","oversizeFee":"0.00","effectiveFrom":"%s","active":true}"""
                .formatted(UUID.randomUUID().toString().substring(0, 8),
                        Instant.now().truncatedTo(ChronoUnit.SECONDS)), admin), 201).get("id").asString();
    }

    @AfterEach
    void switchOffPricingRule() throws Exception {
        post("/api/v1/pricing-rules/" + pricingRuleId + "/deactivate", "", admin);
    }

    private static String orderJson(String reference) {
        return """
                {"externalOrderReference":"%s",
                 "pickupAddress":{"contactName":"BrightMart Warehouse","contactPhone":"+94112223344",
                   "addressLine1":"10 Main Street","city":"Colombo","district":"Colombo"},
                 "dropoffAddress":{"contactName":"Nimal Perera","contactPhone":"+94771234567",
                   "addressLine1":"25 Lake Road","city":"Kandy","district":"Kandy","postalCode":"20000"},
                 "parcelType":"PARCEL","weightKg":2.0,"description":"Two books"}""".formatted(reference);
    }

    private static String reference() {
        return "STORE-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void anIntegrationCreatesAnOrderAndFollowsIt() throws Exception {
        String reference = reference();

        HttpResponse<String> created = withApiKey(ORDERS, orderJson(reference), apiKey);

        JsonNode order = ok(created, 201);
        String trackingNumber = order.get("trackingNumber").asString();
        assertThat(created.headers().firstValue("Location")).contains(ORDERS + "/" + trackingNumber);
        assertThat(order.get("externalOrderReference").asString()).isEqualTo(reference);
        assertThat(order.get("status").asString()).isEqualTo("CREATED");
        assertThat(order.get("quotedFee").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(order.get("currency").asString()).isEqualTo("LKR");
        // No personal data comes back from the order API.
        assertThat(created.body()).doesNotContain("Nimal").doesNotContain("+94771234567")
                .doesNotContain("Lake Road");

        assertThat(ok(withApiKey(ORDERS + "/" + trackingNumber, null, apiKey), 200)
                .get("externalOrderReference").asString()).isEqualTo(reference);
        assertThat(ok(withApiKey(ORDERS + "/" + trackingNumber + "/status", null, apiKey), 200).asString())
                .isEqualTo("CREATED");

        UUID parcelId = UUID.fromString(ok(get("/api/v1/merchant/orders/" + trackingNumber, merchant.token()), 200)
                .get("parcelId").asString());
        statusService.changeStatus(parcelId, StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));

        assertThat(ok(withApiKey(ORDERS + "/" + trackingNumber + "/status", null, apiKey), 200).asString())
                .isEqualTo("PICKED_UP");
    }

    @Test
    void theOrderShowsUpInThePortalWithItsDeliveryDetails() throws Exception {
        String reference = reference();
        String trackingNumber = ok(withApiKey(ORDERS, orderJson(reference), apiKey), 201)
                .get("trackingNumber").asString();

        JsonNode row = ok(get("/api/v1/merchant/orders?search=" + reference, merchant.token()), 200)
                .get("content").get(0);
        assertThat(row.get("trackingNumber").asString()).isEqualTo(trackingNumber);
        assertThat(row.get("recipientName").asString()).isEqualTo("Nimal Perera");
        assertThat(row.get("deliveryCity").asString()).isEqualTo("Kandy");

        JsonNode details = ok(get("/api/v1/merchant/orders/" + trackingNumber, merchant.token()), 200);
        assertThat(details.get("parcelType").asString()).isEqualTo("PARCEL");
        assertThat(details.get("contentDescription").asString()).isEqualTo("Two books");
        assertThat(details.get("dropoffAddress").get("postalCode").asString()).isEqualTo("20000");
    }

    @Test
    void sendingTheSameOrderReferenceAgainReturnsTheExistingOrder() throws Exception {
        String reference = reference();
        String first = ok(withApiKey(ORDERS, orderJson(reference), apiKey), 201).get("trackingNumber").asString();

        String again = ok(withApiKey(ORDERS, orderJson(reference), apiKey), 201).get("trackingNumber").asString();

        assertThat(again).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parcels WHERE merchant_id = ? AND "
                + "external_order_reference = ?", Long.class, merchant.merchantId(), reference)).isEqualTo(1);
    }

    @Test
    void anotherMerchantCannotSeeTheOrder() throws Exception {
        String trackingNumber = ok(withApiKey(ORDERS, orderJson(reference()), apiKey), 201)
                .get("trackingNumber").asString();
        String otherKey = createApiKey(approvedMerchant("Other Shop")).get("apiKey").asString();

        assertError(withApiKey(ORDERS + "/" + trackingNumber, null, otherKey), 404, "NOT_FOUND");
        assertError(withApiKey(ORDERS + "/" + trackingNumber + "/status", null, otherKey), 404, "NOT_FOUND");
    }

    @Test
    void anOAuthTokenCanCreateOrdersToo() throws Exception {
        JsonNode key = createApiKey(merchant);
        String accessToken = ok(oauthToken(Map.of("grant_type", "client_credentials",
                "client_id", key.get("keyId").asString(), "client_secret", key.get("apiKey").asString())), 200)
                .get("access_token").asString();

        String trackingNumber = ok(withAccessToken(ORDERS, orderJson(reference()), accessToken), 201)
                .get("trackingNumber").asString();

        assertThat(ok(withAccessToken(ORDERS + "/" + trackingNumber + "/status", null, accessToken), 200)
                .asString()).isEqualTo("CREATED");
    }

    @Test
    void anInvalidOrderOrAMissingKeyIsRefused() throws Exception {
        HttpResponse<String> invalid = withApiKey(ORDERS, """
                {"externalOrderReference":"","parcelType":"PARCEL","weightKg":-1}""", apiKey);

        assertError(invalid, 400, "VALIDATION_FAILED");
        assertThat(invalid.body()).contains("externalOrderReference", "pickupAddress", "weightKg");
        assertError(post(ORDERS, orderJson(reference())), 401, "UNAUTHORIZED");
    }

    @Test
    void anUnapprovedMerchantsKeyCannotCreateOrders() throws Exception {
        setMerchantStatus(merchant.merchantId(), "SUSPENDED");

        assertError(withApiKey(ORDERS, orderJson(reference()), apiKey), 403, "ACCOUNT_DISABLED");
    }
}
