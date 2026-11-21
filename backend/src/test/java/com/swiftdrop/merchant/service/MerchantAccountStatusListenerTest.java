package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.user.event.UserAccountStatusChangedEvent;
import com.swiftdrop.user.model.AccountStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantAccountStatusListenerTest {

    @Mock private MerchantRepository merchantRepository;
    @InjectMocks private MerchantAccountStatusListener listener;

    private final UUID userId = UUID.randomUUID();

    private Merchant merchant(MerchantApprovalStatus status) {
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setRegisteredByUserId(userId);
        merchant.setApprovalStatus(status);
        when(merchantRepository.findByRegisteredByUserId(userId)).thenReturn(Optional.of(merchant));
        return merchant;
    }

    private UserAccountStatusChangedEvent event(AccountStatus previous, AccountStatus next) {
        return new UserAccountStatusChangedEvent(userId, Role.MERCHANT, previous, next);
    }

    @ParameterizedTest(name = "{0} merchant, account {1} -> {2}: {3}")
    @CsvSource({
        // Suspending or deactivating the account suspends an approved merchant...
        "APPROVED,  ACTIVE,           SUSPENDED,        SUSPENDED",
        "APPROVED,  ACTIVE,           DEACTIVATED,      SUSPENDED",
        // ...and reactivating it approves the merchant again.
        "SUSPENDED, SUSPENDED,        ACTIVE,           APPROVED",
        "SUSPENDED, DEACTIVATED,      SUSPENDED,        SUSPENDED",
        // Merchant approval decides pending and rejected merchants, not User Management.
        "PENDING,   PENDING_APPROVAL, ACTIVE,           PENDING",
        "REJECTED,  PENDING_APPROVAL, DEACTIVATED,      REJECTED",
        "REJECTED,  DEACTIVATED,      ACTIVE,           REJECTED",
        "APPROVED,  PENDING_APPROVAL, ACTIVE,           APPROVED",
    })
    void merchantFollowsItsAccount(MerchantApprovalStatus current, AccountStatus previous, AccountStatus next,
                                   MerchantApprovalStatus expected) {
        Merchant merchant = merchant(current);

        listener.onAccountStatusChanged(event(previous, next));

        assertThat(merchant.getApprovalStatus()).isEqualTo(expected);
    }

    @Test
    void otherRolesAreIgnored() {
        listener.onAccountStatusChanged(
                new UserAccountStatusChangedEvent(userId, Role.CUSTOMER, AccountStatus.ACTIVE, AccountStatus.SUSPENDED));

        verifyNoInteractions(merchantRepository);
    }

    @Test
    void merchantUserWithoutAMerchantIsIgnored() {
        when(merchantRepository.findByRegisteredByUserId(userId)).thenReturn(Optional.empty());

        listener.onAccountStatusChanged(event(AccountStatus.ACTIVE, AccountStatus.SUSPENDED));
    }
}
