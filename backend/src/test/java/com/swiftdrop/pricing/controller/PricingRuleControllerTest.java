package com.swiftdrop.pricing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.pricing.dto.request.PricingRuleRequest;
import com.swiftdrop.pricing.dto.response.PricingRuleResponse;
import com.swiftdrop.pricing.service.PricingRuleService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class PricingRuleControllerTest {

    @Mock
    private PricingRuleService pricingRuleService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final AuthenticatedUser admin = new AuthenticatedUser(adminId.toString(), Set.of(Role.DISPATCHER_ADMIN));
    private final Instant from = Instant.parse("2026-01-01T00:00:00Z");

    private PricingRuleRequest request(String name, String baseFee) {
        return new PricingRuleRequest(
                name, new BigDecimal(baseFee), new BigDecimal("1.000"), new BigDecimal("100.00"),
                null, new BigDecimal("0.00"), from, null, true);
    }

    private PricingRuleResponse response() {
        return new PricingRuleResponse(
                ruleId, "Standard", new BigDecimal("350.00"), "LKR", new BigDecimal("1.000"),
                new BigDecimal("100.00"), null, new BigDecimal("0.00"), from, null, true, from);
    }

    @Test
    void onlyAdminsCanUseTheEndpoints() {
        assertThat(PricingRuleController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('DISPATCHER_ADMIN')");
    }

    @Test
    void createUsesTheSignedInAdminAndReturns201WithLocation() {
        PricingRuleRequest request = request("Standard", "350.00");
        when(pricingRuleService.create(request, adminId)).thenReturn(response());

        ResponseEntity<PricingRuleResponse> result =
                new PricingRuleController(pricingRuleService).create(request, admin);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getLocation().toString()).endsWith("/pricing-rules/" + ruleId);
        assertThat(result.getBody().id()).isEqualTo(ruleId);
    }

    @Test
    void deactivateAndDeleteCallTheService() {
        PricingRuleController controller = new PricingRuleController(pricingRuleService);
        UUID adjustmentId = UUID.randomUUID();

        controller.deactivate(ruleId);
        controller.deleteAdjustment(ruleId, adjustmentId);

        verify(pricingRuleService).deactivate(ruleId);
        verify(pricingRuleService).deleteAdjustment(ruleId, adjustmentId);
    }

    @Test
    void requestRejectsBlankNameAndNegativeFee() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        var violations = validator.validate(request(" ", "-1.00"));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("ruleName", "baseFee");
    }

    @Test
    void requestAcceptsValidRule() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(request("Standard", "350.00"))).isEmpty();
    }
}