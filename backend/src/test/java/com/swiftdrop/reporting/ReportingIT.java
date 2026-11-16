package com.swiftdrop.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.notification.service.EmailService;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The admin reports end to end against a real database: the SQL, the Sri Lankan day boundaries,
 * the rates and who may see them.
 * <p>
 * The test parcels are dated March 2021, long before anything other tests create, so the counts
 * here only ever include this class's own rows.
 */
class ReportingIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@test.swiftdrop.local"; // application-test.yml
    private static final String ADMIN_PASSWORD = "test-admin-password-1";
    private static final String REPORTS = "/api/v1/admin/reports";
    private static final String RANGE = "?from=2021-03-01&to=2021-03-03";

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private static boolean seeded;

    /**
     * Five parcels around 1-3 March 2021 (Sri Lankan time, +05:30):
     * <pre>
     *   P1 created 1 Mar 10:00, delivered 2 Mar 09:00
     *   P2 created 2 Mar 01:00 (still 1 Mar in UTC), failed 3 Mar 12:00
     *   P3 created 3 Mar 08:00, still in transit
     *   P4 created 28 Feb (before the range), delivered 1 Mar 15:00
     *   P5 created 4 Mar 00:30 (still 3 Mar in UTC), after the range
     * </pre>
     */
    @BeforeEach
    void seedParcels() {
        if (seeded) {
            return;
        }
        UUID owner = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", UUID.class, ADMIN_EMAIL);
        UUID address = jdbc.queryForObject("""
                INSERT INTO addresses (contact_name, contact_phone, address_line1, city, district)
                VALUES ('Report Test', '+94771234567', '1 Test Road', 'Colombo', 'Colombo')
                RETURNING address_id""", UUID.class);

        UUID p1 = parcel(owner, address, "2021-03-01T10:00+05:30");
        history(p1, "DELIVERED", "2021-03-02T09:00+05:30");
        UUID p2 = parcel(owner, address, "2021-03-02T01:00+05:30");
        history(p2, "FAILED", "2021-03-03T12:00+05:30");
        UUID p3 = parcel(owner, address, "2021-03-03T08:00+05:30");
        history(p3, "IN_TRANSIT", "2021-03-03T11:00+05:30");
        UUID p4 = parcel(owner, address, "2021-02-28T09:00+05:30");
        history(p4, "DELIVERED", "2021-03-01T15:00+05:30");
        parcel(owner, address, "2021-03-04T00:30+05:30");
        seeded = true;
    }

    private UUID parcel(UUID owner, UUID address, String createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO parcels (tracking_number, customer_user_id, pickup_address_id, dropoff_address_id,
                                     weight_kg, quoted_fee, parcel_type, created_at)
                VALUES (?, ?, ?, ?, 1.0, 500, 'PARCEL', ?)
                RETURNING parcel_id""", UUID.class,
                "RPT-" + UUID.randomUUID().toString().substring(0, 12), owner, address, address,
                OffsetDateTime.parse(createdAt));
    }

    private void history(UUID parcelId, String status, String changedAt) {
        jdbc.update("INSERT INTO parcel_status_history (parcel_id, status_code, changed_at) VALUES (?, ?, ?)",
                parcelId, status, OffsetDateTime.parse(changedAt));
    }

    private String adminToken() throws Exception {
        return token(ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
    }

    private String token(String email, String password, String portal) throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login", """
                {"email":"%s","password":"%s","portal":"%s"}""".formatted(email, password, portal));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("token").asString();
    }

    private String customerToken() throws Exception {
        String email = "reports-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = post("/api/v1/auth/register/customer", """
                {"fullName":"Report Viewer","email":"%s","phone":"+94712345678","password":"parcel2026"}"""
                .formatted(email));
        assertThat(registered.statusCode()).isEqualTo(201);
        verifyEmail(email);
        return token(email, "parcel2026", "PUBLIC");
    }

    @Test
    void summaryCountsTheRangeInSriLankanTime() throws Exception {
        HttpResponse<String> response = get(REPORTS + "/summary" + RANGE, adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode summary = json.readTree(response.body());
        assertThat(summary.get("from").asString()).isEqualTo("2021-03-01");
        assertThat(summary.get("to").asString()).isEqualTo("2021-03-03");
        assertThat(summary.get("total").asLong()).isEqualTo(3);     // P1, P2, P3
        assertThat(summary.get("delivered").asLong()).isEqualTo(2); // P1, P4
        assertThat(summary.get("failed").asLong()).isEqualTo(1);    // P2
        assertThat(summary.get("successRate").decimalValue()).isEqualByComparingTo("66.67");
        assertThat(summary.get("failureRate").decimalValue()).isEqualByComparingTo("33.33");
    }

    @Test
    void deliveriesPerDayHasOneRowPerDay() throws Exception {
        HttpResponse<String> response = get(REPORTS + "/deliveries-per-day" + RANGE, adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode days = json.readTree(response.body());
        assertThat(days.size()).isEqualTo(3);
        assertDay(days.get(0), "2021-03-01", 1, 1, 0); // P1 created, P4 delivered
        assertDay(days.get(1), "2021-03-02", 1, 1, 0); // P2 created, P1 delivered
        assertDay(days.get(2), "2021-03-03", 1, 0, 1); // P3 created, P2 failed
    }

    @Test
    void quietDaysAreIncludedWithZeros() throws Exception {
        JsonNode days = json.readTree(
                get(REPORTS + "/deliveries-per-day?from=2021-01-10&to=2021-01-12", adminToken()).body());

        assertThat(days.size()).isEqualTo(3);
        assertDay(days.get(1), "2021-01-11", 0, 0, 0);
    }

    @Test
    void badRangesAreRejected() throws Exception {
        String admin = adminToken();

        assertThat(get(REPORTS + "/summary?from=2021-03-03&to=2021-03-01", admin).statusCode()).isEqualTo(400);
        assertThat(get(REPORTS + "/summary?from=2999-01-01", admin).statusCode()).isEqualTo(400);
        assertThat(get(REPORTS + "/summary?from=2020-01-01&to=2021-03-01", admin).statusCode()).isEqualTo(400);
        assertThat(get(REPORTS + "/deliveries-per-day?from=01-03-2021", admin).statusCode()).isEqualTo(400);
    }

    @Test
    void withoutDatesTheLastSevenDaysAreReturned() throws Exception {
        JsonNode days = json.readTree(get(REPORTS + "/deliveries-per-day", adminToken()).body());

        assertThat(days.size()).isEqualTo(7);
    }

    @Test
    void onlyTheAdminSeesReports() throws Exception {
        String customer = customerToken();

        assertThat(get(REPORTS + "/summary", customer).statusCode()).isEqualTo(403);
        assertThat(get(REPORTS + "/deliveries-per-day", customer).statusCode()).isEqualTo(403);
        assertThat(get(REPORTS + "/summary").statusCode()).isEqualTo(401);
        assertThat(get(REPORTS + "/deliveries-per-day").statusCode()).isEqualTo(401);
    }

    private static void assertDay(JsonNode day, String date, long total, long delivered, long failed) {
        assertThat(day.get("date").asString()).isEqualTo(date);
        assertThat(day.get("total").asLong()).as("total on " + date).isEqualTo(total);
        assertThat(day.get("delivered").asLong()).as("delivered on " + date).isEqualTo(delivered);
        assertThat(day.get("failed").asLong()).as("failed on " + date).isEqualTo(failed);
    }
}
