package com.swiftdrop.merchant.api;

import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import java.util.UUID;

/**
 * A merchant as other modules see it. Leaves out review details (reviewer, rejection reason), which
 * only the admin screens need.
 *
 * @param ownerUserId the MERCHANT user who registered the business
 */
public record MerchantSummary(
        UUID id,
        String businessName,
        String contactEmail,
        String contactPhone,
        MerchantApprovalStatus approvalStatus,
        UUID ownerUserId) {

    public boolean approved() {
        return approvalStatus == MerchantApprovalStatus.APPROVED;
    }
}
