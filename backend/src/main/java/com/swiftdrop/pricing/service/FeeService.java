package com.swiftdrop.pricing.service;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import com.swiftdrop.pricing.repository.PricingRuleRepository;
import com.swiftdrop.pricing.repository.ZonePricingAdjustmentRepository;
import com.swiftdrop.pricing.strategy.FeeContext;
import com.swiftdrop.pricing.strategy.FeeStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates delivery fees by adding up the lines from every {@link FeeStrategy}. */
@Service
@RequiredArgsConstructor
public class FeeService implements FeeApi {

    private final PricingRuleRepository pricingRules;
    private final ZonePricingAdjustmentRepository zoneAdjustments;
    private final ZoneResolver zoneResolver;
    private final List<FeeStrategy> strategies;

    @Override
    @Transactional(readOnly = true)
    public FeeEstimate estimateFee(FeeRequest request) {
        PricingRule rule = pricingRules.findActiveAt(Instant.now()).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Delivery pricing is not available right now"));

        FeeContext context = new FeeContext(rule, request, findZoneAdjustment(rule, request));

        List<FeeLine> lines = strategies.stream()
                .map(strategy -> strategy.calculate(context))
                .flatMap(Optional::stream)
                .toList();

        BigDecimal total = lines.stream()
                .map(FeeLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new FeeEstimate(total, rule.getCurrency(), rule.getId(), lines);
    }

    private ZonePricingAdjustment findZoneAdjustment(PricingRule rule, FeeRequest request) {
        Optional<UUID> pickupZone = zoneResolver.findZoneId(request.pickup());
        Optional<UUID> dropoffZone = zoneResolver.findZoneId(request.dropoff());
        if (pickupZone.isEmpty() || dropoffZone.isEmpty()) {
            return null;
        }
        return zoneAdjustments
                .findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(
                        rule.getId(), pickupZone.get(), dropoffZone.get())
                .orElse(null);
    }
}