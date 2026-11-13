package com.swiftdrop.delivery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reason a courier can give for a failed delivery (Recipient unavailable, Wrong address, ...).
 * Seeded by {@code V7_001__failure_reasons.sql}. Table {@code failure_reasons}.
 */
@Entity
@Table(name = "failure_reasons")
@Getter
@Setter
@NoArgsConstructor
public class FailureReason {

    @Id
    @Column(name = "reason_code", nullable = false, updatable = false, length = 40)
    private String code;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** The courier must add a note with this reason (e.g. OTHER). */
    @Column(name = "note_required", nullable = false)
    private boolean noteRequired;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    /** Inactive reasons are no longer offered or accepted. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
