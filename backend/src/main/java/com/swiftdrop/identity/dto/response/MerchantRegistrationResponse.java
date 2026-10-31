package com.swiftdrop.identity.dto.response;

import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import java.time.Instant;
import java.util.UUID;

public record MerchantRegistrationResponse(
        UUID merchantId,
        String businessName,
        String registrationNo,
        MerchantApprovalStatus approvalStatus,
        Instant submittedAt) {
}
