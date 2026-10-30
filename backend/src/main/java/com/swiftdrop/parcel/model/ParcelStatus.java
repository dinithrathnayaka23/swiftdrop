package com.swiftdrop.parcel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reference data for the parcel lifecycle (BOOKED, ASSIGNED, ..., DELIVERED). Seeded by
 * {@code V1__init.sql}. Table {@code parcel_statuses}.
 */
@Entity
@Table(name = "parcel_statuses")
@Getter
@Setter
@NoArgsConstructor
public class ParcelStatus {

    @Id
    @Column(name = "status_code", nullable = false, updatable = false, length = 30)
    private String statusCode;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    /** {@code true} when no further status changes are expected (e.g. DELIVERED). */
    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;
}
