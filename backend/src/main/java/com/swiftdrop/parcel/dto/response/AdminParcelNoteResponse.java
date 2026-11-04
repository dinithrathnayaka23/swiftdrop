package com.swiftdrop.parcel.dto.response;

import java.time.Instant;
import java.util.UUID;

/** An internal note on a parcel. Parcels have no notes yet; the list is always empty. */
public record AdminParcelNoteResponse(UUID id, String author, String text, Instant createdAt) {
}
