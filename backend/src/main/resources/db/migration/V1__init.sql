-- =====================================================================================
-- SwiftDrop baseline schema (matches the submitted ER diagram).
--
-- Tables are grouped by the backend module that owns them:
--   user          users
--   config        delivery_zones, zone_coverage_areas, pricing_rules, zone_pricing_adjustments
--   parcel        addresses, parcel_statuses, parcels, parcel_status_history, delivery_receipts
--   dispatch      couriers, delivery_assignments
--   merchant      merchants, merchant_api_credentials, merchant_webhooks, webhook_deliveries
--   notification  notifications
--
-- Later changes go in NEW migrations named by member number (e.g. V3_001__add_parcel_index.sql).
-- Never edit this file once it has been applied to a shared database. See docs/CONTRIBUTING.md.
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- user module
-- -------------------------------------------------------------------------------------

CREATE TABLE users (
    user_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name      VARCHAR(120) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(25),
    password_hash  TEXT,                                      -- NULL for Google-only accounts
    google_subject VARCHAR(255),                              -- Google account "sub" claim
    role           VARCHAR(20)  NOT NULL,
    account_status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role
        CHECK (role IN ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN')),
    CONSTRAINT ck_users_account_status
        CHECK (account_status IN ('ACTIVE', 'PENDING_APPROVAL', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT ck_users_has_credentials
        CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL)
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
CREATE UNIQUE INDEX ux_users_google_subject ON users (google_subject) WHERE google_subject IS NOT NULL;


-- -------------------------------------------------------------------------------------
-- config module: zones and pricing
-- -------------------------------------------------------------------------------------

CREATE TABLE delivery_zones (
    zone_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_name   VARCHAR(100) NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_delivery_zones_zone_name UNIQUE (zone_name)
);

CREATE TABLE zone_coverage_areas (
    coverage_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id     UUID         NOT NULL,
    district    VARCHAR(100) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    postal_code VARCHAR(15),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_zone_coverage_areas_zone
        FOREIGN KEY (zone_id) REFERENCES delivery_zones (zone_id) ON DELETE CASCADE
);

CREATE INDEX ix_zone_coverage_areas_zone_id ON zone_coverage_areas (zone_id);
CREATE INDEX ix_zone_coverage_areas_lookup ON zone_coverage_areas (district, city, postal_code);

CREATE TABLE pricing_rules (
    pricing_rule_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name             VARCHAR(120)  NOT NULL,
    base_fee              NUMERIC(10,2) NOT NULL,
    currency              CHAR(3)       NOT NULL DEFAULT 'LKR',
    included_weight_kg    NUMERIC(8,3)  NOT NULL DEFAULT 0,
    additional_fee_per_kg NUMERIC(10,2) NOT NULL DEFAULT 0,
    max_volume_cm3        NUMERIC(12,2),
    oversize_fee          NUMERIC(10,2) NOT NULL DEFAULT 0,
    effective_from        TIMESTAMPTZ   NOT NULL,
    effective_to          TIMESTAMPTZ,
    is_active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by_user_id    UUID          NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_pricing_rules_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
    CONSTRAINT ck_pricing_rules_amounts
        CHECK (base_fee >= 0 AND included_weight_kg >= 0 AND additional_fee_per_kg >= 0
               AND oversize_fee >= 0 AND (max_volume_cm3 IS NULL OR max_volume_cm3 > 0)),
    CONSTRAINT ck_pricing_rules_effective_period
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE zone_pricing_adjustments (
    zone_adjustment_id UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    pricing_rule_id    UUID          NOT NULL,
    pickup_zone_id     UUID          NOT NULL,
    delivery_zone_id   UUID          NOT NULL,
    adjustment_type    VARCHAR(20)   NOT NULL,
    adjustment_value   NUMERIC(10,2) NOT NULL,
    CONSTRAINT fk_zone_pricing_adjustments_rule
        FOREIGN KEY (pricing_rule_id) REFERENCES pricing_rules (pricing_rule_id) ON DELETE CASCADE,
    CONSTRAINT fk_zone_pricing_adjustments_pickup_zone
        FOREIGN KEY (pickup_zone_id) REFERENCES delivery_zones (zone_id),
    CONSTRAINT fk_zone_pricing_adjustments_delivery_zone
        FOREIGN KEY (delivery_zone_id) REFERENCES delivery_zones (zone_id),
    CONSTRAINT ux_zone_pricing_adjustments_route
        UNIQUE (pricing_rule_id, pickup_zone_id, delivery_zone_id),
    CONSTRAINT ck_zone_pricing_adjustments_type
        CHECK (adjustment_type IN ('FLAT', 'PERCENTAGE'))
);


-- -------------------------------------------------------------------------------------
-- parcel module: addresses, statuses, parcels, history, receipts
-- -------------------------------------------------------------------------------------

CREATE TABLE addresses (
    address_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_name  VARCHAR(120) NOT NULL,
    contact_phone VARCHAR(25)  NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city          VARCHAR(100) NOT NULL,
    district      VARCHAR(100) NOT NULL,
    postal_code   VARCHAR(15),
    zone_id       UUID,                                       -- resolved from zone_coverage_areas
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_addresses_zone
        FOREIGN KEY (zone_id) REFERENCES delivery_zones (zone_id)
);

CREATE INDEX ix_addresses_zone_id ON addresses (zone_id);

CREATE TABLE parcel_statuses (
    status_code   VARCHAR(30)  PRIMARY KEY,
    description   VARCHAR(150) NOT NULL,
    is_terminal   BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order SMALLINT     NOT NULL,
    CONSTRAINT ux_parcel_statuses_display_order UNIQUE (display_order)
);

-- Reference data: parcel lifecycle. Adjust via a new migration if the team changes the flow.
INSERT INTO parcel_statuses (status_code, description, is_terminal, display_order) VALUES
    ('BOOKED',           'Delivery booked, awaiting courier assignment', FALSE, 10),
    ('ASSIGNED',         'Courier assigned for pickup',                  FALSE, 20),
    ('PICKED_UP',        'Parcel collected from sender',                 FALSE, 30),
    ('IN_TRANSIT',       'Parcel on the way',                            FALSE, 40),
    ('OUT_FOR_DELIVERY', 'Courier is delivering the parcel',             FALSE, 50),
    ('DELIVERED',        'Parcel delivered to recipient',                TRUE,  60),
    ('FAILED_ATTEMPT',   'Delivery attempt failed',                      FALSE, 70),
    ('RETURNED',         'Parcel returned to sender',                    TRUE,  80),
    ('CANCELLED',        'Delivery cancelled',                           TRUE,  90);

CREATE TABLE parcels (
    parcel_id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_number          VARCHAR(40)   NOT NULL,
    customer_user_id         UUID,                            -- set for customer bookings
    merchant_id              UUID,                            -- set for merchant API orders
    external_order_reference VARCHAR(100),                    -- merchant's own order id
    pickup_address_id        UUID          NOT NULL,
    dropoff_address_id       UUID          NOT NULL,
    weight_kg                NUMERIC(8,3)  NOT NULL,
    length_cm                NUMERIC(8,2),
    width_cm                 NUMERIC(8,2),
    height_cm                NUMERIC(8,2),
    content_description      TEXT,
    current_status_code      VARCHAR(30)   NOT NULL DEFAULT 'BOOKED',
    pricing_rule_id          UUID,
    quoted_fee               NUMERIC(10,2) NOT NULL,
    fee_currency             CHAR(3)       NOT NULL DEFAULT 'LKR',
    delivery_instructions    TEXT,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_parcels_tracking_number UNIQUE (tracking_number),
    CONSTRAINT fk_parcels_customer
        FOREIGN KEY (customer_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_parcels_pickup_address
        FOREIGN KEY (pickup_address_id) REFERENCES addresses (address_id),
    CONSTRAINT fk_parcels_dropoff_address
        FOREIGN KEY (dropoff_address_id) REFERENCES addresses (address_id),
    CONSTRAINT fk_parcels_current_status
        FOREIGN KEY (current_status_code) REFERENCES parcel_statuses (status_code),
    CONSTRAINT fk_parcels_pricing_rule
        FOREIGN KEY (pricing_rule_id) REFERENCES pricing_rules (pricing_rule_id),
    CONSTRAINT ck_parcels_owner
        CHECK (customer_user_id IS NOT NULL OR merchant_id IS NOT NULL),
    CONSTRAINT ck_parcels_measurements
        CHECK (weight_kg > 0 AND (length_cm IS NULL OR length_cm > 0)
               AND (width_cm IS NULL OR width_cm > 0) AND (height_cm IS NULL OR height_cm > 0)),
    CONSTRAINT ck_parcels_quoted_fee CHECK (quoted_fee >= 0)
);
-- fk_parcels_merchant is added after the merchants table below.

CREATE INDEX ix_parcels_customer_user_id ON parcels (customer_user_id);
CREATE INDEX ix_parcels_merchant_id ON parcels (merchant_id);
CREATE INDEX ix_parcels_current_status_code ON parcels (current_status_code);
CREATE UNIQUE INDEX ux_parcels_merchant_order_ref
    ON parcels (merchant_id, external_order_reference)
    WHERE merchant_id IS NOT NULL AND external_order_reference IS NOT NULL;

CREATE TABLE parcel_status_history (
    status_history_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id            UUID         NOT NULL,
    previous_status_code VARCHAR(30),                         -- NULL for the first entry
    status_code          VARCHAR(30)  NOT NULL,
    changed_by_user_id   UUID,                                -- NULL for system/merchant API changes
    note                 TEXT,
    failure_reason       TEXT,
    recipient_name       VARCHAR(120),
    proof_file_url       TEXT,
    changed_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_parcel_status_history_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id) ON DELETE CASCADE,
    CONSTRAINT fk_parcel_status_history_previous_status
        FOREIGN KEY (previous_status_code) REFERENCES parcel_statuses (status_code),
    CONSTRAINT fk_parcel_status_history_status
        FOREIGN KEY (status_code) REFERENCES parcel_statuses (status_code),
    CONSTRAINT fk_parcel_status_history_changed_by
        FOREIGN KEY (changed_by_user_id) REFERENCES users (user_id)
);

CREATE INDEX ix_parcel_status_history_parcel ON parcel_status_history (parcel_id, changed_at);

CREATE TABLE delivery_receipts (
    receipt_id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id      UUID        NOT NULL,
    receipt_number VARCHAR(50) NOT NULL,
    document_url   TEXT        NOT NULL,
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_delivery_receipts_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id) ON DELETE CASCADE,
    CONSTRAINT ux_delivery_receipts_parcel UNIQUE (parcel_id),
    CONSTRAINT ux_delivery_receipts_receipt_number UNIQUE (receipt_number)
);


-- -------------------------------------------------------------------------------------
-- dispatch module: couriers and assignments
-- -------------------------------------------------------------------------------------

CREATE TABLE couriers (
    courier_id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL,
    vehicle_type         VARCHAR(50) NOT NULL,
    vehicle_registration VARCHAR(30) NOT NULL,
    availability_status  VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_couriers_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT ux_couriers_user UNIQUE (user_id),
    CONSTRAINT ux_couriers_vehicle_registration UNIQUE (vehicle_registration),
    CONSTRAINT ck_couriers_availability_status
        CHECK (availability_status IN ('AVAILABLE', 'BUSY', 'OFFLINE'))
);

CREATE TABLE delivery_assignments (
    assignment_id       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id           UUID        NOT NULL,
    courier_id          UUID        NOT NULL,
    assigned_by_user_id UUID        NOT NULL,
    assignment_status   VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    reassignment_reason TEXT,
    CONSTRAINT fk_delivery_assignments_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id),
    CONSTRAINT fk_delivery_assignments_courier
        FOREIGN KEY (courier_id) REFERENCES couriers (courier_id),
    CONSTRAINT fk_delivery_assignments_assigned_by
        FOREIGN KEY (assigned_by_user_id) REFERENCES users (user_id),
    CONSTRAINT ck_delivery_assignments_status
        CHECK (assignment_status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'REASSIGNED', 'CANCELLED')),
    CONSTRAINT ck_delivery_assignments_period
        CHECK (ended_at IS NULL OR ended_at >= assigned_at)
);

CREATE INDEX ix_delivery_assignments_parcel_id ON delivery_assignments (parcel_id);
CREATE INDEX ix_delivery_assignments_courier_id ON delivery_assignments (courier_id);
-- A parcel can have only one open (not ended) assignment at a time.
CREATE UNIQUE INDEX ux_delivery_assignments_open_per_parcel
    ON delivery_assignments (parcel_id) WHERE ended_at IS NULL;


-- -------------------------------------------------------------------------------------
-- merchant module: merchants, API credentials, webhooks
-- -------------------------------------------------------------------------------------

CREATE TABLE merchants (
    merchant_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name         VARCHAR(180) NOT NULL,
    registration_no       VARCHAR(80),
    contact_email         VARCHAR(255) NOT NULL,
    contact_phone         VARCHAR(25)  NOT NULL,
    business_address      TEXT         NOT NULL,
    approval_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    registered_by_user_id UUID         NOT NULL,
    reviewed_by_user_id   UUID,
    reviewed_at           TIMESTAMPTZ,
    rejection_reason      TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_merchants_registered_by
        FOREIGN KEY (registered_by_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_merchants_reviewed_by
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id),
    CONSTRAINT ux_merchants_registration_no UNIQUE (registration_no),
    CONSTRAINT ck_merchants_approval_status
        CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

CREATE INDEX ix_merchants_registered_by ON merchants (registered_by_user_id);

ALTER TABLE parcels
    ADD CONSTRAINT fk_parcels_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id);

CREATE TABLE merchant_api_credentials (
    api_credential_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id       UUID         NOT NULL,
    key_identifier    VARCHAR(100) NOT NULL,                  -- public prefix, used to look up the key
    api_key_hash      TEXT         NOT NULL,                  -- never store the raw key
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    last_used_at      TIMESTAMPTZ,
    CONSTRAINT fk_merchant_api_credentials_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id) ON DELETE CASCADE,
    CONSTRAINT ux_merchant_api_credentials_key_identifier UNIQUE (key_identifier)
);

CREATE INDEX ix_merchant_api_credentials_merchant_id ON merchant_api_credentials (merchant_id);

CREATE TABLE merchant_webhooks (
    webhook_id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id              UUID        NOT NULL,
    endpoint_url             TEXT        NOT NULL,
    event_types              TEXT[]      NOT NULL DEFAULT '{}',
    signing_secret_encrypted TEXT        NOT NULL,
    is_active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_merchant_webhooks_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (merchant_id) ON DELETE CASCADE
);

CREATE INDEX ix_merchant_webhooks_merchant_id ON merchant_webhooks (merchant_id);

CREATE TABLE webhook_deliveries (
    webhook_delivery_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id            UUID        NOT NULL,
    parcel_id             UUID        NOT NULL,
    status_history_id     UUID,
    event_type            VARCHAR(50) NOT NULL,
    payload_json          JSONB       NOT NULL,
    delivery_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count           INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at       TIMESTAMPTZ,
    last_http_status      INTEGER,
    last_response_excerpt TEXT,
    last_error_message    TEXT,
    next_retry_at         TIMESTAMPTZ,
    delivered_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_webhook_deliveries_webhook
        FOREIGN KEY (webhook_id) REFERENCES merchant_webhooks (webhook_id) ON DELETE CASCADE,
    CONSTRAINT fk_webhook_deliveries_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id),
    CONSTRAINT fk_webhook_deliveries_status_history
        FOREIGN KEY (status_history_id) REFERENCES parcel_status_history (status_history_id),
    CONSTRAINT ck_webhook_deliveries_status
        CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_webhook_deliveries_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX ix_webhook_deliveries_webhook_id ON webhook_deliveries (webhook_id);
-- Lets the retry worker find due deliveries quickly.
CREATE INDEX ix_webhook_deliveries_due
    ON webhook_deliveries (next_retry_at) WHERE delivery_status = 'PENDING';


-- -------------------------------------------------------------------------------------
-- notification module
-- -------------------------------------------------------------------------------------

CREATE TABLE notifications (
    notification_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID,                                   -- NULL when the recipient has no account
    parcel_id         UUID,
    status_history_id UUID,
    channel           VARCHAR(20)  NOT NULL,
    recipient         VARCHAR(255) NOT NULL,                  -- email address or phone number
    subject           VARCHAR(255),
    message           TEXT         NOT NULL,
    delivery_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at           TIMESTAMPTZ,
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_notifications_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id),
    CONSTRAINT fk_notifications_status_history
        FOREIGN KEY (status_history_id) REFERENCES parcel_status_history (status_history_id),
    CONSTRAINT ck_notifications_channel
        CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT ck_notifications_delivery_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX ix_notifications_user_id ON notifications (user_id);
CREATE INDEX ix_notifications_parcel_id ON notifications (parcel_id);
