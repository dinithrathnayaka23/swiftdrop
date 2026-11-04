package com.swiftdrop.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.model.AdjustmentType;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import com.swiftdrop.pricing.repository.PricingRuleRepository;
import com.swiftdrop.pricing.repository.ZonePricingAdjustmentRepository;
import com.swiftdrop.pricing.strategy.BaseFeeStrategy;
import com.swiftdrop.pricing.strategy.OversizeFeeStrategy;
import com.swiftdrop.pricing.strategy.WeightFeeStrategy;
import com.swiftdrop.pricing.strategy.ZoneAdjustmentStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeServiceTest {

    private final FeeLocation colombo = new FeeLocation("Colombo", "Colombo 03", null);
    private final FeeLocation kandy = new FeeLocation("Kandy", "Kandy", "20000");
    private final UUID ruleId = UUID.randomUUID();
    private final UUID colomboZone = UUID.randomUUID();
    private final UUID kandyZone = UUID.randomUUID();

    @Mock
    private PricingRuleRepository pricingRules;

    @Mock
    private ZonePricingAdjustmentRepository zoneAdjustments;

    @Mock
    private ZoneResolver zoneResolver;

    private FeeService service;

    @BeforeEach
    void setUp() {
        service = new FeeService(
                pricingRules,
                zoneAdjustments,
                zoneResolver,
                List.of(
                        new BaseFeeStrategy(),
                        new WeightFeeStrategy(),
                        new OversizeFeeStrategy(),
                        new ZoneAdjustmentStrategy()));
    }

    private PricingRule rule() {
        PricingRule rule = new PricingRule();
        rule.setId(ruleId);
        rule.setBaseFee(new BigDecimal("350.00"));
        rule.setIncludedWeightKg(new BigDecimal("1"));
        rule.setAdditionalFeePerKg(new BigDecimal("100.00"));
        return rule;
    }

    private FeeRequest request(String weightKg) {
        return new FeeRequest(colombo, kandy, new BigDecimal(weightKg), null, null, null);
    }

    private ZonePricingAdjustment flatAdjustment(String value) {
        ZonePricingAdjustment adjustment = new ZonePricingAdjustment();
        adjustment.setAdjustmentType(AdjustmentType.FLAT);
        adjustment.setAdjustmentValue(new BigDecimal(value));
        return adjustment;
    }

    private void zonesFound() {
        when(zoneResolver.findZoneId(colombo)).thenReturn(Optional.of(colomboZone));
        when(zoneResolver.findZoneId(kandy)).thenReturn(Optional.of(kandyZone));
    }

    @Test
    void addsBaseAndWeightFeesWhenNoZoneIsFound() {
        when(pricingRules.findActiveAt(any(Instant.class))).thenReturn(List.of(rule()));

        // 3.2 kg: 350 base + 3 extra kg x 100
        FeeEstimate estimate = service.estimateFee(request("3.2"));

        assertThat(estimate.totalFee()).isEqualByComparingTo("650.00");
        assertThat(estimate.currency()).isEqualTo("LKR");
        assertThat(estimate.pricingRuleId()).isEqualTo(ruleId);
        assertThat(estimate.lines()).hasSize(2);
    }

    @Test
    void addsZoneAdjustmentWhenBothZonesAreFound() {
        when(pricingRules.findActiveAt(any(Instant.class))).thenReturn(List.of(rule()));
        zonesFound();
        when(zoneAdjustments.findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(ruleId, colomboZone, kandyZone))
                .thenReturn(Optional.of(flatAdjustment("150.00")));

        FeeEstimate estimate = service.estimateFee(request("1"));

        assertThat(estimate.totalFee()).isEqualByComparingTo("500.00");
        assertThat(estimate.lines()).hasSize(2);
    }

    @Test
    void skipsZoneAdjustmentWhenRouteHasNone() {
        when(pricingRules.findActiveAt(any(Instant.class))).thenReturn(List.of(rule()));
        zonesFound();
        when(zoneAdjustments.findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(ruleId, colomboZone, kandyZone))
                .thenReturn(Optional.empty());

        FeeEstimate estimate = service.estimateFee(request("1"));

        assertThat(estimate.totalFee()).isEqualByComparingTo("350.00");
    }

    @Test
    void totalIsNeverNegative() {
        when(pricingRules.findActiveAt(any(Instant.class))).thenReturn(List.of(rule()));
        zonesFound();
        when(zoneAdjustments.findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(ruleId, colomboZone, kandyZone))
                .thenReturn(Optional.of(flatAdjustment("-1000.00")));

        FeeEstimate estimate = service.estimateFee(request("1"));

        assertThat(estimate.totalFee()).isEqualByComparingTo("0.00");
    }

    @Test
    void failsWhenNoPricingRuleIsActive() {
        when(pricingRules.findActiveAt(any(Instant.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.estimateFee(request("1"))).isInstanceOf(NotFoundException.class);
    }
}