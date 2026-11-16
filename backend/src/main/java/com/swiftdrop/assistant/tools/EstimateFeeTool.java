package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.llm.ToolDefinition;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLocation;
import com.swiftdrop.pricing.api.FeeRequest;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** {@code estimate_fee}: a delivery fee quote from the pricing module. */
@Component
@RequiredArgsConstructor
public class EstimateFeeTool implements AssistantTool {

    public static final String NAME = "estimate_fee";
    static final int MAX_PLACE_CHARS = 60;

    private static final Map<String, Object> TEXT = Map.of("type", "string");
    private static final Map<String, Object> NUMBER = Map.of("type", "number");
    private static final ToolDefinition DEFINITION = new ToolDefinition(NAME,
            "Delivery fee for a parcel in Sri Lanka. Needs the pickup and drop-off district and city and the "
                    + "weight in kg; ask the customer for any that are missing. Dimensions are optional.",
            Map.of("type", "object",
                    "properties", Map.of(
                            "pickupDistrict", TEXT, "pickupCity", TEXT,
                            "dropoffDistrict", TEXT, "dropoffCity", TEXT,
                            "weightKg", NUMBER, "lengthCm", NUMBER, "widthCm", NUMBER, "heightCm", NUMBER),
                    "required", List.of("pickupDistrict", "pickupCity", "dropoffDistrict", "dropoffCity",
                            "weightKg")));

    private final FeeApi feeApi;
    private final SystemSettingsService settingsService;
    private final JsonMapper json;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolOutput run(ToolArguments arguments, ToolContext context) {
        SystemSettingsView settings = settingsService.current();
        FeeRequest request = new FeeRequest(
                new FeeLocation(arguments.text("pickupDistrict", MAX_PLACE_CHARS),
                        arguments.text("pickupCity", MAX_PLACE_CHARS), null),
                new FeeLocation(arguments.text("dropoffDistrict", MAX_PLACE_CHARS),
                        arguments.text("dropoffCity", MAX_PLACE_CHARS), null),
                atMost(arguments.positiveNumber("weightKg"), settings.maxParcelWeightKg(), "weightKg", "kg"),
                atMost(arguments.optionalPositiveNumber("lengthCm"), settings.maxParcelDimensionCm(), "lengthCm", "cm"),
                atMost(arguments.optionalPositiveNumber("widthCm"), settings.maxParcelDimensionCm(), "widthCm", "cm"),
                atMost(arguments.optionalPositiveNumber("heightCm"), settings.maxParcelDimensionCm(), "heightCm", "cm"));

        FeeEstimate estimate;
        try {
            estimate = feeApi.estimateFee(request);
        } catch (NotFoundException ex) {
            return ToolOutput.of(json.createObjectNode()
                    .put("error", "PRICING_UNAVAILABLE")
                    .put("message", "Prices aren't available right now. Please contact us at "
                            + settings.contactEmail() + " or " + settings.contactPhone() + "."));
        }

        var fee = new AssistantCard.Fee(estimate.totalFee(), estimate.currency(), estimate.lines().stream()
                .map(line -> new AssistantCard.Fee.Line(line.label(), line.amount()))
                .toList());
        ObjectNode result = json.createObjectNode()
                .put("totalFee", estimate.totalFee())
                .put("currency", estimate.currency())
                .put("message", "The delivery fee is " + estimate.currency() + " "
                        + estimate.totalFee().toPlainString() + ".");
        ArrayNode lines = result.putArray("lines");
        fee.lines().forEach(line -> lines.addObject().put("label", line.label()).put("amount", line.amount()));
        return new ToolOutput(result, AssistantCard.fee(fee));
    }

    private static BigDecimal atMost(BigDecimal value, BigDecimal max, String field, String unit) {
        if (value != null && max != null && value.compareTo(max) > 0) {
            throw new InvalidToolArgumentException(field, "must be at most " + max.stripTrailingZeros().toPlainString()
                    + " " + unit);
        }
        return value;
    }
}
