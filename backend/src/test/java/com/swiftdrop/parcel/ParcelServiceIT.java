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
import com.swiftdrop.parcel.model.ParcelType;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        return new NewParcel(customerId, null, null, address("Colombo"), address("Kandy"), ParcelType.FRAGILE,
                new BigDecimal("2.500"), null, null, null, "Glassware", null, null, new BigDecimal("450.00"));
    }

    @Test
    void statusTableHoldsTheSevenStatusesInOrder() {
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

    private static NewParcel merchantOrder(UUID merchantId, String reference) {
        return new NewParcel(null, merchantId, reference, address("Colombo"), address("Galle"),
                ParcelType.ELECTRONICS, BigDecimal.ONE, null, null, null, null, null, null, new BigDecimal("350.00"));
    }

    @Test
    void merchantOrderReferenceMustBeUniquePerMerchant() {
        UUID merchantId = newMerchant();
        NewParcel order = merchantOrder(merchantId, "ORD-1001");

        ParcelDetails created = parcelService.createParcel(order);

        assertThatThrownBy(() -> parcelService.createParcel(order)).isInstanceOf(ConflictException.class);
        assertThat(parcelService.listByMerchant(merchantId, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(parcelService.findByMerchantOrderReference(merchantId, "ORD-1001"))
                .get().extracting(ParcelDetails::id).isEqualTo(created.id());
        assertThat(parcelService.findByMerchantOrderReference(newMerchant(), "ORD-1001")).isEmpty();
    }

    @Test
    void parcelTypeIsStoredAndTheDatabaseRejectsUnknownTypes() {
        ParcelDetails created = parcelService.createParcel(customerParcel(newCustomer()));

        assertThat(parcelService.getParcel(created.id()).parcelType()).isEqualTo(ParcelType.FRAGILE);
        assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET parcel_type = 'BOX' WHERE parcel_id = ?",
                created.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsSeveralParcelsByIdInOneCall() {
        UUID customerId = newCustomer();
        ParcelDetails first = parcelService.createParcel(customerParcel(customerId));
        ParcelDetails second = parcelService.createParcel(customerParcel(customerId));

        assertThat(parcelService.findAllByIds(List.of(first.id(), second.id(), UUID.randomUUID())))
                .extracting(ParcelDetails::id).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void listsAndCountsParcelsByStatus() {
        UUID merchantId = newMerchant();
        ParcelDetails created = parcelService.createParcel(merchantOrder(merchantId, "ORD-2001"));
        ParcelDetails delivered = parcelService.createParcel(merchantOrder(merchantId, "ORD-2002"));
        // StatusApi will move parcels along; until then set the status directly.
        jdbc.update("UPDATE parcels SET current_status_code = 'DELIVERED' WHERE parcel_id = ?", delivered.id());

        Map<ParcelStatusCode, Long> merchantCounts = parcelService.countByStatusForMerchant(merchantId);
        assertThat(merchantCounts).containsEntry(ParcelStatusCode.CREATED, 1L)
                .containsEntry(ParcelStatusCode.DELIVERED, 1L)
                .containsEntry(ParcelStatusCode.FAILED, 0L);

        assertThat(parcelService.countByStatus().get(ParcelStatusCode.DELIVERED)).isGreaterThanOrEqualTo(1L);

        List<UUID> deliveredIds = parcelService.listByStatus(ParcelStatusCode.DELIVERED, PageRequest.of(0, 100))
                .map(ParcelDetails::id).getContent();
        assertThat(deliveredIds).contains(delivered.id()).doesNotContain(created.id());
    }
}
