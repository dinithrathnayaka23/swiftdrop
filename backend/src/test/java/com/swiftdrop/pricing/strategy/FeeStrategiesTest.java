package com.swiftdrop.pricing.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.pricing.model.AdjustmentType;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeeStrategiesTest {

    private final FeeLocation colombo = new FeeLocation("Colombo", "Colombo 03", null);
    private final FeeLocation kandy = new FeeLocation("Kandy", "Kandy", "20000");

    private PricingRule rule() {
        PricingRule rule = new PricingRule();
        rule.setBaseFee(new BigDecimal("350.00"));
        rule.setIncludedWeightKg(new BigDecimal("1"));
        rule.setAdditionalFeePerKg(new BigDecimal("100.00"));
        rule.setMaxVolumeCm3(new BigDecimal("50000"));
        rule.setOversizeFee(new BigDecimal("200.00"));
        return rule;
    }

    private FeeRequest request(String weightKg, String length, String width, String height) {
        return new FeeRequest(
                colombo,
                kandy,
                new BigDecimal(weightKg),
                length == null ? null : new BigDecimal(length),
                width == null ? null : new BigDecimal(width),
                height == null ? null : new BigDecimal(height));
    }

    private ZonePricingAdjustment adjustment(AdjustmentType type, String value) {
        ZonePricingAdjustment adjustment = new ZonePricingAdjustment();
        adjustment.setAdjustmentType(type);
        adjustment.setAdjustmentValue(new BigDecimal(value));
        return adjustment;
    }

    @Test
    void baseFeeIsTheRuleBaseFee() {
        FeeContext context = new FeeContext(rule(), request("1", null, null, null), null);

        var line = new BaseFeeStrategy().calculate(context).orElseThrow();

        assertThat(line.amount()).isEqualByComparingTo("350.00");
    }

    @Test
    void weightFeeIsEmptyWithinIncludedWeight() {
        FeeContext context = new FeeContext(rule(), request("1", null, null, null), null);

        assertThat(new WeightFeeStrategy().calculate(context)).isEmpty();
    }

    @Test
    void weightFeeRoundsExtraKilogramsUp() {
        // 3.2 kg - 1 kg included = 2.2 kg extra, charged as 3 kg x 100
        FeeContext context = new FeeContext(rule(), request("3.2", null, null, null), null);

        var line = new WeightFeeStrategy().calculate(context).orElseThrow();

        assertThat(line.amount()).isEqualByComparingTo("300.00");
    }

    @Test
    void oversizeFeeAppliesWhenVolumeIsTooBig() {
        // 50 x 40 x 30 = 60000 cm3, above the 50000 limit
        FeeContext context = new FeeContext(rule(), request("1", "50", "40", "30"), null);

        var line = new OversizeFeeStrategy().calculate(context).orElseThrow();

        assertThat(line.amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void oversizeFeeIsEmptyWhenVolumeFits() {
        // 40 x 40 x 30 = 48000 cm3
        FeeContext context = new FeeContext(rule(), request("1", "40", "40", "30"), null);

        assertThat(new OversizeFeeStrategy().calculate(context)).isEmpty();
    }

    @Test
    void oversizeFeeIsEmptyWhenSizeIsNotGiven() {
        FeeContext context = new FeeContext(rule(), request("1", null, null, null), null);

        assertThat(new OversizeFeeStrategy().calculate(context)).isEmpty();
    }

    @Test
    void zoneAdjustmentFlatAddsFixedAmount() {
        FeeContext context = new FeeContext(
                rule(), request("1", null, null, null), adjustment(AdjustmentType.FLAT, "150.00"));

        var line = new ZoneAdjustmentStrategy().calculate(context).orElseThrow();

        assertThat(line.amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void zoneAdjustmentPercentageIsTakenFromBaseFee() {
        // 10% of 350.00
        FeeContext context = new FeeContext(
                rule(), request("1", null, null, null), adjustment(AdjustmentType.PERCENTAGE, "10.00"));

        var line = new ZoneAdjustmentStrategy().calculate(context).orElseThrow();

        assertThat(line.amount()).isEqualByComparingTo("35.00");
    }

    @Test
    void zoneAdjustmentIsEmptyWhenRouteHasNone() {
        FeeContext context = new FeeContext(rule(), request("1", null, null, null), null);

        assertThat(new ZoneAdjustmentStrategy().calculate(context)).isEmpty();
    }
}