package com.swiftdrop.dispatch.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

/** Courier profile (vehicle and availability) linked to a COURIER user. Table {@code couriers}. */
@Entity
@Table(name = "couriers")
@Getter
@Setter
@NoArgsConstructor
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID id;

    /** {@code users.user_id} of the courier's account (user module). One courier profile per user. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "vehicle_type", nullable = false, length = 50)
    private String vehicleType;

    @Column(name = "vehicle_registration", nullable = false, length = 30)
    private String vehicleRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private CourierAvailability availabilityStatus = CourierAvailability.OFFLINE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Ids of the delivery zones ({@code delivery_zones.zone_id}, config module) this courier covers.
     * Table {@code courier_service_areas}. Lazy: read it inside a transaction.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "courier_service_areas", joinColumns = @JoinColumn(name = "courier_id"))
    @Column(name = "zone_id", nullable = false)
    @BatchSize(size = 50)
    private Set<UUID> serviceAreaIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
