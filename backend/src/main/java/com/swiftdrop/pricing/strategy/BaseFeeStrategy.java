package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.api.FeeLine;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The starting price of every delivery. */
@Component
@Order(1)
public class BaseFeeStrategy implements FeeStrategy {

    @Override
    public Optional<FeeLine> calculate(FeeContext context) {
        return Optional.of(new FeeLine("Base fee", context.rule().getBaseFee()));
    }
}