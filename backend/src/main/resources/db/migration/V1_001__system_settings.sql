-- Member 1 (identity / system settings).
-- Admin: System Settings. One row holds the platform-wide settings the admin edits on the System
-- Settings screen; other modules read them (parcel limits, failed-delivery handling, notification
-- switches) and maintenance mode keeps everyone but the admin out. Not in the original ER diagram;
-- see docs/DATA_MODEL.md.

CREATE TABLE system_settings (
    -- Always 1: there is exactly one settings row.
    settings_id               SMALLINT      PRIMARY KEY DEFAULT 1,

    -- Company information (shown to users, e.g. support contact)
    company_name              VARCHAR(120)  NOT NULL DEFAULT 'SwiftDrop',
    contact_email             VARCHAR(255)  NOT NULL DEFAULT 'support@swiftdrop.lk',
    contact_phone             VARCHAR(25),
    company_address           VARCHAR(255),

    -- Application settings
    time_zone                 VARCHAR(50)   NOT NULL DEFAULT 'Asia/Colombo',
    date_format               VARCHAR(12)   NOT NULL DEFAULT 'DD_MMM_YYYY',
    time_format               VARCHAR(5)    NOT NULL DEFAULT 'H12',
    items_per_page            SMALLINT      NOT NULL DEFAULT 10,

    -- Parcel settings
    default_parcel_type       VARCHAR(20)   NOT NULL DEFAULT 'PARCEL',
    max_parcel_weight_kg      NUMERIC(8,3)  NOT NULL DEFAULT 50,
    max_parcel_dimension_cm   NUMERIC(8,2)  NOT NULL DEFAULT 120,
    failed_delivery_action    VARCHAR(25)   NOT NULL DEFAULT 'RETRY_DELIVERY',
    auto_assign_courier       BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Email notifications to the admin
    notify_failed_deliveries  BOOLEAN       NOT NULL DEFAULT TRUE,
    notify_new_merchants      BOOLEAN       NOT NULL DEFAULT TRUE,
    notify_courier_issues     BOOLEAN       NOT NULL DEFAULT TRUE,
    daily_summary_report      BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Maintenance
    maintenance_mode          BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Optimistic locking: an update must name the version it was based on.
    version                   BIGINT        NOT NULL DEFAULT 0,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by_user_id        UUID,

    CONSTRAINT ck_system_settings_single_row CHECK (settings_id = 1),
    CONSTRAINT ck_system_settings_date_format
        CHECK (date_format IN ('DD_MMM_YYYY', 'DD_MM_YYYY', 'MM_DD_YYYY', 'YYYY_MM_DD')),
    CONSTRAINT ck_system_settings_time_format CHECK (time_format IN ('H12', 'H24')),
    CONSTRAINT ck_system_settings_items_per_page CHECK (items_per_page IN (10, 20, 50, 100)),
    CONSTRAINT ck_system_settings_parcel_type
        CHECK (default_parcel_type IN ('DOCUMENT', 'PARCEL', 'FRAGILE', 'ELECTRONICS', 'PERISHABLE')),
    CONSTRAINT ck_system_settings_parcel_limits
        CHECK (max_parcel_weight_kg > 0 AND max_parcel_weight_kg <= 1000
               AND max_parcel_dimension_cm > 0 AND max_parcel_dimension_cm <= 1000),
    CONSTRAINT ck_system_settings_failed_action
        CHECK (failed_delivery_action IN ('RETRY_DELIVERY', 'RETURN_TO_SENDER', 'HOLD_FOR_COLLECTION')),
    CONSTRAINT fk_system_settings_updated_by FOREIGN KEY (updated_by_user_id)
        REFERENCES users (user_id) ON DELETE SET NULL
);

COMMENT ON TABLE system_settings IS
    'Platform-wide settings edited on Admin: System Settings. Exactly one row (settings_id = 1).';

INSERT INTO system_settings (settings_id) VALUES (1);
