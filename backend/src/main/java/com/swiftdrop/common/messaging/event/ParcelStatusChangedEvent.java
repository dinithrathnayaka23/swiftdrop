package com.swiftdrop.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code swiftdrop.parcel.events} with routing key {@code parcel.status.changed}
 * after a parcel status change is committed (report section 4.1). Published by the parcel module;
 * consumed by live tracking, merchant webhooks and notifications.
 *
 * <p>Delivery is at least once, so consumers skip events whose {@code eventId} they have already
 * handled. {@code statusVersion} grows by one with every change of the same parcel, so a consumer
 * can ignore an older event that arrives after a newer one. Changing this record changes the
 * message contract: raise {@link #SCHEMA_VERSION} and agree the change with the consumers.
 *
 * <p>No personal data (names, phone numbers, addresses, proof) is included; consumers look up what
 * they need by ID.
 *
 * @param eventType       always {@link #EVENT_TYPE}
 * @param merchantId      the owning merchant, or {@code null} for a customer booking
 * @param previousStatus  parcel status code before the change
 * @param newStatus       parcel status code after the change
 * @param statusHistoryId the {@code parcel_status_history} row of this change
 * @param changedByUserId who made the change, or {@code null} for a system change
 */
public record ParcelStatusChangedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID parcelId,
        String trackingNumber,
        UUID merchantId,
        String previousStatus,
        String newStatus,
        long statusVersion,
        UUID statusHistoryId,
        UUID changedByUserId,
        Instant occurredAt) {

    public static final String EVENT_TYPE = "PARCEL_STATUS_CHANGED";
    public static final int SCHEMA_VERSION = 1;
}
