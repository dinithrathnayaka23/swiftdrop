-- Member 4 (parcel module).
-- Adds the parcel type the customer picks when booking (report FR2: "parcel type, size and weight").
-- The type describes the contents, so couriers know how to handle the parcel.

ALTER TABLE parcels ADD COLUMN parcel_type VARCHAR(20) NOT NULL DEFAULT 'PARCEL';

-- The default only fills rows that already exist (development databases); new parcels must say
-- their type.
ALTER TABLE parcels ALTER COLUMN parcel_type DROP DEFAULT;

ALTER TABLE parcels ADD CONSTRAINT ck_parcels_parcel_type
    CHECK (parcel_type IN ('DOCUMENT', 'PARCEL', 'FRAGILE', 'ELECTRONICS', 'PERISHABLE'));
