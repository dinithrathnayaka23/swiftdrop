package com.swiftdrop.parcel;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.IntegrationTestBase;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.request.AdminParcelFilter;
import com.swiftdrop.parcel.dto.request.AdminParcelTab;
import com.swiftdrop.parcel.dto.response.AdminParcelRowResponse;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.DispatchStateLookup;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelViewService;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** The admin Parcels screen once the dispatch module provides a {@link DispatchStateLookup}. */
@Import(DispatchStateLookupIT.FakeDispatch.class)
class DispatchStateLookupIT extends IntegrationTestBase {

    /** Stands in for the dispatch module's bean. */
    @TestConfiguration
    static class FakeDispatch {
        static final Set<UUID> OPEN_ASSIGNMENTS = ConcurrentHashMap.newKeySet();

        @Bean
        DispatchStateLookup dispatchStateLookup() {
            return () -> Set.copyOf(OPEN_ASSIGNMENTS);
        }
    }

    @Autowired private ParcelService parcelService;
    @Autowired private ParcelViewService parcelViewService;
    @Autowired private UserService userService;

    private ParcelDetails parcelTo(String city) {
        UUID customer = userService.createUser(NewUser.withPassword("Ayesha Khan",
                "customer-" + UUID.randomUUID() + "@test.swiftdrop.local", "+94771234567", "x", Role.CUSTOMER,
                AccountStatus.ACTIVE)).id();
        NewAddress address = new NewAddress("Kasun Perera", "+94771234567", "12 Galle Road", null, city, "Western",
                null, null);
        return parcelService.createParcel(new NewParcel(customer, null, null, address, address, ParcelType.PARCEL,
                BigDecimal.ONE, null, null, null, null, null, null, BigDecimal.TEN));
    }

    private List<AdminParcelRowResponse> rows(AdminParcelTab tab, String city) {
        return parcelViewService.listForAdmin(new AdminParcelFilter(tab, null, null, city, null, null), 0, 100)
                .getContent();
    }

    @Test
    void createdParcelsSplitIntoAssignedAndUnassigned() {
        String city = "Town " + UUID.randomUUID();
        ParcelDetails assigned = parcelTo(city);
        ParcelDetails waiting = parcelTo(city);
        FakeDispatch.OPEN_ASSIGNMENTS.add(assigned.id());

        assertThat(rows(AdminParcelTab.ASSIGNED, city)).extracting(AdminParcelRowResponse::id)
                .containsExactly(assigned.id());
        assertThat(rows(AdminParcelTab.UNASSIGNED, city)).extracting(AdminParcelRowResponse::id)
                .containsExactly(waiting.id());
        assertThat(rows(AdminParcelTab.ALL, city)).extracting(AdminParcelRowResponse::dispatchState)
                .containsExactlyInAnyOrder("ASSIGNED", "UNASSIGNED");
        assertThat(parcelViewService.getForAdmin(assigned.id()).dispatchState()).isEqualTo("ASSIGNED");

        Map<AdminParcelTab, Long> counts = parcelViewService.adminCounts();
        assertThat(counts.get(AdminParcelTab.ASSIGNED)).isGreaterThanOrEqualTo(1);
        assertThat(counts.get(AdminParcelTab.UNASSIGNED) + counts.get(AdminParcelTab.ASSIGNED)
                + counts.get(AdminParcelTab.IN_TRANSIT) + counts.get(AdminParcelTab.DELIVERED)
                + counts.get(AdminParcelTab.FAILED)).isEqualTo(counts.get(AdminParcelTab.ALL));
    }
}
