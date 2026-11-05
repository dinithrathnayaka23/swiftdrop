package com.swiftdrop.pricing.service;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.pricing.dto.request.PricingRuleRequest;
import com.swiftdrop.pricing.dto.request.ZoneAdjustmentRequest;
import com.swiftdrop.pricing.dto.response.PricingRuleResponse;
import com.swiftdrop.pricing.dto.response.ZoneAdjustmentResponse;
import com.swiftdrop.pricing.mapper.PricingRuleMapper;
import com.swiftdrop.pricing.model.PricingRule;
import com.swiftdrop.pricing.model.ZonePricingAdjustment;
import com.swiftdrop.pricing.repository.PricingRuleRepository;
import com.swiftdrop.pricing.repository.ZonePricingAdjustmentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin management of pricing rules and their zone adjustments. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingRuleService {

    private final PricingRuleRepository rules;
    private final ZonePricingAdjustmentRepository adjustments;

    /** All rules, newest start date first. */
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> list() {
        return rules.findAll(Sort.by(Sort.Direction.DESC, "effectiveFrom")).stream()
                .map(PricingRuleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PricingRuleResponse get(UUID ruleId) {
        return PricingRuleMapper.toResponse(findRule(ruleId));
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest request, UUID adminUserId) {
        PricingRule rule = new PricingRule();
        rule.setCreatedByUserId(adminUserId);
        apply(rule, request);
        PricingRule saved = rules.save(rule);
        log.info("Pricing rule {} created by user {}", saved.getId(), adminUserId);
        return PricingRuleMapper.toResponse(saved);
    }

    @Transactional
    public PricingRuleResponse update(UUID ruleId, PricingRuleRequest request) {
        PricingRule rule = findRule(ruleId);
        apply(rule, request);
        log.info("Pricing rule {} updated", ruleId);
        return PricingRuleMapper.toResponse(rule);
    }

    /** Stops the rule being used for new fees. Parcels already quoted keep their link to it. */
    @Transactional
    public PricingRuleResponse deactivate(UUID ruleId) {
        PricingRule rule = findRule(ruleId);
        rule.setActive(false);
        log.info("Pricing rule {} deactivated", ruleId);
        return PricingRuleMapper.toResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<ZoneAdjustmentResponse> listAdjustments(UUID ruleId) {
        findRule(ruleId);
        return adjustments.findByPricingRule_Id(ruleId).stream()
                .map(PricingRuleMapper::toResponse)
                .toList();
    }

    @Transactional
    public ZoneAdjustmentResponse addAdjustment(UUID ruleId, ZoneAdjustmentRequest request) {
        PricingRule rule = findRule(ruleId);
        if (adjustments.findByPricingRule_IdAndPickupZoneIdAndDeliveryZoneId(
                        ruleId, request.pickupZoneId(), request.deliveryZoneId())
                .isPresent()) {
            throw new ConflictException("This route already has an adjustment");
        }
        ZonePricingAdjustment adjustment = new ZonePricingAdjustment();
        adjustment.setPricingRule(rule);
        adjustment.setPickupZoneId(request.pickupZoneId());
        adjustment.setDeliveryZoneId(request.deliveryZoneId());
        return PricingRuleMapper.toResponse(saveAdjustment(adjustment, request));
    }

    /** Changes the type and value of an adjustment. The route itself cannot be changed. */
    @Transactional
    public ZoneAdjustmentResponse updateAdjustment(
            UUID ruleId, UUID adjustmentId, ZoneAdjustmentRequest request) {
        ZonePricingAdjustment adjustment = findAdjustment(ruleId, adjustmentId);
        if (!adjustment.getPickupZoneId().equals(request.pickupZoneId())
                || !adjustment.getDeliveryZoneId().equals(request.deliveryZoneId())) {
            throw new BadRequestException("The route of an adjustment cannot be changed");
        }
        return PricingRuleMapper.toResponse(saveAdjustment(adjustment, request));
    }

    @Transactional
    public void deleteAdjustment(UUID ruleId, UUID adjustmentId) {
        adjustments.delete(findAdjustment(ruleId, adjustmentId));
    }

    private ZonePricingAdjustment saveAdjustment(ZonePricingAdjustment adjustment, ZoneAdjustmentRequest request) {
        adjustment.setAdjustmentType(request.adjustmentType());
        adjustment.setAdjustmentValue(request.adjustmentValue());
        try {
            return adjustments.saveAndFlush(adjustment);
        } catch (DataIntegrityViolationException e) {
            // the database foreign keys reject a zone id that does not exist
            throw new BadRequestException("Unknown delivery zone");
        }
    }

    private void apply(PricingRule rule, PricingRuleRequest request) {
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new BadRequestException("The end date must be after the start date");
        }
        rule.setRuleName(request.ruleName().trim());
        rule.setBaseFee(request.baseFee());
        rule.setIncludedWeightKg(request.includedWeightKg());
        rule.setAdditionalFeePerKg(request.additionalFeePerKg());
        rule.setMaxVolumeCm3(request.maxVolumeCm3());
        rule.setOversizeFee(request.oversizeFee());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setActive(request.active());
    }

    private PricingRule findRule(UUID ruleId) {
        return rules.findById(ruleId).orElseThrow(() -> new NotFoundException("Pricing rule not found"));
    }

    private ZonePricingAdjustment findAdjustment(UUID ruleId, UUID adjustmentId) {
        return adjustments.findById(adjustmentId)
                .filter(a -> a.getPricingRule().getId().equals(ruleId))
                .orElseThrow(() -> new NotFoundException("Zone adjustment not found"));
    }
}

/*all the admin pricing logic. It lists, reads, creates,
 updates and deactivates rules, and manages the zone 
 adjustments of a rule. It checks the date window (the database would 
 reject a bad one with an ugly 500 error), refuses a second 
 adjustment for the same route (409), and turns an unknown zone 
 id into a clean 400. It never logs names or addresses, only ids. */