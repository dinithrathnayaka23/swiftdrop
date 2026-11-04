package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.api.FeeLine;
import java.util.Optional;

/** One pricing rule. Returns a fee line, or nothing when the rule adds no charge. */
public interface FeeStrategy {

    Optional<FeeLine> calculate(FeeContext context);
}