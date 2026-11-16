package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.llm.ToolDefinition;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.service.SystemSettingsService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** {@code get_service_info}: contact details, parcel types and limits from the system settings. */
@Component
@RequiredArgsConstructor
public class ServiceInfoTool implements AssistantTool {

    public static final String NAME = "get_service_info";
    static final String HOW_TRACKING_WORKS = "Enter the tracking number (it starts with SD) on the Track page, "
            + "or ask here. The status updates live as the parcel moves.";

    private static final ToolDefinition DEFINITION = ToolDefinition.withoutArguments(NAME,
            "SwiftDrop's contact details, the parcel types you can send, the maximum weight and size, "
                    + "and how tracking works.");

    private final SystemSettingsService settingsService;
    private final JsonMapper json;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolOutput run(ToolArguments arguments, ToolContext context) {
        SystemSettingsView settings = settingsService.current();
        ObjectNode result = json.createObjectNode()
                .put("companyName", settings.companyName())
                .put("contactEmail", settings.contactEmail())
                .put("contactPhone", settings.contactPhone())
                .put("maxParcelWeightKg", settings.maxParcelWeightKg())
                .put("maxParcelDimensionCm", settings.maxParcelDimensionCm())
                .put("howTrackingWorks", HOW_TRACKING_WORKS);
        ArrayNode types = result.putArray("parcelTypes");
        for (ParcelType type : ParcelType.values()) {
            types.addObject().put("type", type.name()).put("description", describe(type));
        }
        result.put("message", message(settings));
        return ToolOutput.of(result);
    }

    static String describe(ParcelType type) {
        return switch (type) {
            case DOCUMENT -> "Letters and papers";
            case PARCEL -> "General goods with no special handling";
            case FRAGILE -> "Breakable items, such as glass or ceramics";
            case ELECTRONICS -> "Phones, computers and other devices";
            case PERISHABLE -> "Food, flowers and other items that spoil";
        };
    }

    private static String message(SystemSettingsView settings) {
        String types = Arrays.stream(ParcelType.values())
                .map(type -> describe(type).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("; "));
        return "You can send: " + types + ". Maximum weight " + plain(settings.maxParcelWeightKg())
                + " kg and maximum size " + plain(settings.maxParcelDimensionCm()) + " cm per side. Contact "
                + settings.companyName() + " at " + settings.contactEmail() + " or " + settings.contactPhone() + ".";
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
