package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.common.exception.ForbiddenException;
import com.swiftdrop.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantAccessServiceTest {

    @Mock private MerchantRepository merchantRepository;

    private MerchantAccessService merchantAccessService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        merchantAccessService = new MerchantAccessService(merchantRepository);
    }

    private Merchant merchantWithStatus(MerchantApprovalStatus status) {
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setRegisteredByUserId(userId);
        merchant.setApprovalStatus(status);
        when(merchantRepository.findByRegisteredByUserId(userId)).thenReturn(Optional.of(merchant));
        return merchant;
    }

    @Test
    void approvedMerchantGetsItsOwnMerchantId() {
        Merchant merchant = merchantWithStatus(MerchantApprovalStatus.APPROVED);

        assertThat(merchantAccessService.requireApprovedMerchant(userId)).isEqualTo(merchant.getId());
    }

    @Test
    void pendingMerchantIsBlockedAsAwaitingApproval() {
        merchantWithStatus(MerchantApprovalStatus.PENDING);

        assertThatThrownBy(() -> merchantAccessService.requireApprovedMerchant(userId))
                .isInstanceOfSatisfying(MerchantNotApprovedException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_PENDING_APPROVAL));
    }

    @ParameterizedTest
    @EnumSource(value = MerchantApprovalStatus.class, names = {"REJECTED", "SUSPENDED"})
    void rejectedAndSuspendedMerchantsAreBlockedWithTheSameMessage(MerchantApprovalStatus status) {
        merchantWithStatus(status);

        assertThatThrownBy(() -> merchantAccessService.requireApprovedMerchant(userId))
                .isInstanceOfSatisfying(MerchantNotApprovedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED);
                    assertThat(e.getMessage()).isEqualTo("This merchant account is not active. Please contact support.");
                });
    }

    @Test
    void userWithoutAMerchantAccountIsForbidden() {
        when(merchantRepository.findByRegisteredByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantAccessService.requireApprovedMerchant(userId))
                .isInstanceOf(ForbiddenException.class);
    }
}
