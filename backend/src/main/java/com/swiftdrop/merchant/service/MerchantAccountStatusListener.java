package com.swiftdrop.merchant.service;

import com.swiftdrop.common.security.Role;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.user.event.UserAccountStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps an approved merchant's status in step with its contact person's account. When the admin
 * suspends or deactivates that account (User Management), the merchant becomes SUSPENDED, so the
 * portal and the order API (API keys and OAuth tokens) refuse it straight away with
 * {@code ACCOUNT_DISABLED}, instead of working until the signed-in user's access token expires.
 * Reactivating the account makes the merchant APPROVED again.
 *
 * <p>Pending and rejected merchants are decided through merchant approval and are left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantAccountStatusListener {

    private final MerchantRepository merchantRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountStatusChanged(UserAccountStatusChangedEvent event) {
        if (event.role() != Role.MERCHANT) {
            return;
        }
        merchantRepository.findByRegisteredByUserId(event.userId()).ifPresent(merchant -> {
            MerchantApprovalStatus next = nextStatus(merchant.getApprovalStatus(), event);
            if (next != merchant.getApprovalStatus()) {
                log.info("Merchant {} changed from {} to {} because its account is now {}", merchant.getId(),
                        merchant.getApprovalStatus(), next, event.newStatus());
                merchant.setApprovalStatus(next);
            }
        });
    }

    /** The merchant status that matches the account's new status. */
    static MerchantApprovalStatus nextStatus(MerchantApprovalStatus current, UserAccountStatusChangedEvent event) {
        return switch (event.newStatus()) {
            case SUSPENDED, DEACTIVATED ->
                    current == MerchantApprovalStatus.APPROVED ? MerchantApprovalStatus.SUSPENDED : current;
            case ACTIVE -> current == MerchantApprovalStatus.SUSPENDED ? MerchantApprovalStatus.APPROVED : current;
            case PENDING_APPROVAL -> current;
        };
    }
}
