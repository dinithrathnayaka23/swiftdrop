package com.swiftdrop.tracking.mapper;

import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusHistoryEntry;
import com.swiftdrop.tracking.dto.response.PublicTrackingEventResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the public tracking view of a parcel. {@link ParcelDetails} and {@link StatusHistoryEntry}
 * hold personal data; only the tracking number, status codes, timestamps, the pickup and drop-off
 * city and the weight are copied.
 */
public final class PublicTrackingMapper {

    private PublicTrackingMapper() {
    }

    /**
     * @param history the parcel's status history in any order; the response lists it oldest first
     */
    public static PublicTrackingResponse toResponse(ParcelDetails parcel, List<StatusHistoryEntry> history) {
        List<PublicTrackingEventResponse> events = history.stream()
                .sorted(Comparator.comparing(StatusHistoryEntry::changedAt))
                .map(entry -> new PublicTrackingEventResponse(entry.status(), entry.changedAt()))
                .toList();
        return new PublicTrackingResponse(
                parcel.trackingNumber(),
                parcel.currentStatus(),
                lastUpdatedAt(parcel, events),
                parcel.createdAt(),
                parcel.pickupAddress().city(),
                parcel.dropoffAddress().city(),
                parcel.weightKg(),
                events);
    }

    /** When the status last changed; the parcel's own timestamp only if there is no history. */
    private static Instant lastUpdatedAt(ParcelDetails parcel, List<PublicTrackingEventResponse> events) {
        return events.isEmpty() ? parcel.updatedAt() : events.getLast().changedAt();
    }
}
