package com.swiftdrop.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.pricing.dto.request.PricingRuleRequest;
import com.swiftdrop.pricing.dto.request.ZoneAdjustmentRequest;
import com.swiftdrop.pricing.dto.response.PricingRuleResponse;
import com.swiftdrop.pricing.model.AdjustmentType;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import com.swiftdrop.pricing.repository.PricingRuleRepository;
import com.swiftdrop.pricing.repository.ZonePricingAdjustmentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricingRuleServiceTest {

    private final UUID adminId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private PricingRuleRepository rules;

    @Mock
    private ZonePricingAdjustmentRepository adjustments;

    @InjectMocks
    private PricingRuleService service;

    private PricingRuleRequest request(Instant effectiveTo) {
        return new PricingRuleRequest(
                "  Standard  ",
                new BigDecimal("350.00"),
                new BigDecimal("1.000"),
                new BigDecimal("100.00"),
                null,
                new BigDecimal("0.00"),
                from,
                effectiveTo,
                true);
    }

    @Test
    void createSavesTrimmedRuleWithAdminId() {
        when(rules.save(any(PricingRule.class))).thenAnswer(call -> call.getArgument(0));

        PricingRuleResponse response = service.create(request(null), adminId);

        assertThat(response.ruleName()).isEqualTo("Standard");
        assertThat(response.baseFee()).isEqualByComparingTo("350.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> service.create(request(from.minusSeconds(60)), adminId))
                .isInstanceOf(BadRequestException.class);

        verify(rules, never()).save(any());
    }

    @Test
    void updateFailsWhenRuleDoesNotExist() {
        when(rules.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ruleId, request(null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deactivateTurnsTheRuleOff() {
        PricingRule rule = new PricingRule();
        rule.setId(ruleId);
        rule.setActive(true);
        when(rules.findById(ruleId)).thenReturn(Optional.of(rule));

        PricingRuleResponse response = service.deactivate(ruleId);

        assertThat(response.active()).isFalse();
    }

    @Test
    void addAdjustmentRefusesRouteThatAlreadyHasOne() {
        UUID pickupZone = UUID.randomUUID();
        UUID deliveryZone = UUID.randomUUID();
        PricingRule rule = new PricingRule();
        rule.setId(ruleId);
        when(rules.findById(ruleId)).thenReturn(Optional.of(rule));
        when(adjustments.findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(ruleId, pickupZone, deliveryZone))
                .thenReturn(Optional.of(new ZonePricingAdjustment()));

        ZoneAdjustmentRequest request = new ZoneAdjustmentRequest(
                pickupZone, deliveryZone, AdjustmentType.FLAT, new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.addAdjustment(ruleId, request)).isInstanceOf(ConflictException.class);
    }
}