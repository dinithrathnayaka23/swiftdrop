package com.swiftdrop.pricing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A pricing rule as shown to the admin. */
public record PricingRuleResponse(
        UUID id,
        String ruleName,
        BigDecimal baseFee,
        String currency,
        BigDecimal includedWeightKg,
        BigDecimal additionalFeePerKg,
        BigDecimal maxVolumeCm3,
        BigDecimal oversizeFee,
        Instant effectiveFrom,
        Instant effectiveTo,
        boolean active,
        Instant createdAt) {
}