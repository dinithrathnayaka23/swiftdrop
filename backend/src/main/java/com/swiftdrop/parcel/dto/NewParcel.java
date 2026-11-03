package com.swiftdrop.parcel.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Data needed to create a parcel. Built by the booking flow (customer parcels) and the merchant
 * order API (merchant parcels) after they have validated the request and calculated the fee.
 *
 * <p>Exactly one owner is set: {@code customerUserId} for a customer booking, or {@code merchantId}
 * for a merchant order.
 *
 * @param externalOrderReference the merchant's own order id; only for merchant parcels, optional
 * @param lengthCm               optional; {@code widthCm} and {@code heightCm} likewise
 * @param pricingRuleId          {@code pricing_rules.pricing_rule_id} used for the quote, or {@code null}
 * @param quotedFee              fee in LKR shown to the customer or merchant
 */
public record NewParcel(
        UUID customerUserId,
        UUID merchantId,
        String externalOrderReference,
        NewAddress pickupAddress,
        NewAddress dropoffAddress,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String contentDescription,
        String deliveryInstructions,
        UUID pricingRuleId,
        BigDecimal quotedFee) {

    /** Rejects programming errors early; user input is validated by the calling module. */
    public NewParcel {
        if ((customerUserId == null) == (merchantId == null)) {
            throw new IllegalArgumentException("Exactly one of customerUserId and merchantId must be set");
        }
        if (customerUserId != null && externalOrderReference != null) {
            throw new IllegalArgumentException("externalOrderReference is only for merchant parcels");
        }
        Objects.requireNonNull(pickupAddress, "pickupAddress");
        Objects.requireNonNull(dropoffAddress, "dropoffAddress");
        Objects.requireNonNull(weightKg, "weightKg");
        Objects.requireNonNull(quotedFee, "quotedFee");
        if (weightKg.signum() <= 0) {
            throw new IllegalArgumentException("weightKg must be greater than 0");
        }
        if (quotedFee.signum() < 0) {
            throw new IllegalArgumentException("quotedFee must not be negative");
        }
    }
}
