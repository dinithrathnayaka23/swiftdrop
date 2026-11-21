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
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The merchant portal's orders search and filters over HTTP, against the real database. */
class MerchantOrderSearchIT extends IntegrationTestBase {

    private static final String ORDERS = "/api/v1/merchant/orders";

    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private MerchantService merchantService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;

    private UUID merchantId;
    private String token;

    @BeforeEach
    void signInAsApprovedMerchant() {
        String email = "merchant-" + UUID.randomUUID() + "@test.swiftdrop.local";
        UUID userId = userService.createUser(NewUser.withPassword("Sunil Fernando", email, "+94771234567", "x",
                Role.MERCHANT, AccountStatus.ACTIVE)).id();
        merchantId = merchantService.registerPending(new NewMerchant(userId, "BrightMart",
                "PV" + UUID.randomUUID().toString().substring(0, 8), email, "+94771234567",
                "1 Main Street, Colombo")).merchantId();
        jdbc.update("UPDATE merchants SET approval_status = 'APPROVED' WHERE merchant_id = ?", merchantId);
        token = jwtService.generateToken(userId.toString(), Set.of(Role.MERCHANT));
    }

    private ParcelDetails order(UUID owner, String reference, String recipient, String city) {
        NewAddress pickup = new NewAddress("BrightMart Warehouse", "+94771234567", "1 Depot Road", null,
                "Colombo", "Western", null, null);
        NewAddress dropoff = new NewAddress(recipient, "+94772222222", "25 Temple Street", null, city,
                "Central", null, null);
        return parcelService.createParcel(new NewParcel(null, owner, reference, pickup, dropoff, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, null, new BigDecimal("450.00")));
    }

    private void setStatus(ParcelDetails parcel, String status) {
        jdbc.update("UPDATE parcels SET current_status_code = ? WHERE parcel_id = ?", status, parcel.id());
    }

    /** Sets the creation time, as an ISO timestamp with offset. */
    private void setCreatedAt(ParcelDetails parcel, String timestamp) {
        jdbc.update("UPDATE parcels SET created_at = CAST(? AS timestamptz) WHERE parcel_id = ?", timestamp,
                parcel.id());
    }

    private JsonNode list(String query) throws Exception {
        HttpResponse<String> response = get(ORDERS + "?" + query, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private List<String> trackingNumbers(String query) throws Exception {
        List<String> values = new ArrayList<>();
        list(query).get("content").forEach(row -> values.add(row.get("trackingNumber").asString()));
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void searchMatchesTrackingNumberReferenceRecipientAndCityIgnoringCase() throws Exception {
        ParcelDetails kandy = order(merchantId, "ORD-1001", "Nimal Perera", "Kandy");
        ParcelDetails galle = order(merchantId, "ORD-2002", "Kamala Silva", "Galle");

        assertThat(trackingNumbers("search=" + kandy.trackingNumber().toLowerCase()))
                .containsExactly(kandy.trackingNumber());
        assertThat(trackingNumbers("search=ord-2002")).containsExactly(galle.trackingNumber());
        assertThat(trackingNumbers("search=" + encode("nimal per"))).containsExactly(kandy.trackingNumber());
        assertThat(trackingNumbers("search=GALL")).containsExactly(galle.trackingNumber());
        assertThat(trackingNumbers("search=ORD-")).containsExactlyInAnyOrder(kandy.trackingNumber(),
                galle.trackingNumber());
    }

    @Test
    void likeWildcardsInTheSearchMatchOnlyThemselves() throws Exception {
        ParcelDetails percent = order(merchantId, "SALE_50%", "Nimal Perera", "Kandy");
        order(merchantId, "SALEX50X", "Nimal Perera", "Kandy");

        assertThat(trackingNumbers("search=" + encode("_50%"))).containsExactly(percent.trackingNumber());
        assertThat(trackingNumbers("search=" + encode("%"))).containsExactly(percent.trackingNumber());
    }

    @Test
    void otherMerchantsOrdersAreNeverListed() throws Exception {
        ParcelDetails mine = order(merchantId, "ORD-1001", "Nimal Perera", "Kandy");
        UUID otherUser = userService.createUser(NewUser.withPassword("Other", "other-" + UUID.randomUUID()
                + "@test.swiftdrop.local", "+94771234567", "x", Role.MERCHANT, AccountStatus.ACTIVE)).id();
        UUID other = merchantService.registerPending(new NewMerchant(otherUser, "Other Shop",
                "PV" + UUID.randomUUID().toString().substring(0, 8), "o@test.swiftdrop.local", "+94771234567",
                "2 Main Street, Colombo")).merchantId();
        order(other, "ORD-1001", "Nimal Perera", "Kandy");

        assertThat(trackingNumbers("search=ORD-1001")).containsExactly(mine.trackingNumber());
    }

    @Test
    void statusAndDatesFilterAndEveryFilterCombines() throws Exception {
        ParcelDetails early = order(merchantId, "ORD-1", "Nimal Perera", "Kandy");
        ParcelDetails onTheDay = order(merchantId, "ORD-2", "Nimal Perera", "Kandy");
        ParcelDetails lastMinute = order(merchantId, "ORD-3", "Kamala Silva", "Galle");
        ParcelDetails nextDay = order(merchantId, "ORD-4", "Nimal Perera", "Kandy");
        setCreatedAt(early, "2026-11-01T23:59:00+05:30");
        setCreatedAt(onTheDay, "2026-11-02T00:00:00+05:30");
        setCreatedAt(lastMinute, "2026-11-02T23:59:59+05:30");
        setCreatedAt(nextDay, "2026-11-03T00:00:00+05:30");
        setStatus(onTheDay, "IN_TRANSIT");
        setStatus(lastMinute, "IN_TRANSIT");

        assertThat(trackingNumbers("from=2026-11-02&to=2026-11-02"))
                .containsExactly(lastMinute.trackingNumber(), onTheDay.trackingNumber());
        assertThat(trackingNumbers("from=2026-11-02")).containsExactly(nextDay.trackingNumber(),
                lastMinute.trackingNumber(), onTheDay.trackingNumber());
        assertThat(trackingNumbers("to=2026-11-01")).containsExactly(early.trackingNumber());
        assertThat(trackingNumbers("status=IN_TRANSIT")).containsExactly(lastMinute.trackingNumber(),
                onTheDay.trackingNumber());
        assertThat(trackingNumbers("status=IN_TRANSIT&search=kandy&from=2026-11-02&to=2026-11-02"))
                .containsExactly(onTheDay.trackingNumber());
        assertThat(trackingNumbers("status=DELIVERED&search=kandy")).isEmpty();
    }

    @Test
    void pagingCountsOnlyTheMatchingOrders() throws Exception {
        for (int i = 0; i < 3; i++) {
            order(merchantId, "KANDY-" + i, "Nimal Perera", "Kandy");
        }
        order(merchantId, "GALLE-1", "Kamala Silva", "Galle");

        JsonNode page = list("search=kandy&size=2&page=1");

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("page").get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void badFiltersAnswer400WithAClearMessage() throws Exception {
        assertBadRequest("status=LOST", "status must be one of CREATED, PICKED_UP, IN_TRANSIT, "
                + "OUT_FOR_DELIVERY, DELIVERED, FAILED_ATTEMPT, FAILED");
        assertBadRequest("from=2026-02-30", "from must be a date in the format yyyy-MM-dd");
        assertBadRequest("to=02-11-2026", "to must be a date in the format yyyy-MM-dd");
        assertBadRequest("from=2026-11-03&to=2026-11-02", "from must be on or before to");
        assertBadRequest("search=" + "x".repeat(101), "search must be at most 100 characters");
        assertBadRequest("size=101", "size must be between 1 and 100");
    }

    private void assertBadRequest(String query, String message) throws Exception {
        HttpResponse<String> response = get(ORDERS + "?" + query, token);

        assertThat(response.statusCode()).as(query).isEqualTo(400);
        JsonNode error = json.readTree(response.body());
        assertThat(error.get("code").asString()).isEqualTo("BAD_REQUEST");
        assertThat(error.get("message").asString()).isEqualTo(message);
    }
}
