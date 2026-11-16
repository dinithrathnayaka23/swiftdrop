package com.swiftdrop.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * Proof of delivery from the courier app: who received the parcel and, optionally, where the photo
 * is stored. The parcel's DELIVERED status is in the parcel module's history ({@code statusHistoryId}).
 * Table {@code delivery_proofs}.
 *
 * <p>The id is chosen before saving (the photo URL in the history entry contains it), so the entity
 * tells Spring Data it is new instead of letting it look the id up first.
 */
@Entity
@Table(name = "delivery_proofs")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryProof implements Persistable<UUID> {

    @Id
    @Column(name = "proof_id", nullable = false, updatable = false)
    private UUID id;

    /** {@code parcels.parcel_id} (parcel module). */
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    /** The DELIVERED entry in {@code parcel_status_history} (parcel module). */
    @Column(name = "status_history_id", nullable = false, updatable = false)
    private UUID statusHistoryId;

    /** {@code users.user_id} of the courier who delivered it. */
    @Column(name = "courier_user_id", nullable = false, updatable = false)
    private UUID courierUserId;

    @Column(name = "recipient_name", nullable = false, updatable = false, length = 120)
    private String recipientName;

    /** Where the photo is, relative to the proof storage folder; {@code null} without a photo. */
    @Column(name = "photo_storage_key", updatable = false)
    private String photoStorageKey;

    @Column(name = "photo_content_type", updatable = false, length = 50)
    private String photoContentType;

    @Column(name = "photo_size_bytes", updatable = false)
    private Long photoSizeBytes;

    @Column(name = "photo_sha256", updatable = false, length = 64)
    private String photoSha256;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Transient
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    /** Loaded or saved rows are no longer new. */
    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    public boolean hasPhoto() {
        return photoStorageKey != null;
    }
}
