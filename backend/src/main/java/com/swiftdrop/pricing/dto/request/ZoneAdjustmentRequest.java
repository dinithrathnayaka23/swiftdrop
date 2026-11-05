package com.swiftdrop.pricing.dto.request;

import com.swiftdrop.pricing.model.AdjustmentType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Add or change the price adjustment of a pickup zone to delivery zone route.
 *
 * @param adjustmentValue an amount in LKR for FLAT, or a percentage of the base fee for PERCENTAGE;
 *                        negative values give a discount
 */
public record ZoneAdjustmentRequest(
        @NotNull UUID pickupZoneId,
        @NotNull UUID deliveryZoneId,
        @NotNull AdjustmentType adjustmentType,
        @NotNull @Digits(integer = 8, fraction = 2) BigDecimal adjustmentValue) {
}