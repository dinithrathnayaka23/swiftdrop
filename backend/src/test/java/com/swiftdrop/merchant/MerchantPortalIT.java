package com.swiftdrop.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * The merchant portal over HTTP against the real database: business profile, dashboard counts,
 * orders list and order details, each scoped to the signed-in merchant. The orders filters are
 * covered in depth by {@link MerchantOrderSearchIT}.
 */
class MerchantPortalIT extends MerchantIntegrationTestBase {

    private static final String PROFILE = "/api/v1/merchant/profile";
    private static final String ORDERS = "/api/v1/merchant/orders";

    @Autowired private ParcelStatusService statusService;

    private SignedInMerchant merchant;
    private SignedInMerchant otherMerchant;

    @BeforeEach
    void signIn() {
        merchant = approvedMerchant("BrightMart");
        otherMerchant = approvedMerchant("Other Shop");
    }

    private void pickUp(ParcelDetails parcel) {
        statusService.changeStatus(parcel.id(), StatusChange.to(ParcelStatusCode.PICKED_UP, null, "Collected"));
    }

    private static List<String> trackingNumbers(JsonNode page) {
        List<String> values = new ArrayList<>();
        page.get("content").forEach(row -> values.add(row.get("trackingNumber").asString()));
        return values;
    }

    @Test
    void theMerchantSeesAndUpdatesItsBusinessProfile() throws Exception {
        JsonNode profile = ok(get(PROFILE, merchant.token()), 200);
        assertThat(profile.get("merchantId").asString()).isEqualTo(merchant.merchantId().toString());
        assertThat(profile.get("businessName").asString()).isEqualTo("BrightMart");
        assertThat(profile.get("approvalStatus").asString()).isEqualTo("APPROVED");

        JsonNode updated = ok(put(PROFILE, """
                {"contactEmail":"orders@brightmart.lk","contactPhone":"+94112345678",
                 "businessAddress":"42 Galle Road, Colombo 03"}""", merchant.token()), 200);

        assertThat(updated.get("contactEmail").asString()).isEqualTo("orders@brightmart.lk");
        JsonNode reloaded = ok(get(PROFILE, merchant.token()), 200);
        assertThat(reloaded.get("contactPhone").asString()).isEqualTo("+94112345678");
        assertThat(reloaded.get("businessAddress").asString()).isEqualTo("42 Galle Road, Colombo 03");
        assertThat(reloaded.get("businessName").asString()).isEqualTo("BrightMart");
        // The other merchant's profile is untouched.
        assertThat(ok(get(PROFILE, otherMerchant.token()), 200).get("contactEmail").asString())
                .isNotEqualTo("orders@brightmart.lk");
    }

    @Test
    void aBadProfileUpdateIsRefusedFieldByFieldAndChangesNothing() throws Exception {
        String before = ok(get(PROFILE, merchant.token()), 200).get("contactPhone").asString();

        HttpResponse<String> refused = put(PROFILE, """
                {"contactEmail":"not-an-email","contactPhone":"0771234567","businessAddress":"short"}""",
                merchant.token());

        assertError(refused, 400, "VALIDATION_FAILED");
        List<String> fields = new ArrayList<>();
        body(refused).get("fieldErrors").forEach(error -> fields.add(error.get("field").asString()));
        assertThat(fields).containsExactlyInAnyOrder("contactEmail", "contactPhone", "businessAddress");
        assertThat(ok(get(PROFILE, merchant.token()), 200).get("contactPhone").asString()).isEqualTo(before);
    }

    @Test
    void theDashboardAndOrdersListShowOnlyTheMerchantsOwnOrders() throws Exception {
        ParcelDetails waiting = order(merchant.merchantId(), "ORD-1001", "Nimal Perera", "Kandy");
        ParcelDetails collected = order(merchant.merchantId(), "ORD-1002", "Kamala Silva", "Galle");
        pickUp(collected);
        order(otherMerchant.merchantId(), "ORD-9999", "Someone Else", "Jaffna");

        JsonNode counts = ok(get(ORDERS + "/counts", merchant.token()), 200);
        assertThat(counts.get("CREATED").asLong()).isEqualTo(1);
        assertThat(counts.get("PICKED_UP").asLong()).isEqualTo(1);

        JsonNode page = ok(get(ORDERS + "?page=0&size=10", merchant.token()), 200);
        assertThat(page.get("page").get("totalElements").asLong()).isEqualTo(2);
        assertThat(trackingNumbers(page)).containsExactly(collected.trackingNumber(), waiting.trackingNumber());
        JsonNode row = page.get("content").get(1);
        assertThat(row.get("externalOrderReference").asString()).isEqualTo("ORD-1001");
        assertThat(row.get("recipientName").asString()).isEqualTo("Nimal Perera");
        assertThat(row.get("deliveryCity").asString()).isEqualTo("Kandy");
        assertThat(row.get("status").asString()).isEqualTo("CREATED");

        assertThat(trackingNumbers(ok(get(ORDERS + "?status=PICKED_UP", merchant.token()), 200)))
                .containsExactly(collected.trackingNumber());
        assertThat(trackingNumbers(ok(get(ORDERS + "?search=ORD-9999", merchant.token()), 200))).isEmpty();
    }

    @Test
    void orderDetailsShowTheAddressesAndTheStatusTimeline() throws Exception {
        ParcelDetails parcel = order(merchant.merchantId(), "ORD-2001", "Nimal Perera", "Kandy");
        pickUp(parcel);

        JsonNode details = ok(get(ORDERS + "/" + parcel.trackingNumber(), merchant.token()), 200);

        assertThat(details.get("externalOrderReference").asString()).isEqualTo("ORD-2001");
        assertThat(details.get("status").asString()).isEqualTo("PICKED_UP");
        assertThat(details.get("dropoffAddress").get("contactName").asString()).isEqualTo("Nimal Perera");
        assertThat(details.get("dropoffAddress").get("city").asString()).isEqualTo("Kandy");
        assertThat(details.get("pickupAddress").get("contactName").asString()).isEqualTo("BrightMart Warehouse");
        List<String> timeline = new ArrayList<>();
        details.get("timeline").forEach(entry -> timeline.add(entry.get("status").asString()));
        assertThat(timeline).containsExactly("CREATED", "PICKED_UP");
        assertThat(details.get("timeline").get(1).get("note").asString()).isEqualTo("Collected");
    }

    @Test
    void anotherMerchantsOrderIsNotFound() throws Exception {
        ParcelDetails theirs = order(otherMerchant.merchantId(), "ORD-3001", "Nimal Perera", "Kandy");

        assertError(get(ORDERS + "/" + theirs.trackingNumber(), merchant.token()), 404, "NOT_FOUND");
        assertError(get(ORDERS + "/" + theirs.trackingNumber() + "/receipt", merchant.token()), 404, "NOT_FOUND");
        assertError(get(ORDERS + "/SDNOSUCHORDER1", merchant.token()), 404, "NOT_FOUND");
        assertThat(get(ORDERS + "/" + theirs.trackingNumber(), otherMerchant.token()).statusCode()).isEqualTo(200);
    }

    @Test
    void onlyASignedInApprovedMerchantMayUseThePortal() throws Exception {
        assertThat(get(PROFILE).statusCode()).isEqualTo(401);

        UUID customer = userService.createUser(NewUser.withPassword("Kasun Customer", "customer-"
                + UUID.randomUUID() + "@test.swiftdrop.local", "+94771234567", "x", Role.CUSTOMER,
                AccountStatus.ACTIVE)).id();
        assertThat(get(PROFILE, tokenFor(customer, Role.CUSTOMER)).statusCode()).isEqualTo(403);

        assertError(get(ORDERS, pendingMerchant("Waiting Shop").token()), 403, "ACCOUNT_PENDING_APPROVAL");
        assertError(get(PROFILE, tokenFor(merchantUser(), Role.MERCHANT)), 403, "FORBIDDEN");

        setMerchantStatus(merchant.merchantId(), "REJECTED");
        assertError(get(ORDERS + "/counts", merchant.token()), 403, "ACCOUNT_DISABLED");
    }

    @Test
    void anOutOfRangePageIsRefused() throws Exception {
        assertError(get(ORDERS + "?size=101", merchant.token()), 400, "BAD_REQUEST");
        assertError(get(ORDERS + "?page=-1", merchant.token()), 400, "BAD_REQUEST");
    }
}
