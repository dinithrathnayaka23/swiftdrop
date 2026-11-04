package com.swiftdrop.parcel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.parcel.dto.request.AdminParcelFilter;
import com.swiftdrop.parcel.dto.request.AdminParcelTab;
import com.swiftdrop.parcel.dto.request.CustomerParcelFilter;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelViewService;
import com.swiftdrop.parcel.service.ReceiptService;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class ParcelControllersTest {

    @Mock private ParcelViewService parcelViewService;
    @Mock private ReceiptService receiptService;

    private final UUID customerId = UUID.randomUUID();
    private final AuthenticatedUser customer = new AuthenticatedUser(customerId.toString(), Set.of(Role.CUSTOMER));

    @Test
    void customerEndpointsAreForCustomersAndAdminEndpointsForAdmins() {
        assertThat(CustomerParcelController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('CUSTOMER')");
        assertThat(AdminParcelController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void customerListUsesTheSignedInCustomerAndTheFilters() {
        CustomerParcelController controller = new CustomerParcelController(parcelViewService, receiptService);
        CustomerParcelFilter filter = new CustomerParcelFilter("kandy", ParcelStatusCode.DELIVERED,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(parcelViewService.listForCustomer(customerId, filter, 2, 10)).thenReturn(Page.empty(PageRequest.of(2, 10)));

        var page = controller.list(customer, "kandy", ParcelStatusCode.DELIVERED, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), 2, 10);

        assertThat(page.getMetadata().number()).isEqualTo(2);
    }

    @Test
    void customerDetailAndCountsUseTheSignedInCustomer() {
        CustomerParcelController controller = new CustomerParcelController(parcelViewService, receiptService);

        controller.get(customer, "SD7KQ2MX9TRB4H");
        controller.counts(customer);

        verify(parcelViewService).getForCustomer(customerId, "SD7KQ2MX9TRB4H");
        verify(parcelViewService).customerCounts(customerId);
    }

    @Test
    void adminListPassesTheFilters() {
        AdminParcelController controller = new AdminParcelController(parcelViewService);
        AdminParcelFilter filter = new AdminParcelFilter(AdminParcelTab.FAILED, "kasun", null, "Colombo", null, null);
        when(parcelViewService.listForAdmin(filter, 0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));

        assertThat(controller.list(AdminParcelTab.FAILED, "kasun", null, "Colombo", null, null, 0, 20).getContent())
                .isEmpty();
    }

    @Test
    void rejectsANegativePageAndAnOversizedPage() {
        CustomerParcelController controller = new CustomerParcelController(parcelViewService, receiptService);

        assertThatThrownBy(() -> controller.list(customer, null, null, null, null, -1, 10))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> controller.list(customer, null, null, null, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new AdminParcelController(parcelViewService)
                .list(AdminParcelTab.ALL, null, null, null, null, null, 0, 0))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcelViewService);
    }
}
