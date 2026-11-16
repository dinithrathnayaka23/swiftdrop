-- Member 6 (dispatch module).
--
-- A delivery assignment can end as FAILED: the courier's delivery attempt ended with the parcel in
-- the FAILED status. It is an end state like COMPLETED, but it must not count as a completed delivery.

ALTER TABLE delivery_assignments DROP CONSTRAINT ck_delivery_assignments_status;

ALTER TABLE delivery_assignments ADD CONSTRAINT ck_delivery_assignments_status
    CHECK (assignment_status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'REASSIGNED', 'CANCELLED'));
