package com.swiftdrop.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.config.api.ZoneApi;
import com.swiftdrop.config.service.ConfigZoneResolver;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.service.ZoneResolver;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The admin Service Areas API end to end, ZoneApi on real data, and zone prices in fee estimates. */
class ServiceAreaIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String AREAS = "/api/v1/admin/service-areas";

    @Autowired private JsonMapper json;
    @Autowired private ZoneApi zoneApi;
    @Autowired private FeeApi feeApi;
    @Autowired private ApplicationContext context;

    @LocalServerPort private int port;

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    /** DELETE as the given user (IntegrationTestBase has no delete helper). */
    private HttpResponse<String> delete(String path, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).DELETE()
                .header("Authorization", "Bearer " + bearerToken).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String token(String email, String password, String portal) throws Exception {
        return body(post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal)))
                .get("token").asString();
    }

    private String adminToken() throws Exception {
        return token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
    }

    /** A unique place name, so tests never collide with each other's coverage. */
    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Creates a zone covering {@code city} in {@code district}: the whole city plus one postal code. */
    private JsonNode createZone(String admin, String name, String district, String city, String postal)
            throws Exception {
        HttpResponse<String> created = post(AREAS, """
                {"name":"%s","description":"Test zone","coverageAreas":[
                  {"district":"%s","city":"%s"},
                  {"district":"%s","city":"%s","postalCode":"%s"}]}"""
                .formatted(name, district, city, district, city, postal), admin);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return body(created);
    }

    @Test
    void theAppHasExactlyOneZoneResolverAndItIsTheConfigModules() {
        assertThat(context.getBeansOfType(ZoneResolver.class)).hasSize(1);
        assertThat(context.getBean(ZoneResolver.class)).isInstanceOf(ConfigZoneResolver.class);
    }

    @Test
    void zonesAreCreatedResolvedAndKeptApart() throws Exception {
        String admin = adminToken();
        String district = unique("District");
        String city = unique("City");
        String name = unique("Zone");

        JsonNode zone = createZone(admin, name, district, city, "10500");
        UUID zoneId = UUID.fromString(zone.get("id").asString());
        assertThat(zone.get("coverageCount").asInt()).isEqualTo(2);
        assertThat(zone.get("postalCodeRange").asString()).isEqualTo("10500");

        // Found through the list (search by city) and by id.
        JsonNode rows = body(get(AREAS + "?search=" + city.substring(5).replace(" ", "%20"), admin)).get("content");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("cities").get(0).asString()).isEqualTo(city);
        assertThat(get(AREAS + "/" + zoneId, admin).statusCode()).isEqualTo(200);
        assertThat(get(AREAS + "/" + UUID.randomUUID(), admin).statusCode()).isEqualTo(404);

        // ZoneApi: exact postal match, whole-city match, case and spaces ignored, nothing elsewhere.
        assertThat(zoneApi.findZoneId(district, city, "10500")).contains(zoneId);
        assertThat(zoneApi.findZoneId(" " + district.toUpperCase(), city.toLowerCase() + " ", "99999")).contains(zoneId);
        assertThat(zoneApi.findZoneId(district, unique("Elsewhere"), null)).isEmpty();
        assertThat(zoneApi.zoneNames(java.util.List.of(zoneId))).containsEntry(zoneId, name);

        // Same name in another case: 409 on "name".
        HttpResponse<String> sameName = post(AREAS, "{\"name\":\"" + name.toUpperCase() + "\"}", admin);
        assertThat(sameName.statusCode()).isEqualTo(409);
        assertThat(body(sameName).get("fieldErrors").get(0).get("field").asString()).isEqualTo("name");

        // Another active zone can't cover the same place: 409 naming the zone.
        String otherName = unique("Other");
        HttpResponse<String> overlap = post(AREAS, """
                {"name":"%s","coverageAreas":[{"district":"%s","city":"%s"}]}"""
                .formatted(otherName, district.toLowerCase(), city), admin);
        assertThat(overlap.statusCode()).isEqualTo(409);
        assertThat(body(overlap).get("message").asString()).contains(name);

        // ... but an inactive one can, and can't be activated while the first zone is active.
        HttpResponse<String> parked = post(AREAS, """
                {"name":"%s","active":false,"coverageAreas":[{"district":"%s","city":"%s"}]}"""
                .formatted(otherName, district, city), admin);
        assertThat(parked.statusCode()).isEqualTo(201);
        String parkedId = body(parked).get("id").asString();
        assertThat(post(AREAS + "/" + parkedId + "/activate", "", admin).statusCode()).isEqualTo(409);

        // Deactivated: lookups ignore it, and the other zone may now be activated.
        assertThat(post(AREAS + "/" + zoneId + "/deactivate", "", admin).statusCode()).isEqualTo(200);
        assertThat(zoneApi.findZoneId(district, city, "10500")).isEmpty();
        assertThat(zoneApi.missingOrInactive(java.util.List.of(zoneId))).containsExactly(zoneId);
        assertThat(post(AREAS + "/" + parkedId + "/activate", "", admin).statusCode()).isEqualTo(200);
        assertThat(zoneApi.findZoneId(district, city, null)).contains(UUID.fromString(parkedId));

        // Coverage rows are added (201) and removed (204).
        HttpResponse<String> added = post(AREAS + "/" + parkedId + "/coverage-areas", """
                {"district":"%s","city":"%s","postalCode":"10600"}""".formatted(district, city), admin);
        assertThat(added.statusCode()).isEqualTo(201);
        String coverageId = body(added).get("id").asString();
        assertThat(delete(AREAS + "/" + parkedId + "/coverage-areas/" + coverageId, admin).statusCode())
                .isEqualTo(204);
        assertThat(delete(AREAS + "/" + parkedId + "/coverage-areas/" + coverageId, admin).statusCode())
                .isEqualTo(404);
    }

    @Test
    void onlyTheDispatcherAdminMayUseTheApi() throws Exception {
        assertThat(get(AREAS).statusCode()).isEqualTo(401);

        String admin = adminToken();
        String email = "courier-" + UUID.randomUUID() + "@example.com";
        assertThat(post("/api/v1/admin/couriers", """
                {"fullName":"Ramesh Perera","email":"%s","phone":"+94771234567","password":"courier2026",
                 "vehicleType":"Motorbike","vehicleRegistration":"IT-%s"}"""
                .formatted(email, UUID.randomUUID().toString().substring(0, 8).toUpperCase()), admin).statusCode())
                .isEqualTo(201);
        String courier = token(email, "courier2026", "COURIER");

        assertThat(get(AREAS, courier).statusCode()).isEqualTo(403);
        assertThat(post(AREAS, "{\"name\":\"Nope\"}", courier).statusCode()).isEqualTo(403);
    }

    @Test
    void aFeeEstimateNowAppliesTheZoneAdjustment() throws Exception {
        String admin = adminToken();
        String district = unique("District");
        String pickupCity = unique("Pickup");
        String dropoffCity = unique("Dropoff");
        String from = createZone(admin, unique("From"), district, pickupCity, "10700").get("id").asString();
        String to = createZone(admin, unique("To"), district, dropoffCity, "10800").get("id").asString();

        // The newest effective rule wins (FeeService), so this one is used; switched off at the end.
        HttpResponse<String> rule = post("/api/v1/pricing-rules", """
                {"ruleName":"%s","baseFee":"400.00","includedWeightKg":"1.000","additionalFeePerKg":"50.00",
                 "oversizeFee":"0.00","effectiveFrom":"%s","active":true}"""
                .formatted(unique("Zone IT"), Instant.now().truncatedTo(ChronoUnit.SECONDS)), admin);
        assertThat(rule.statusCode()).as(rule.body()).isEqualTo(201);
        String ruleId = body(rule).get("id").asString();
        try {
            assertThat(post("/api/v1/pricing-rules/" + ruleId + "/zone-adjustments", """
                    {"pickupZoneId":"%s","deliveryZoneId":"%s","adjustmentType":"FLAT","adjustmentValue":"150.00"}"""
                    .formatted(from, to), admin).statusCode()).isEqualTo(201);

            FeeEstimate estimate = feeApi.estimateFee(new FeeRequest(new FeeLocation(district, pickupCity, null),
                    new FeeLocation(district, dropoffCity, "10800"), BigDecimal.ONE, null, null, null));

            assertThat(estimate.lines()).extracting(FeeLine::label).contains("Zone adjustment");
        } finally {
            post("/api/v1/pricing-rules/" + ruleId + "/deactivate", "", admin);
        }
    }
}
