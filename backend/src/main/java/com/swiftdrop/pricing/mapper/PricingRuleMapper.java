package com.swiftdrop.pricing.mapper;

import com.swiftdrop.pricing.dto.response.PricingRuleResponse;
import com.swiftdrop.pricing.dto.response.ZoneAdjustmentResponse;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;

/** Entity to response mapping for the pricing module. */
public final class PricingRuleMapper {

    private PricingRuleMapper() {
    }

    public static PricingRuleResponse toResponse(PricingRule rule) {
        return new PricingRuleResponse(
                rule.getId(),
                rule.getRuleName(),
                rule.getBaseFee(),
                rule.getCurrency(),
                rule.getIncludedWeightKg(),
                rule.getAdditionalFeePerKg(),
                rule.getMaxVolumeCm3(),
                rule.getOversizeFee(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.isActive(),
                rule.getCreatedAt());
    }

    public static ZoneAdjustmentResponse toResponse(ZonePricingAdjustment adjustment) {
        return new ZoneAdjustmentResponse(
                adjustment.getId(),
                adjustment.getPricingRule().getId(),
                adjustment.getPickupZoneId(),
                adjustment.getDeliveryZoneId(),
                adjustment.getAdjustmentType(),
                adjustment.getAdjustmentValue());
    }
}

//turns entities into response records. It is a plain class 
// with static methods, because it has no state. Keeping 
// this in one place means the service doesn’t repeat field copying (DRY).