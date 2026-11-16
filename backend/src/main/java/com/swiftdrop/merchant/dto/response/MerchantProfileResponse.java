package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import java.time.Instant;
import java.util.UUID;

/** The business profile a merchant sees in the portal. */
public record MerchantProfileResponse(
        UUID merchantId,
        String businessName,
        String registrationNo,
        String contactEmail,
        String contactPhone,
        String businessAddress,
        MerchantApprovalStatus approvalStatus,
        Instant approvedAt,
        Instant updatedAt) {
}
