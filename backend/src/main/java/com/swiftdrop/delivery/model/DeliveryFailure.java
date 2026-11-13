package com.swiftdrop.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A failed delivery reported from the courier app: which reason the courier picked and their note.
 * The parcel's FAILED status itself is in the parcel module's history ({@code statusHistoryId}).
 * A parcel can have more than one (after a retry). Table {@code delivery_failures}.
 */
@Entity
@Table(name = "delivery_failures")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "failure_id", nullable = false, updatable = false)
    private UUID id;

    /** {@code parcels.parcel_id} (parcel module). */
    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    /** The FAILED entry in {@code parcel_status_history} (parcel module). */
    @Column(name = "status_history_id", nullable = false, updatable = false)
    private UUID statusHistoryId;

    /** {@code users.user_id} of the courier who reported it. */
    @Column(name = "courier_user_id", nullable = false, updatable = false)
    private UUID courierUserId;

    /** {@code failure_reasons.reason_code}. */
    @Column(name = "reason_code", nullable = false, updatable = false, length = 40)
    private String reasonCode;

    @Column(name = "note", updatable = false)
    private String note;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
