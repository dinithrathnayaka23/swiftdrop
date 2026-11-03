package com.swiftdrop.parcel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/** ParcelService against the real schema: proves the V4_001 statuses and the parcel entity mapping. */
class ParcelServiceIT extends IntegrationTestBase {

    @Autowired private ParcelService parcelService;
    @Autowired private UserService userService;
    @Autowired private MerchantService merchantService;
    @Autowired private JdbcTemplate jdbc;

    private UUID newCustomer() {
        String email = "customer-" + UUID.randomUUID() + "@test.swiftdrop.local";
        return userService.createUser(NewUser.withPassword("Nimal Perera", email, "+94771234567", "x",
                Role.CUSTOMER, AccountStatus.ACTIVE)).id();
    }

    private UUID newMerchant() {
        String email = "merchant-" + UUID.randomUUID() + "@test.swiftdrop.local";
        UUID contactPerson = userService.createUser(NewUser.withPassword("Sunil Fernando", email, "+94771234567",
                "x", Role.MERCHANT, AccountStatus.ACTIVE)).id();
        String registrationNo = "PV" + UUID.randomUUID().toString().substring(0, 8);
        return merchantService.registerPending(new NewMerchant(contactPerson, "BrightMart", registrationNo, email,
                "+94771234567", "1 Main Street, Colombo")).merchantId();
    }

    private static NewAddress address(String city) {
        return new NewAddress("Kamala Silva", "+94771234567", "12 Galle Road", null, city, "Colombo", null, null);
    }

    private static NewParcel customerParcel(UUID customerId) {
        return new NewParcel(customerId, null, null, address("Colombo"), address("Kandy"), new BigDecimal("2.500"),
                null, null, null, "Books", null, null, new BigDecimal("450.00"));
    }

    @Test
    void statusTableHoldsTheSixReportStatusesInOrder() {
        List<String> codes = jdbc.queryForList(
                "SELECT status_code FROM parcel_statuses ORDER BY display_order", String.class);
        assertThat(codes).containsExactlyElementsOf(
                Arrays.stream(ParcelStatusCode.values()).map(Enum::name).toList());

        List<String> terminal = jdbc.queryForList(
                "SELECT status_code FROM parcel_statuses WHERE is_terminal ORDER BY display_order", String.class);
        assertThat(terminal).containsExactly("DELIVERED", "FAILED");
    }

    @Test
    void createdParcelIsSavedWithItsFirstHistoryEntryAndCanBeFound() {
        UUID customerId = newCustomer();

        ParcelDetails created = parcelService.createParcel(customerParcel(customerId));

        assertThat(created.id()).isNotNull();
        assertThat(created.currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(created.trackingNumber()).matches("SD[2-9A-HJKMNP-Z]{12}");
        assertThat(created.createdAt()).isNotNull();

        Map<String, Object> history = jdbc.queryForMap(
                "SELECT previous_status_code, status_code, changed_by_user_id FROM parcel_status_history "
                        + "WHERE parcel_id = ?", created.id());
        assertThat(history.get("previous_status_code")).isNull();
        assertThat(history.get("status_code")).isEqualTo("CREATED");
        assertThat(history.get("changed_by_user_id")).isEqualTo(customerId);

        ParcelDetails byId = parcelService.getParcel(created.id());
        assertThat(byId.dropoffAddress().city()).isEqualTo("Kandy");
        assertThat(byId.weightKg()).isEqualByComparingTo("2.5");

        assertThat(parcelService.findByTrackingNumber(created.trackingNumber().toLowerCase()))
                .get().extracting(ParcelDetails::id).isEqualTo(created.id());
    }

    @Test
    void listsOnlyTheCustomersOwnParcelsNewestFirst() {
        UUID customerId = newCustomer();
        ParcelDetails older = parcelService.createParcel(customerParcel(customerId));
        ParcelDetails newer = parcelService.createParcel(customerParcel(customerId));
        // Two quick inserts can share a timestamp; make the order certain.
        jdbc.update("UPDATE parcels SET created_at = now() - interval '1 hour' WHERE parcel_id = ?", older.id());
        parcelService.createParcel(customerParcel(newCustomer()));

        Page<ParcelDetails> page = parcelService.listByCustomer(customerId, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(ParcelDetails::id).containsExactly(newer.id(), older.id());
    }

    @Test
    void merchantOrderReferenceMustBeUniquePerMerchant() {
        UUID merchantId = newMerchant();
        NewParcel order = new NewParcel(null, merchantId, "ORD-1001", address("Colombo"), address("Galle"),
                BigDecimal.ONE, null, null, null, null, null, null, new BigDecimal("350.00"));

        parcelService.createParcel(order);

        assertThatThrownBy(() -> parcelService.createParcel(order)).isInstanceOf(ConflictException.class);
        assertThat(parcelService.listByMerchant(merchantId, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }
}
