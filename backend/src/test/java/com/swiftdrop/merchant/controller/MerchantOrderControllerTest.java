package com.swiftdrop.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantAccessService;
import com.swiftdrop.merchant.service.MerchantOrderService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class MerchantOrderControllerTest {

    @Mock private MerchantAccessService merchantAccessService;
    @Mock private MerchantOrderService orderService;
    @InjectMocks private MerchantOrderController controller;

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.MERCHANT));

    @Test
    void onlyMerchantsMayUseIt() {
        assertThat(MerchantOrderController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('MERCHANT')");
    }

    @Test
    void everyCallIsScopedToTheSignedInUsersMerchant() {
        when(merchantAccessService.requireApprovedMerchant(userId)).thenReturn(merchantId);
        when(orderService.list(merchantId, 2, 25)).thenReturn(Page.empty());

        controller.counts(user);
        controller.list(user, 2, 25);
        controller.get(user, "SD-20261112-ABC123");

        verify(orderService).counts(merchantId);
        verify(orderService).list(merchantId, 2, 25);
        verify(orderService).get(merchantId, "SD-20261112-ABC123");
    }

    @Test
    void merchantThatIsNotApprovedCannotSeeOrders() {
        when(merchantAccessService.requireApprovedMerchant(userId))
                .thenThrow(MerchantNotApprovedException.forStatus(MerchantApprovalStatus.SUSPENDED));

        assertThatThrownBy(() -> controller.list(user, 0, 10)).isInstanceOf(MerchantNotApprovedException.class);
        verifyNoInteractions(orderService);
    }
}
