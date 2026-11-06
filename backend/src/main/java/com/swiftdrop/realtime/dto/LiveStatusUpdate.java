package com.swiftdrop.realtime.dto;

import java.time.Instant;

/**
 * Data of a {@code status} server-sent event, pushed when a parcel's status changes. Matches the
 * frontend's {@code LiveStatusUpdate} (frontend/src/features/shared-live/types.ts).
 *
 * <p>Built from {@code ParcelStatusChangedEvent}, so it holds no personal data. The browser
 * applies it only if {@code statusVersion} is newer than what it shows.
 *
 * @param previousStatus {@code null} for the first status (CREATED)
 */
public record LiveStatusUpdate(
        String trackingNumber,
        String previousStatus,
        String status,
        long statusVersion,
        Instant changedAt) {

    /** Name of the server-sent event that carries this record. */
    public static final String EVENT_NAME = "status";
}
