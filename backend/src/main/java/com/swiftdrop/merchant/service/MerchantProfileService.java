package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.request.UpdateMerchantProfileRequest;
import com.swiftdrop.merchant.dto.response.MerchantProfileResponse;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The merchant's own business profile. Callers pass the ID returned by
 * {@link MerchantAccessService#requireApprovedMerchant}, so a merchant only reaches its own profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantProfileService {

    private final MerchantRepository merchantRepository;

    @Transactional(readOnly = true)
    public MerchantProfileResponse get(UUID merchantId) {
        return MerchantMapper.toProfileResponse(load(merchantId));
    }

    /** Updates the contact details, stored in the same form as at registration. */
    @Transactional
    public MerchantProfileResponse update(UUID merchantId, UpdateMerchantProfileRequest request) {
        Merchant merchant = load(merchantId);
        merchant.setContactEmail(UserService.normalizeEmail(request.contactEmail()));
        merchant.setContactPhone(request.contactPhone());
        merchant.setBusinessAddress(request.businessAddress().trim());
        // Flush so updatedAt in the response is the new value.
        Merchant saved = merchantRepository.saveAndFlush(merchant);
        log.info("Merchant {} updated its profile", merchantId);
        return MerchantMapper.toProfileResponse(saved);
    }

    private Merchant load(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
    }
}
