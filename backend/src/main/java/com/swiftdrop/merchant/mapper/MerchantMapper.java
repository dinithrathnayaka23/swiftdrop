package com.swiftdrop.merchant.mapper;

import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.Merchant;

public final class MerchantMapper {

    private MerchantMapper() {
    }

    public static MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(merchant.getId(), merchant.getBusinessName(), merchant.getRegistrationNo(),
                merchant.getContactEmail(), merchant.getContactPhone(), merchant.getBusinessAddress(),
                merchant.getApprovalStatus(), merchant.getCreatedAt(), merchant.getReviewedAt(),
                merchant.getRejectionReason());
    }
}
