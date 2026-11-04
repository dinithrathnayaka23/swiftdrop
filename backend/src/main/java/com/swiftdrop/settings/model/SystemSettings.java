package com.swiftdrop.settings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Platform-wide settings; the table has exactly one row. Table {@code system_settings} (V1_001). */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
public class SystemSettings {

    /** The id of the only row. */
    public static final short ID = 1;

    @Id
    @Column(name = "settings_id", nullable = false, updatable = false)
    private Short id;

    // ---- Company information ----

    @Column(name = "company_name", nullable = false, length = 120)
    private String companyName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 25)
    private String contactPhone;

    @Column(name = "company_address", length = 255)
    private String companyAddress;

    // ---- Application settings ----

    /** IANA time zone, e.g. Asia/Colombo. */
    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_format", nullable = false, length = 12)
    private DateFormat dateFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_format", nullable = false, length = 5)
    private TimeFormat timeFormat;

    @Column(name = "items_per_page", nullable = false)
    private short itemsPerPage;

    // ---- Parcel settings ----

    /** A parcels.parcel_type value (DOCUMENT, PARCEL, FRAGILE, ELECTRONICS, PERISHABLE). */
    @Column(name = "default_parcel_type", nullable = false, length = 20)
    private String defaultParcelType;

    @Column(name = "max_parcel_weight_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal maxParcelWeightKg;

    @Column(name = "max_parcel_dimension_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxParcelDimensionCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_delivery_action", nullable = false, length = 25)
    private FailedDeliveryAction failedDeliveryAction;

    @Column(name = "auto_assign_courier", nullable = false)
    private boolean autoAssignCourier;

    // ---- Email notifications to the admin ----

    @Column(name = "notify_failed_deliveries", nullable = false)
    private boolean notifyFailedDeliveries;

    @Column(name = "notify_new_merchants", nullable = false)
    private boolean notifyNewMerchants;

    @Column(name = "notify_courier_issues", nullable = false)
    private boolean notifyCourierIssues;

    @Column(name = "daily_summary_report", nullable = false)
    private boolean dailySummaryReport;

    // ---- Maintenance ----

    /** When on, only the admin can use the system. */
    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;
}
