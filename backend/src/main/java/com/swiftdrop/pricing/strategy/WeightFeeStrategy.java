package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.api.FeeLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Charges for every started kilogram above the weight that the base fee already includes. */
@Component
@Order(2)
public class WeightFeeStrategy implements FeeStrategy {

    @Override
    public Optional<FeeLine> calculate(FeeContext context) {
        BigDecimal extraKg = context.request().weightKg().subtract(context.rule().getIncludedWeightKg());
        if (extraKg.signum() <= 0) {
            return Optional.empty();
        }
        // 2.2 extra kg is charged as 3 kg
        BigDecimal wholeKg = extraKg.setScale(0, RoundingMode.CEILING);
        BigDecimal amount = context.rule().getAdditionalFeePerKg()
                .multiply(wholeKg)
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new FeeLine("Extra weight (" + wholeKg + " kg)", amount));
    }
}