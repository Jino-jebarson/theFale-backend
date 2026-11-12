-- V19: Shift flow hardening, 1st-shift fee waivers, 1st-shift admin gate, floor PIN, disputes, and post-shift ratings

ALTER TABLE locum_shifts
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS facility_fee_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS facility_fee_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS auto_approved BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE locum_shifts DROP CONSTRAINT IF EXISTS locum_shifts_status_check;
ALTER TABLE locum_shifts ADD CONSTRAINT locum_shifts_status_check
    CHECK (status IN ('PENDING_APPROVAL', 'OPEN', 'FILLED', 'BOOKED', 'CANCELLED', 'REJECTED', 'COMPLETED', 'EXPIRED'));

ALTER TABLE shift_claims
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS check_in_pin VARCHAR(6),
    ADD COLUMN IF NOT EXISTS dispute_reason TEXT,
    ADD COLUMN IF NOT EXISTS disputed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS disputed_by VARCHAR(30),
    ADD COLUMN IF NOT EXISTS platform_fee_percent NUMERIC(5, 2) NOT NULL DEFAULT 5.00,
    ADD COLUMN IF NOT EXISTS hospital_rating INT,
    ADD COLUMN IF NOT EXISTS hospital_review TEXT,
    ADD COLUMN IF NOT EXISTS rating_tags TEXT,
    ADD COLUMN IF NOT EXISTS rated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE shift_claims DROP CONSTRAINT IF EXISTS shift_claims_rating_check;
ALTER TABLE shift_claims ADD CONSTRAINT shift_claims_rating_check
    CHECK (hospital_rating IS NULL OR (hospital_rating >= 1 AND hospital_rating <= 5));

ALTER TABLE candidate_profiles
    ADD COLUMN IF NOT EXISTS shift_rating_avg NUMERIC(3, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS shift_rating_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS completed_shifts_count INT NOT NULL DEFAULT 0;

