package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adds the extra charge (or discount) set for a pickup zone to delivery zone route. */
@Component
@Order(4)
public class ZoneAdjustmentStrategy implements FeeStrategy {

    @Override
    public Optional<FeeLine> calculate(FeeContext context) {
        ZonePricingAdjustment adjustment = context.zoneAdjustment();
        if (adjustment == null) {
            return Optional.empty();
        }
        BigDecimal amount = switch (adjustment.getAdjustmentType()) {
            case FLAT -> adjustment.getAdjustmentValue();
            // a percentage of the base fee
            case PERCENTAGE -> context.rule().getBaseFee()
                    .multiply(adjustment.getAdjustmentValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        };
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new FeeLine("Zone adjustment", amount));
    }
}