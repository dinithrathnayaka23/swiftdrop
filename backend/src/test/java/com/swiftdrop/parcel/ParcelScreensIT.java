package com.swiftdrop.parcel;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.service.MerchantService;
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

/** The customer and admin parcel endpoints over HTTP, against the real database. */
class ParcelScreensIT extends IntegrationTestBase {

    @Autowired private ParcelService parcelService;
    @Autowired private ParcelStatusService statusService;
    @Autowired private UserService userService;
    @Autowired private MerchantService merchantService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JsonMapper json;

    private UUID customerId;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void signIn() {
        customerId = newUser(Role.CUSTOMER);
        customerToken = jwtService.generateToken(customerId.toString(), Set.of(Role.CUSTOMER));
        UUID adminId = userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id();
        adminToken = jwtService.generateToken(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));
    }

    private UUID newUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.swiftdrop.local";
        return userService.createUser(NewUser.withPassword("Ayesha Khan", email, "+94771234567", "x", role,
                AccountStatus.ACTIVE)).id();
    }

    private static NewAddress address(String contactName, String city) {
        return new NewAddress(contactName, "+94771234567", "12 Galle Road", null, city, "Western", null, null);
    }

    private ParcelDetails customerParcel(UUID owner, String city) {
        return parcelService.createParcel(new NewParcel(owner, null, null, address("Ayesha Khan", "Colombo"),
                address("Hassan Ali", city), ParcelType.PARCEL, new BigDecimal("2.5"), null, null, null, "Books",
                null, null, new BigDecimal("750.00")));
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private static List<String> field(JsonNode array, String name) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(name).asString()));
        return values;
    }

    @Test
    void customerSeesOnlyTheirOwnParcelsInSpringsPagedShape() throws Exception {
        ParcelDetails mine = customerParcel(customerId, "Kandy");
        customerParcel(newUser(Role.CUSTOMER), "Galle");

        HttpResponse<String> response = get("/api/v1/customer/parcels?size=5", customerToken);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = body(response);
        assertThat(field(page.get("content"), "trackingNumber")).containsExactly(mine.trackingNumber());
        assertThat(page.get("content").get(0).get("destinationCity").asString()).isEqualTo("Kandy");
        assertThat(page.get("content").get(0).get("status").asString()).isEqualTo("CREATED");
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("page").get("size").asInt()).isEqualTo(5);
        assertThat(page.get("page").get("number").asInt()).isZero();
    }

    @Test
    void customerFiltersBySearchStatusAndBookingDate() throws Exception {
        ParcelDetails kandy = customerParcel(customerId, "Kandy");
        ParcelDetails galle = customerParcel(customerId, "Galle");
        statusService.changeStatus(galle.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, null));
        jdbc.update("UPDATE parcels SET created_at = TIMESTAMPTZ '2026-01-15 10:00:00+05:30' WHERE parcel_id = ?",
                kandy.id());

        assertThat(field(body(get("/api/v1/customer/parcels?search=kand", customerToken)).get("content"),
                "trackingNumber")).containsExactly(kandy.trackingNumber());
        assertThat(field(body(get("/api/v1/customer/parcels?status=PICKED_UP", customerToken)).get("content"),
                "trackingNumber")).containsExactly(galle.trackingNumber());
        assertThat(field(body(get("/api/v1/customer/parcels?bookedFrom=2026-01-15&bookedTo=2026-01-15",
                customerToken)).get("content"), "trackingNumber")).containsExactly(kandy.trackingNumber());
    }

    @Test
    void customerCountsCoverEveryStatus() throws Exception {
        customerParcel(customerId, "Kandy");

        JsonNode counts = body(get("/api/v1/customer/parcels/counts", customerToken));

        assertThat(counts.get("CREATED").asLong()).isEqualTo(1);
        assertThat(counts.get("DELIVERED").asLong()).isZero();
        assertThat(counts.size()).isEqualTo(ParcelStatusCode.values().length);
    }

    @Test
    void customerOpensTheirParcelByTrackingNumberButNotSomeoneElses() throws Exception {
        ParcelDetails mine = customerParcel(customerId, "Kandy");
        ParcelDetails theirs = customerParcel(newUser(Role.CUSTOMER), "Galle");

        HttpResponse<String> own = get("/api/v1/customer/parcels/by-tracking-number/"
                + mine.trackingNumber().toLowerCase(), customerToken);
        assertThat(own.statusCode()).isEqualTo(200);
        assertThat(body(own).get("dropoffAddress").get("contactName").asString()).isEqualTo("Hassan Ali");
        assertThat(body(own).get("history").get(0).get("status").asString()).isEqualTo("CREATED");

        HttpResponse<String> other = get("/api/v1/customer/parcels/by-tracking-number/"
                + theirs.trackingNumber(), customerToken);
        assertThat(other.statusCode()).isEqualTo(404);
    }

    @Test
    void invalidPageSizeIsABadRequest() throws Exception {
        assertThat(get("/api/v1/customer/parcels?size=500", customerToken).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/customer/parcels?page=-1", customerToken).statusCode()).isEqualTo(400);
    }

    @Test
    void customersCannotUseTheAdminEndpointsAndAdminsCannotUseTheCustomerOnes() throws Exception {
        assertThat(get("/api/v1/admin/parcels", customerToken).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/customer/parcels", adminToken).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/customer/parcels", null).statusCode()).isEqualTo(401);
    }

    @Test
    void adminListShowsTheMerchantAndFiltersByTabSearchAndArea() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID contact = newUser(Role.MERCHANT);
        UUID merchantId = merchantService.registerPending(new NewMerchant(contact, "Zephyr Mart " + suffix,
                "PV" + suffix, "zephyr-" + suffix + "@test.swiftdrop.local", "+94771234567", "1 Main St")).merchantId();
        merchantService.approve(merchantId, userService.findOnlyAccountWithRole(Role.DISPATCHER_ADMIN).orElseThrow().id());
        ParcelDetails order = parcelService.createParcel(new NewParcel(null, merchantId, "ORD-" + suffix,
                address("Zephyr Mart", "Colombo"), address("Kasun Perera", "Moratuwa " + suffix),
                ParcelType.ELECTRONICS, BigDecimal.ONE, null, null, null, null, null, null, BigDecimal.TEN));

        JsonNode byMerchant = body(get("/api/v1/admin/parcels?search=zephyr%20mart%20" + suffix, adminToken));
        assertThat(field(byMerchant.get("content"), "trackingNumber")).containsExactly(order.trackingNumber());
        assertThat(byMerchant.get("content").get(0).get("merchantName").asString()).isEqualTo("Zephyr Mart " + suffix);
        assertThat(byMerchant.get("content").get(0).get("recipientName").asString()).isEqualTo("Kasun Perera");

        JsonNode byArea = body(get("/api/v1/admin/parcels?area=moratuwa%20" + suffix, adminToken));
        assertThat(field(byArea.get("content"), "trackingNumber")).containsExactly(order.trackingNumber());

        JsonNode unassigned = body(get("/api/v1/admin/parcels?tab=UNASSIGNED&size=100", adminToken));
        assertThat(field(unassigned.get("content"), "status")).containsOnly("CREATED");
        JsonNode delivered = body(get("/api/v1/admin/parcels?tab=DELIVERED&search=" + order.trackingNumber(),
                adminToken));
        assertThat(delivered.get("content")).isEmpty();

        JsonNode detail = body(get("/api/v1/admin/parcels/" + order.id(), adminToken));
        assertThat(detail.get("merchantName").asString()).isEqualTo("Zephyr Mart " + suffix);
        assertThat(detail.get("parcelType").asString()).isEqualTo("ELECTRONICS");
        assertThat(detail.get("notes")).isEmpty();
    }

    @Test
    void adminCountsAddUpToAll() throws Exception {
        customerParcel(customerId, "Kandy");

        JsonNode counts = body(get("/api/v1/admin/parcels/counts", adminToken));

        long all = counts.get("ALL").asLong();
        assertThat(all).isPositive();
        assertThat(counts.get("UNASSIGNED").asLong() + counts.get("ASSIGNED").asLong()
                + counts.get("IN_TRANSIT").asLong() + counts.get("DELIVERED").asLong()
                + counts.get("FAILED").asLong()).isEqualTo(all);
    }

    @Test
    void unknownAdminParcelIsNotFound() throws Exception {
        assertThat(get("/api/v1/admin/parcels/" + UUID.randomUUID(), adminToken).statusCode()).isEqualTo(404);
    }
}
