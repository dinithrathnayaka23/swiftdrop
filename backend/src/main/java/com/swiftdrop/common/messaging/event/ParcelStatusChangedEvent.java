package com.swiftdrop.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code swiftdrop.parcel.events} with routing key {@code parcel.status.changed}
 * whenever a parcel changes status.
 *
 * <p><b>PLACEHOLDER - TO BE FINALIZED BY MEMBERS 3, 6, 9.</b> Field names and types
 * (in particular whether statuses become a shared enum) are not agreed yet. Changing
 * this record changes the message contract between publisher and consumers, so
 * agree changes in a PR reviewed by all three.
 *
 * <p>Do not add personal data (names, phone numbers, addresses) to events; consumers
 * should look those up by ID.
 */
public record ParcelStatusChangedEvent(
        UUID parcelId,
        String trackingNumber,
        String oldStatus,
        String newStatus,
        Instant timestamp,
        UUID actorId) {
}
