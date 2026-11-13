package com.swiftdrop.assistant.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Structured data shown under a reply. Cards are built by the server from tool results, never from
 * the model's text, so the widget always shows real figures. Exactly one payload is set, matching
 * {@link #type()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantCard(Type type, PublicTrackingResponse tracking, Fee fee, List<ParcelSummary> parcels) {

    public enum Type {
        TRACKING,
        FEE,
        PARCELS
    }

    public AssistantCard {
        Objects.requireNonNull(type, "type");
        parcels = parcels == null ? null : List.copyOf(parcels);
    }

    public static AssistantCard tracking(PublicTrackingResponse tracking) {
        return new AssistantCard(Type.TRACKING, Objects.requireNonNull(tracking), null, null);
    }

    public static AssistantCard fee(Fee fee) {
        return new AssistantCard(Type.FEE, null, Objects.requireNonNull(fee), null);
    }

    public static AssistantCard parcels(List<ParcelSummary> parcels) {
        return new AssistantCard(Type.PARCELS, null, null, Objects.requireNonNull(parcels));
    }

    /** A delivery fee quote; the pricing rule behind it stays internal. */
    public record Fee(BigDecimal totalFee, String currency, List<Line> lines) {

        public Fee {
            lines = List.copyOf(lines);
        }

        /** One line of the breakdown, for example "Weight fee". */
        public record Line(String label, BigDecimal amount) {
        }
    }

    /**
     * One of the signed-in customer's recent parcels: no names, addresses or internal ids.
     *
     * @param statusLabel the status in plain words, e.g. "Out for delivery"
     */
    public record ParcelSummary(
            String trackingNumber,
            ParcelStatusCode status,
            String statusLabel,
            String fromCity,
            String toCity,
            Instant lastUpdatedAt) {
    }
}
