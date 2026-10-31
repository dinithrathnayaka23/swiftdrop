package com.swiftdrop.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.merchant.dto.request.RejectMerchantRequest;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class AdminMerchantControllerTest {

    @Mock private MerchantService merchantService;
    @InjectMocks private AdminMerchantController controller;

    private final UUID adminId = UUID.randomUUID();
    private final AuthenticatedUser admin = new AuthenticatedUser(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));

    @Test
    void onlyDispatcherAdminsMayUseIt() {
        assertThat(AdminMerchantController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void listsByStatus() {
        when(merchantService.listByStatus(MerchantApprovalStatus.PENDING)).thenReturn(List.of());

        assertThat(controller.list(MerchantApprovalStatus.PENDING)).isEmpty();
    }

    @Test
    void approveRecordsTheSignedInAdmin() {
        UUID merchantId = UUID.randomUUID();

        controller.approve(merchantId, admin);

        verify(merchantService).approve(merchantId, adminId);
    }

    @Test
    void rejectPassesTheReason() {
        UUID merchantId = UUID.randomUUID();
        MerchantResponse rejected = new MerchantResponse(merchantId, "BrightMart", "PV00234567", "a@b.lk", "+94",
                "addr", MerchantApprovalStatus.REJECTED, null, null, "Unverified");
        when(merchantService.reject(merchantId, adminId, "Unverified")).thenReturn(rejected);

        assertThat(controller.reject(merchantId, new RejectMerchantRequest("Unverified"), admin)).isEqualTo(rejected);
    }
}
