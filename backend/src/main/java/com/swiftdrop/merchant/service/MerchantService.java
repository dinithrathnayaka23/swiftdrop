package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.NewMerchant;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.notification.service.EmailService;
import com.swiftdrop.user.model.AccountStatus;
import com.swiftdrop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant registration and approval. A merchant's contact person signs in with a MERCHANT user
 * whose account stays PENDING_APPROVAL until an admin approves the business.
 */
@Slf4j
@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public MerchantService(MerchantRepository merchantRepository, UserService userService,
                           EmailService emailService,
                           @Value("${swiftdrop.frontend.base-url}") String frontendBaseUrl) {
        this.merchantRepository = merchantRepository;
        this.userService = userService;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Standard stored form: upper case without spaces or hyphens ("pv 00234567" → "PV00234567"). */
    public static String normalizeRegistrationNo(String registrationNo) {
        return registrationNo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public boolean registrationNoExists(String registrationNo) {
        return merchantRepository.existsByRegistrationNo(normalizeRegistrationNo(registrationNo));
    }

    /** Creates a PENDING merchant. Runs inside the caller's registration transaction. */
    @Transactional
    public MerchantResponse registerPending(NewMerchant newMerchant) {
        Merchant merchant = new Merchant();
        merchant.setRegisteredByUserId(newMerchant.registeredByUserId());
        merchant.setBusinessName(newMerchant.businessName().trim());
        merchant.setRegistrationNo(normalizeRegistrationNo(newMerchant.registrationNo()));
        merchant.setContactEmail(UserService.normalizeEmail(newMerchant.contactEmail()));
        merchant.setContactPhone(newMerchant.contactPhone());
        merchant.setBusinessAddress(newMerchant.businessAddress().trim());
        merchant.setApprovalStatus(MerchantApprovalStatus.PENDING);
        Merchant saved = merchantRepository.saveAndFlush(merchant);
        log.info("Merchant {} registered, pending approval", saved.getId());
        return MerchantMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> listByStatus(MerchantApprovalStatus status) {
        return merchantRepository.findByApprovalStatusOrderByCreatedAtAsc(status).stream()
                .map(MerchantMapper::toResponse)
                .toList();
    }

    /** Approves a pending merchant and activates its contact person's account. */
    @Transactional
    public MerchantResponse approve(UUID merchantId, UUID adminUserId) {
        Merchant merchant = loadPending(merchantId);
        merchant.setApprovalStatus(MerchantApprovalStatus.APPROVED);
        merchant.setReviewedByUserId(adminUserId);
        merchant.setReviewedAt(Instant.now());
        merchant.setRejectionReason(null);
        userService.changeAccountStatus(merchant.getRegisteredByUserId(), AccountStatus.ACTIVE);
        log.info("Merchant {} approved by {}", merchant.getId(), adminUserId);

        emailService.send(merchant.getContactEmail(), "Your SwiftDrop merchant account is approved",
                """
                Good news! %s has been approved on SwiftDrop.

                You can now sign in and start shipping: %s/login

                The SwiftDrop team""".formatted(merchant.getBusinessName(), frontendBaseUrl));
        return MerchantMapper.toResponse(merchant);
    }

    /** Rejects a pending merchant; its contact person's account is deactivated. */
    @Transactional
    public MerchantResponse reject(UUID merchantId, UUID adminUserId, String reason) {
        Merchant merchant = loadPending(merchantId);
        merchant.setApprovalStatus(MerchantApprovalStatus.REJECTED);
        merchant.setReviewedByUserId(adminUserId);
        merchant.setReviewedAt(Instant.now());
        merchant.setRejectionReason(reason.trim());
        userService.changeAccountStatus(merchant.getRegisteredByUserId(), AccountStatus.DEACTIVATED);
        log.info("Merchant {} rejected by {}", merchant.getId(), adminUserId);

        emailService.send(merchant.getContactEmail(), "Update on your SwiftDrop merchant registration",
                """
                Thank you for registering %s with SwiftDrop.

                Unfortunately we could not approve your registration.
                Reason: %s

                If you have questions, reply to this email.

                The SwiftDrop team""".formatted(merchant.getBusinessName(), merchant.getRejectionReason()));
        return MerchantMapper.toResponse(merchant);
    }

    private Merchant loadPending(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
        if (merchant.getApprovalStatus() != MerchantApprovalStatus.PENDING) {
            throw new ConflictException("Only pending merchants can be reviewed");
        }
        return merchant;
    }
}
