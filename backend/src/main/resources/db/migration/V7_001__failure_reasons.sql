-- Member 7 (delivery module, courier app).
--
-- Failed deliveries: the reasons a courier can pick, and one row per failed delivery reported from
-- the courier app. The parcel's own status change (FAILED, with the reason text) stays in
-- parcel_status_history; delivery_failures keeps the reason code and the courier's note next to it.

CREATE TABLE failure_reasons (
    reason_code   VARCHAR(40)  PRIMARY KEY,
    label         VARCHAR(100) NOT NULL,
    note_required BOOLEAN      NOT NULL DEFAULT FALSE,   -- the courier must explain (e.g. OTHER)
    display_order SMALLINT     NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,    -- inactive reasons are no longer offered
    CONSTRAINT ux_failure_reasons_display_order UNIQUE (display_order)
);

INSERT INTO failure_reasons (reason_code, label, note_required, display_order) VALUES
    ('RECIPIENT_UNAVAILABLE', 'Recipient unavailable', FALSE, 10),
    ('WRONG_ADDRESS',         'Wrong address',         FALSE, 20),
    ('REFUSED',               'Refused by recipient',  FALSE, 30),
    ('ACCESS_ISSUE',          'Access issue',          FALSE, 40),
    ('DAMAGED',               'Parcel damaged',        TRUE,  50),
    ('OTHER',                 'Other',                 TRUE,  60);

-- No unique constraint on parcel_id: a parcel can fail again after a retry.
CREATE TABLE delivery_failures (
    failure_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id         UUID         NOT NULL,
    status_history_id UUID         NOT NULL,             -- the FAILED entry in parcel_status_history
    courier_user_id   UUID         NOT NULL,             -- the courier's account (users.user_id)
    reason_code       VARCHAR(40)  NOT NULL,
    note              TEXT,
    recorded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_delivery_failures_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_failures_status_history
        FOREIGN KEY (status_history_id) REFERENCES parcel_status_history (status_history_id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_failures_courier_user
        FOREIGN KEY (courier_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_delivery_failures_reason
        FOREIGN KEY (reason_code) REFERENCES failure_reasons (reason_code),
    CONSTRAINT ux_delivery_failures_status_history UNIQUE (status_history_id)
);

CREATE INDEX ix_delivery_failures_parcel ON delivery_failures (parcel_id);
-- The courier's Completed Deliveries, newest first.
CREATE INDEX ix_delivery_failures_courier_recorded ON delivery_failures (courier_user_id, recorded_at);
