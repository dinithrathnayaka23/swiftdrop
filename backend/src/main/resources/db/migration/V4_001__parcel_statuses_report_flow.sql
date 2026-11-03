-- Member 4 (parcel module).
-- Replaces the parcel lifecycle seeded by V1 with the six statuses agreed in the final report
-- (section 3.6, Figure 8):
--
--   CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
--   PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY -> FAILED (reason recorded)
--
-- DELIVERED and FAILED are end states. Courier assignment is recorded in delivery_assignments,
-- so it is no longer a parcel status.

INSERT INTO parcel_statuses (status_code, description, is_terminal, display_order) VALUES
    ('CREATED', 'Delivery booked, waiting for pickup',        FALSE, 1),
    ('FAILED',  'Delivery failed; the reason is recorded',     TRUE,  2);

-- Move any existing rows (development databases) to the new codes before the old ones go.
UPDATE parcels SET current_status_code = 'CREATED'
    WHERE current_status_code IN ('BOOKED', 'ASSIGNED');
UPDATE parcels SET current_status_code = 'FAILED'
    WHERE current_status_code IN ('FAILED_ATTEMPT', 'RETURNED', 'CANCELLED');

UPDATE parcel_status_history SET status_code = 'CREATED'
    WHERE status_code IN ('BOOKED', 'ASSIGNED');
UPDATE parcel_status_history SET status_code = 'FAILED'
    WHERE status_code IN ('FAILED_ATTEMPT', 'RETURNED', 'CANCELLED');
UPDATE parcel_status_history SET previous_status_code = 'CREATED'
    WHERE previous_status_code IN ('BOOKED', 'ASSIGNED');
UPDATE parcel_status_history SET previous_status_code = 'FAILED'
    WHERE previous_status_code IN ('FAILED_ATTEMPT', 'RETURNED', 'CANCELLED');

DELETE FROM parcel_statuses
    WHERE status_code IN ('BOOKED', 'ASSIGNED', 'FAILED_ATTEMPT', 'RETURNED', 'CANCELLED');

-- One statement per row, in this order, so ux_parcel_statuses_display_order never clashes.
UPDATE parcel_statuses SET display_order = 10 WHERE status_code = 'CREATED';
UPDATE parcel_statuses SET display_order = 20 WHERE status_code = 'PICKED_UP';
UPDATE parcel_statuses SET display_order = 30 WHERE status_code = 'IN_TRANSIT';
UPDATE parcel_statuses SET display_order = 40 WHERE status_code = 'OUT_FOR_DELIVERY';
UPDATE parcel_statuses SET display_order = 50 WHERE status_code = 'DELIVERED';
UPDATE parcel_statuses SET display_order = 60 WHERE status_code = 'FAILED';

ALTER TABLE parcels ALTER COLUMN current_status_code SET DEFAULT 'CREATED';
