-- Member 7 (delivery module, courier app).
--
-- Proof of delivery: who received the parcel and, optionally, a photo taken by the courier. The
-- parcel's DELIVERED status itself stays in parcel_status_history (with the recipient's name and the
-- photo's URL, /proofs/{proof_id}/photo); this row points at that history entry and says where the
-- photo file is stored. The photo is re-encoded as JPEG without metadata (no GPS) before it is stored.

CREATE TABLE delivery_proofs (
    proof_id           UUID         PRIMARY KEY,                -- set by the app: the photo URL uses it
    parcel_id          UUID         NOT NULL,
    status_history_id  UUID         NOT NULL,                   -- the DELIVERED entry in parcel_status_history
    courier_user_id    UUID         NOT NULL,                   -- the courier's account (users.user_id)
    recipient_name     VARCHAR(120) NOT NULL,
    photo_storage_key  VARCHAR(255),                            -- path under the proof storage folder
    photo_content_type VARCHAR(50),
    photo_size_bytes   BIGINT,
    photo_sha256       VARCHAR(64),                             -- hex SHA-256 of the stored file
    captured_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_delivery_proofs_parcel
        FOREIGN KEY (parcel_id) REFERENCES parcels (parcel_id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_proofs_status_history
        FOREIGN KEY (status_history_id) REFERENCES parcel_status_history (status_history_id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_proofs_courier_user
        FOREIGN KEY (courier_user_id) REFERENCES users (user_id),
    CONSTRAINT ux_delivery_proofs_status_history UNIQUE (status_history_id),
    -- A photo is all four columns or none of them.
    CONSTRAINT ck_delivery_proofs_photo CHECK (
        (photo_storage_key IS NULL AND photo_content_type IS NULL AND photo_size_bytes IS NULL
            AND photo_sha256 IS NULL)
        OR (photo_storage_key IS NOT NULL AND photo_content_type IS NOT NULL AND photo_size_bytes > 0
            AND photo_sha256 IS NOT NULL))
);

CREATE INDEX ix_delivery_proofs_parcel ON delivery_proofs (parcel_id);
-- The courier's Completed Deliveries, newest first.
CREATE INDEX ix_delivery_proofs_courier_captured ON delivery_proofs (courier_user_id, captured_at);
