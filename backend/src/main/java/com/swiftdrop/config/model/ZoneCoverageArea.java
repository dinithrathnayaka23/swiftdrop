package com.swiftdrop.config.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A district/city/postal code covered by a delivery zone. Table {@code zone_coverage_areas}. */
@Entity
@Table(name = "zone_coverage_areas")
@Getter
@Setter
@NoArgsConstructor
public class ZoneCoverageArea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "coverage_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private DeliveryZone zone;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", length = 15)
    private String postalCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
