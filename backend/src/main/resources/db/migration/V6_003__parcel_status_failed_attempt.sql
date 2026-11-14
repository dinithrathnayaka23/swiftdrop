-- Member 6 (dispatch), agreed with Member 4 (parcel module owner).
--
-- A failed delivery attempt no longer ends the parcel straight away. The courier reports the attempt
-- and the dispatcher decides:
--
--   CREATED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED
--   PICKED_UP / IN_TRANSIT / OUT_FOR_DELIVERY -> FAILED_ATTEMPT   (courier; reason recorded)
--   FAILED_ATTEMPT -> CREATED                                     (dispatcher retries)
--   FAILED_ATTEMPT -> FAILED                                      (dispatcher closes; reason recorded)
--
-- The allowed changes live in ParcelStatusCode; this table only lists the statuses. DELIVERED and
-- FAILED stay the only end states. Display order 55 sits between DELIVERED (50) and FAILED (60).

INSERT INTO parcel_statuses (status_code, description, is_terminal, display_order) VALUES
    ('FAILED_ATTEMPT', 'Delivery attempt failed, waiting for the dispatcher', FALSE, 55);
