package com.swiftdrop.parcel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One entry in a parcel's timeline, including proof of delivery or failure details.
 * Table {@code parcel_status_history}.
 */
@Entity
@Table(name = "parcel_status_history")
@Getter
@Setter
@NoArgsConstructor
public class ParcelStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "status_history_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    /** {@code null} for the first entry of a parcel. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_status_code")
    private ParcelStatus previousStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_code", nullable = false)
    private ParcelStatus status;

    /** {@code users.user_id} who made the change (user module); {@code null} for system changes. */
    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Column(name = "proof_file_url")
    private String proofFileUrl;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
