package com.swiftdrop.merchant.dto;

import java.util.UUID;

/** Data needed to register a merchant business for approval. */
public record NewMerchant(
        UUID registeredByUserId,
        String businessName,
        String registrationNo,
        String contactEmail,
        String contactPhone,
        String businessAddress) {
}
