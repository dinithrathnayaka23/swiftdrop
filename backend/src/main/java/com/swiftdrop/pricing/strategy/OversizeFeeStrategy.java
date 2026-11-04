package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.api.FeeRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adds a fixed fee when the parcel is bigger than the rule allows (length x width x height). */
@Component
@Order(3)
public class OversizeFeeStrategy implements FeeStrategy {

    @Override
    public Optional<FeeLine> calculate(FeeContext context) {
        BigDecimal maxVolume = context.rule().getMaxVolumeCm3();
        FeeRequest request = context.request();
        if (maxVolume == null
                || request.lengthCm() == null
                || request.widthCm() == null
                || request.heightCm() == null) {
            return Optional.empty();
        }
        BigDecimal volume = request.lengthCm().multiply(request.widthCm()).multiply(request.heightCm());
        if (volume.compareTo(maxVolume) <= 0 || context.rule().getOversizeFee().signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new FeeLine("Oversize fee", context.rule().getOversizeFee()));
    }
}