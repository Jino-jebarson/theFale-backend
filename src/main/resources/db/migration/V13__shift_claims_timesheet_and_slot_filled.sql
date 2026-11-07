-- V13: Extend shift_claims with actual_hours and support TIMESHEET_SUBMITTED & SLOT_FILLED statuses
ALTER TABLE shift_claims ADD COLUMN IF NOT EXISTS actual_hours INT;

ALTER TABLE shift_claims DROP CONSTRAINT IF EXISTS shift_claims_status_check;
ALTER TABLE shift_claims ADD CONSTRAINT shift_claims_status_check
    CHECK (status IN ('CLAIM_PENDING_ADMIN', 'CLAIMED', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'TIMESHEET_SUBMITTED', 'COMPLETED', 'NO_SHOW', 'CANCELLED', 'DISPUTED', 'REJECTED', 'SLOT_FILLED'));

ALTER TABLE locum_shifts DROP CONSTRAINT IF EXISTS locum_shifts_status_check;
ALTER TABLE locum_shifts ADD CONSTRAINT locum_shifts_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'OPEN', 'FILLED', 'BOOKED', 'CANCELLED', 'REJECTED', 'COMPLETED'));

