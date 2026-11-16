package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.llm.ToolDefinition;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code list_my_recent_parcels}: the signed-in customer's latest parcels. The customer comes from
 * the sign-in, never from the model. The model only sees tracking numbers and statuses; the card
 * carries the cities and dates.
 */
@Component
@RequiredArgsConstructor
public class MyRecentParcelsTool implements AssistantTool {

    public static final String NAME = "list_my_recent_parcels";
    static final int LIMIT = 5;
    static final String NONE = "You don't have any parcels yet.";

    private static final ToolDefinition DEFINITION = ToolDefinition.withoutArguments(NAME,
            "The signed-in customer's " + LIMIT + " most recent parcels with their tracking numbers and statuses. "
                    + "Use it when they ask about their parcels without giving a tracking number.");

    private final ParcelService parcelService;
    private final JsonMapper json;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolOutput run(ToolArguments arguments, ToolContext context) {
        List<ParcelDetails> parcels = parcelService.listByCustomer(context.customerId(), PageRequest.of(0, LIMIT))
                .getContent();
        if (parcels.isEmpty()) {
            return ToolOutput.of(json.createObjectNode().put("count", 0).put("message", NONE));
        }

        ObjectNode result = json.createObjectNode().put("count", parcels.size());
        ArrayNode list = result.putArray("parcels");
        parcels.forEach(parcel -> list.addObject()
                .put("trackingNumber", parcel.trackingNumber())
                .put("status", parcel.currentStatus().name())
                .put("statusLabel", StatusLabels.label(parcel.currentStatus())));
        result.put("message", "Your recent parcels: " + parcels.stream()
                .map(parcel -> parcel.trackingNumber() + " (" + StatusLabels.label(parcel.currentStatus()) + ")")
                .collect(Collectors.joining(", ")) + ".");

        return new ToolOutput(result, AssistantCard.parcels(parcels.stream()
                .map(parcel -> new AssistantCard.ParcelSummary(parcel.trackingNumber(), parcel.currentStatus(),
                        StatusLabels.label(parcel.currentStatus()), parcel.pickupAddress().city(),
                        parcel.dropoffAddress().city(), parcel.updatedAt()))
                .toList()));
    }
}
