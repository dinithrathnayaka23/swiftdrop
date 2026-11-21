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
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Merchant receipt downloads over HTTP, against the real database. */
class MerchantOrderReceiptIT extends IntegrationTestBase {

    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private MerchantService merchantService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;

    private UUID merchantId;
    private String token;

    @BeforeEach
    void signInAsApprovedMerchant() {
        UUID userId = newUser(Role.MERCHANT);
        merchantId = newMerchant(userId);
        jdbc.update("UPDATE merchants SET approval_status = 'APPROVED' WHERE merchant_id = ?", merchantId);
        token = jwtService.generateToken(userId.toString(), Set.of(Role.MERCHANT));
    }

    private UUID newUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.swiftdrop.local";
        return userService.createUser(NewUser.withPassword("Sunil Fernando", email, "+94771234567", "x", role,
                AccountStatus.ACTIVE)).id();
    }

    private UUID newMerchant(UUID contactPerson) {
        return merchantService.registerPending(new NewMerchant(contactPerson, "BrightMart",
                "PV" + UUID.randomUUID().toString().substring(0, 8),
                "shop-" + UUID.randomUUID() + "@test.swiftdrop.local", "+94771234567",
                "1 Main Street, Colombo")).merchantId();
    }

    private static NewAddress address(String contactName, String city) {
        return new NewAddress(contactName, "+94771234567", "12 Galle Road", null, city, "Western", null, null);
    }

    private ParcelDetails order(UUID owner) {
        return parcelService.createParcel(new NewParcel(null, owner, "ORD-" + UUID.randomUUID(),
                address("BrightMart Warehouse", "Colombo"), address("Nimal Perera", "Kandy"), ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, "Shoes", null, null, new BigDecimal("450.00")));
    }

    private ParcelDetails customerBooking() {
        return parcelService.createParcel(new NewParcel(newUser(Role.CUSTOMER), null, null,
                address("Ayesha Khan", "Colombo"), address("Hassan Ali", "Galle"), ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, "Books", null, null, new BigDecimal("750.00")));
    }

    private HttpResponse<String> receipt(String trackingNumber) throws Exception {
        return get("/api/v1/merchant/orders/" + trackingNumber + "/receipt", token);
    }

    private long receiptsRecorded(ParcelDetails parcel) {
        return jdbc.queryForObject("SELECT count(*) FROM delivery_receipts WHERE parcel_id = ?", Long.class,
                parcel.id());
    }

    @Test
    void merchantDownloadsTheReceiptOfTheirOwnOrderAndItIsRecordedOnce() throws Exception {
        ParcelDetails mine = order(merchantId);

        HttpResponse<String> first = receipt(mine.trackingNumber().toLowerCase());
        HttpResponse<String> second = receipt(mine.trackingNumber());

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(first.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("attachment", "receipt-" + mine.trackingNumber() + ".pdf");
        assertThat(first.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(first.body()).startsWith("%PDF-");
        assertThat(second.statusCode()).isEqualTo(200);

        Map<String, Object> recorded = jdbc.queryForMap(
                "SELECT receipt_number, document_url FROM delivery_receipts WHERE parcel_id = ?", mine.id());
        assertThat(recorded.get("receipt_number")).isEqualTo("SDR-" + mine.trackingNumber().substring(2));
        assertThat(recorded.get("document_url"))
                .isEqualTo("/api/v1/merchant/orders/" + mine.trackingNumber() + "/receipt");
        assertThat(receiptsRecorded(mine)).isEqualTo(1);
    }

    @Test
    void anotherMerchantsOrderIsNotFound() throws Exception {
        ParcelDetails theirs = order(newMerchant(newUser(Role.MERCHANT)));

        assertThat(receipt(theirs.trackingNumber()).statusCode()).isEqualTo(404);
        assertThat(receiptsRecorded(theirs)).isZero();
    }

    @Test
    void customerBookingIsNotFound() throws Exception {
        ParcelDetails booking = customerBooking();

        assertThat(receipt(booking.trackingNumber()).statusCode()).isEqualTo(404);
        assertThat(receiptsRecorded(booking)).isZero();
    }

    @Test
    void unknownTrackingNumberIsNotFound() throws Exception {
        assertThat(receipt("SDNOSUCHORDER1").statusCode()).isEqualTo(404);
    }

    @Test
    void merchantAwaitingApprovalCannotDownloadReceipts() throws Exception {
        ParcelDetails mine = order(merchantId);
        jdbc.update("UPDATE merchants SET approval_status = 'PENDING' WHERE merchant_id = ?", merchantId);

        assertThat(receipt(mine.trackingNumber()).statusCode()).isEqualTo(403);
        assertThat(receiptsRecorded(mine)).isZero();
    }

    @Test
    void customerCannotUseTheMerchantEndpoint() throws Exception {
        ParcelDetails mine = order(merchantId);
        String customerToken = jwtService.generateToken(newUser(Role.CUSTOMER).toString(), Set.of(Role.CUSTOMER));

        assertThat(get("/api/v1/merchant/orders/" + mine.trackingNumber() + "/receipt", customerToken)
                .statusCode()).isEqualTo(403);
    }
}
