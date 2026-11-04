package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;

/**
 * Everything a strategy needs to work out its part of the fee.
 *
 * @param rule           the pricing rule being used
 * @param request        the parcel and the two locations
 * @param zoneAdjustment the adjustment for this pickup and delivery zone route, or {@code null} if none
 */
public record FeeContext(PricingRule rule, FeeRequest request, ZonePricingAdjustment zoneAdjustment) {
}