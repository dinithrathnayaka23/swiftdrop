package com.swiftdrop.pricing.controller;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.pricing.dto.request.PricingRuleRequest;
import com.swiftdrop.pricing.dto.request.ZoneAdjustmentRequest;
import com.swiftdrop.pricing.dto.response.PricingRuleResponse;
import com.swiftdrop.pricing.dto.response.ZoneAdjustmentResponse;
import com.swiftdrop.pricing.service.PricingRuleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pricing rules and zone adjustments, for dispatchers/admins: the Pricing Rules screen. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/pricing-rules")
@PreAuthorize("hasRole('DISPATCHER_ADMIN')")
@RequiredArgsConstructor
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    @GetMapping
    public List<PricingRuleResponse> list() {
        return pricingRuleService.list();
    }

    @GetMapping("/{ruleId}")
    public PricingRuleResponse get(@PathVariable UUID ruleId) {
        return pricingRuleService.get(ruleId);
    }

    @PostMapping
    public ResponseEntity<PricingRuleResponse> create(
            @Valid @RequestBody PricingRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        PricingRuleResponse created = pricingRuleService.create(request, UUID.fromString(user.userId()));
        return ResponseEntity.created(URI.create(ApiPaths.API_V1 + "/pricing-rules/" + created.id()))
                .body(created);
    }

    @PutMapping("/{ruleId}")
    public PricingRuleResponse update(@PathVariable UUID ruleId, @Valid @RequestBody PricingRuleRequest request) {
        return pricingRuleService.update(ruleId, request);
    }

    /** Stops the rule being used for new fees. */
    @PostMapping("/{ruleId}/deactivate")
    public PricingRuleResponse deactivate(@PathVariable UUID ruleId) {
        return pricingRuleService.deactivate(ruleId);
    }

    @GetMapping("/{ruleId}/zone-adjustments")
    public List<ZoneAdjustmentResponse> listAdjustments(@PathVariable UUID ruleId) {
        return pricingRuleService.listAdjustments(ruleId);
    }

    @PostMapping("/{ruleId}/zone-adjustments")
    public ResponseEntity<ZoneAdjustmentResponse> addAdjustment(
            @PathVariable UUID ruleId, @Valid @RequestBody ZoneAdjustmentRequest request) {
        ZoneAdjustmentResponse created = pricingRuleService.addAdjustment(ruleId, request);
        return ResponseEntity.created(URI.create(
                        ApiPaths.API_V1 + "/pricing-rules/" + ruleId + "/zone-adjustments/" + created.id()))
                .body(created);
    }

    @PutMapping("/{ruleId}/zone-adjustments/{adjustmentId}")
    public ZoneAdjustmentResponse updateAdjustment(
            @PathVariable UUID ruleId,
            @PathVariable UUID adjustmentId,
            @Valid @RequestBody ZoneAdjustmentRequest request) {
        return pricingRuleService.updateAdjustment(ruleId, adjustmentId, request);
    }

    @DeleteMapping("/{ruleId}/zone-adjustments/{adjustmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAdjustment(@PathVariable UUID ruleId, @PathVariable UUID adjustmentId) {
        pricingRuleService.deleteAdjustment(ruleId, adjustmentId);
    }
}