-- V10_001__notifications_read_and_preferences.sql
-- Member 10: Notification read state and customer notification preferences

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id                     UUID PRIMARY KEY,
    email_enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_picked_up         BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_in_transit        BOOLEAN NOT NULL DEFAULT FALSE,
    notify_on_out_for_delivery  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_delivered         BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_failed_attempt    BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_emails            BOOLEAN NOT NULL DEFAULT FALSE,
    system_maintenance_alerts   BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_notifications_user_read ON notifications (user_id, is_read);

