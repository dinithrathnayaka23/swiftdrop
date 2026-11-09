-- Member 6 (dispatch module).
--
-- 1. Courier availability now matches the Couriers screen: ONLINE / ON_BREAK / OFFLINE
--    (V1 had AVAILABLE / BUSY / OFFLINE). No courier data exists outside development databases,
--    so the old values are simply mapped across.
-- 2. courier_service_areas links a courier to the delivery zones they cover (the zones come from
--    the config module; only the zone id is stored here).

ALTER TABLE couriers DROP CONSTRAINT ck_couriers_availability_status;

UPDATE couriers SET availability_status = 'ONLINE'   WHERE availability_status = 'AVAILABLE';
UPDATE couriers SET availability_status = 'ON_BREAK' WHERE availability_status = 'BUSY';

ALTER TABLE couriers ADD CONSTRAINT ck_couriers_availability_status
    CHECK (availability_status IN ('ONLINE', 'ON_BREAK', 'OFFLINE'));

CREATE TABLE courier_service_areas (
    courier_id UUID NOT NULL,
    zone_id    UUID NOT NULL,
    CONSTRAINT pk_courier_service_areas PRIMARY KEY (courier_id, zone_id),
    CONSTRAINT fk_courier_service_areas_courier
        FOREIGN KEY (courier_id) REFERENCES couriers (courier_id) ON DELETE CASCADE,
    CONSTRAINT fk_courier_service_areas_zone
        FOREIGN KEY (zone_id) REFERENCES delivery_zones (zone_id)
);

CREATE INDEX ix_courier_service_areas_zone_id ON courier_service_areas (zone_id);
