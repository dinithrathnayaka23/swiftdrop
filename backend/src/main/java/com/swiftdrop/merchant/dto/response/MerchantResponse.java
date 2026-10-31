package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import java.time.Instant;
import java.util.UUID;

/** Merchant details for admins reviewing registrations. */
public record MerchantResponse(
        UUID merchantId,
        String businessName,
        String registrationNo,
        String contactEmail,
        String contactPhone,
        String businessAddress,
        MerchantApprovalStatus approvalStatus,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason) {
}
