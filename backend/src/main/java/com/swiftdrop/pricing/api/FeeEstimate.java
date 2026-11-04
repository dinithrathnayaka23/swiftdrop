package com.swiftdrop.pricing.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The result of a fee calculation.
 *
 * @param totalFee      fee in the given currency
 * @param pricingRuleId {@code pricing_rules.pricing_rule_id} used for the quote; pass it to
 *                      {@code NewParcel}. Null only while the stub is in use.
 * @param lines         breakdown shown on the review screen
 */
public record FeeEstimate(
        BigDecimal totalFee,
        String currency,
        UUID pricingRuleId,
        List<FeeLine> lines) {

    public FeeEstimate {
        lines = List.copyOf(lines);
    }
}