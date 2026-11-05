package com.swiftdrop.pricing.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Create or update a pricing rule. Amounts are in LKR.
 *
 * @param includedWeightKg weight covered by the base fee
 * @param maxVolumeCm3     optional; parcels bigger than this pay the oversize fee
 * @param effectiveTo      optional; empty means the rule has no end date
 */
public record PricingRuleRequest(
        @NotBlank @Size(max = 120) String ruleName,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0.000") @Digits(integer = 5, fraction = 3) BigDecimal includedWeightKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal additionalFeePerKg,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal maxVolumeCm3,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal oversizeFee,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo,
        @NotNull Boolean active) {
}