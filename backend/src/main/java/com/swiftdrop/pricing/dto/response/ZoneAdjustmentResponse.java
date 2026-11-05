package com.swiftdrop.pricing.dto.response;

import com.swiftdrop.pricing.model.AdjustmentType;
import java.math.BigDecimal;
import java.util.UUID;

/** A route price adjustment as shown to the admin. */
public record ZoneAdjustmentResponse(
        UUID id,
        UUID pricingRuleId,
        UUID pickupZoneId,
        UUID deliveryZoneId,
        AdjustmentType adjustmentType,
        BigDecimal adjustmentValue) {
}