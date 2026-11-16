package com.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class EstimateFeeToolTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ToolContext CONTEXT = new ToolContext(UUID.randomUUID(), "203.0.113.7");

    private final FeeApi feeApi = mock(FeeApi.class);
    private final SystemSettingsService settingsService = mock(SystemSettingsService.class);
    private final EstimateFeeTool tool = new EstimateFeeTool(feeApi, settingsService, JSON);
    private final ToolRegistry registry = new ToolRegistry(List.of(tool), JSON);

    @BeforeEach
    void settings() {
        when(settingsService.current()).thenReturn(new SystemSettingsView("SwiftDrop", "help@swiftdrop.lk",
                "+94112345678", "1 Main Street", "Asia/Colombo", null, null, 10, "PARCEL", new BigDecimal("30"),
                new BigDecimal("150"), null, false, false, false, false, false, false, 1, null));
    }

    private static ObjectNode colomboToKandy(Object weightKg) {
        ObjectNode arguments = JSON.createObjectNode().put("pickupDistrict", "Colombo").put("pickupCity", "Colombo")
                .put("dropoffDistrict", "Kandy").put("dropoffCity", " Kandy ");
        arguments.set("weightKg", JSON.valueToTree(weightKg));
        return arguments;
    }

    @Test
    void quotesTheFeeWithAFeeCardAndNoPricingRuleId() {
        when(feeApi.estimateFee(any())).thenReturn(new FeeEstimate(new BigDecimal("750.00"), "LKR", UUID.randomUUID(),
                List.of(new FeeLine("Base fee", new BigDecimal("500.00")), new FeeLine("Weight fee", new BigDecimal("250.00")))));

        ToolOutput output = registry.run(EstimateFeeTool.NAME, colomboToKandy(2.5), CONTEXT);

        ArgumentCaptor<FeeRequest> request = ArgumentCaptor.forClass(FeeRequest.class);
        org.mockito.Mockito.verify(feeApi).estimateFee(request.capture());
        assertThat(request.getValue().dropoff().city()).isEqualTo("Kandy");
        assertThat(request.getValue().weightKg()).isEqualByComparingTo("2.5");
        assertThat(output.result().get("message").asString()).isEqualTo("The delivery fee is LKR 750.00.");
        assertThat(output.result().toString()).doesNotContain("pricingRuleId");
        assertThat(output.card().type()).isEqualTo(AssistantCard.Type.FEE);
        assertThat(output.card().fee().lines()).hasSize(2);
    }

    @Test
    void aWeightOverTheSettingsMaximumIsInvalidInput() {
        JsonNode result = registry.run(EstimateFeeTool.NAME, colomboToKandy(31), CONTEXT).result();

        assertThat(result.get("error").asString()).isEqualTo("INVALID_INPUT");
        assertThat(result.get("field").asString()).isEqualTo("weightKg");
        assertThat(result.get("message").asString()).contains("weightKg must be at most 30 kg");
        verifyNoInteractions(feeApi);
    }

    @Test
    void noActivePricingRuleGivesPricingUnavailableWithTheContactDetails() {
        when(feeApi.estimateFee(any())).thenThrow(new NotFoundException("Delivery pricing is not available right now"));

        ToolOutput output = registry.run(EstimateFeeTool.NAME, colomboToKandy(2), CONTEXT);

        assertThat(output.result().get("error").asString()).isEqualTo("PRICING_UNAVAILABLE");
        assertThat(output.result().get("message").asString()).contains("help@swiftdrop.lk", "+94112345678");
        assertThat(output.card()).isNull();
    }
}
