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
import com.swiftdrop.merchant.service.MerchantApiKeyService;
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
class MerchantApiKeyControllerTest {

    @Mock private MerchantAccessService merchantAccessService;
    @Mock private MerchantApiKeyService apiKeyService;
    @InjectMocks private MerchantApiKeyController controller;

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(userId.toString(), Set.of(Role.MERCHANT));

    @Test
    void onlyMerchantsMayUseIt() {
        assertThat(MerchantApiKeyController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('MERCHANT')");
    }

    @Test
    void everyCallIsScopedToTheSignedInUsersMerchant() {
        UUID keyId = UUID.randomUUID();
        when(merchantAccessService.requireApprovedMerchant(userId)).thenReturn(merchantId);
        when(apiKeyService.list(merchantId)).thenReturn(List.of());

        controller.list(user);
        controller.generate(user);
        controller.revoke(keyId, user);
        controller.rotate(keyId, user);

        verify(apiKeyService).list(merchantId);
        verify(apiKeyService).generate(merchantId);
        verify(apiKeyService).revoke(merchantId, keyId);
        verify(apiKeyService).rotate(merchantId, keyId);
    }

    @Test
    void merchantThatIsNotApprovedCannotManageKeys() {
        when(merchantAccessService.requireApprovedMerchant(userId))
                .thenThrow(MerchantNotApprovedException.forStatus(MerchantApprovalStatus.SUSPENDED));

        assertThatThrownBy(() -> controller.generate(user)).isInstanceOf(MerchantNotApprovedException.class);
        verifyNoInteractions(apiKeyService);
    }
}
