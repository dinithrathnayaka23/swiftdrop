package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.ForbiddenException;
import com.swiftdrop.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "approved only" gate for the merchant portal and the merchant order API. Checked on every
 * request, not just at sign-in, because a JWT issued before a merchant was suspended stays valid
 * until it expires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAccessService {

    private final MerchantRepository merchantRepository;

    /**
     * Returns the ID of the merchant the user registered, if it is APPROVED. Callers scope every
     * query to this ID so a merchant only ever sees its own data.
     */
    @Transactional(readOnly = true)
    public UUID requireApprovedMerchant(UUID userId) {
        Merchant merchant = merchantRepository.findByRegisteredByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("User {} has no merchant account", userId);
                    return new ForbiddenException("No merchant account is linked to this user");
                });
        if (merchant.getApprovalStatus() != MerchantApprovalStatus.APPROVED) {
            log.info("Merchant {} blocked: status {}", merchant.getId(), merchant.getApprovalStatus());
            throw MerchantNotApprovedException.forStatus(merchant.getApprovalStatus());
        }
        return merchant.getId();
    }
}
