ALTER TABLE locum_shifts
    ADD COLUMN profession varchar(100), ADD COLUMN shift_city varchar(100),
    ADD COLUMN registration_required boolean DEFAULT false,
    ADD COLUMN bls_required boolean DEFAULT false, ADD COLUMN acls_required boolean DEFAULT false,
    ADD COLUMN icu_experience_required boolean DEFAULT false,
    ADD COLUMN minimum_experience_years integer,
    ADD COLUMN report_to varchar(255), ADD COLUMN report_location varchar(255),
    ADD COLUMN dress_requirement varchar(255), ADD COLUMN items_to_bring text,
    ADD COLUMN reporting_notes text;

ALTER TABLE facility_profiles ADD COLUMN first_shift_approved boolean NOT NULL DEFAULT false;
UPDATE facility_profiles f SET first_shift_approved = true
WHERE EXISTS (SELECT 1 FROM locum_shifts s WHERE s.facility_id = f.id
    AND s.status IN ('OPEN','FILLED','BOOKED','COMPLETED','EXPIRED'));

ALTER TABLE facility_wallets ADD COLUMN first_completed_shift_id uuid;
UPDATE facility_wallets w SET first_completed_shift_id = (
    SELECT s.id FROM locum_shifts s JOIN shift_claims c ON c.shift_id = s.id
    WHERE s.facility_id = w.facility_id AND c.status = 'COMPLETED'
    ORDER BY c.completed_at ASC NULLS LAST, c.id LIMIT 1);

ALTER TABLE shift_claims ADD COLUMN check_in_qr_token varchar(64),
    ADD COLUMN check_in_latitude double precision, ADD COLUMN check_in_longitude double precision,
    ADD COLUMN check_in_accuracy double precision, ADD COLUMN location_captured_at timestamptz,
    ADD COLUMN selfie_captured_at timestamptz, ADD COLUMN check_in_selfie text,
    ADD COLUMN check_in_method varchar(10), ADD COLUMN hospital_fee_amount numeric(12,2);

-- Pending applications must not consume confirmed capacity.
UPDATE locum_shifts s SET slots_claimed = (SELECT count(*) FROM shift_claims c
    WHERE c.shift_id = s.id AND c.status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS','TIMESHEET_SUBMITTED','COMPLETED','DISPUTED'));
UPDATE locum_shifts SET status = 'OPEN' WHERE status = 'FILLED' AND slots_claimed < openings;

-- Old monetary snapshots remain unchanged. New shift posts reserve 8%; the first
-- completed shift's fee is released back to the hospital at timesheet approval.

ALTER TABLE shift_claims ADD COLUMN check_in_failed_attempts integer NOT NULL DEFAULT 0, ADD COLUMN check_in_blocked_until timestamptz;

UPDATE shift_claims SET platform_fee = 0, platform_fee_percent = 0, net_amount = gross_amount - tds_amount
WHERE completed_at IS NULL AND status IN ('CLAIMED','CLAIM_PENDING_ADMIN','CONFIRMED','CHECKED_IN','IN_PROGRESS','TIMESHEET_SUBMITTED');

-- Rotate outstanding legacy four-digit credentials. No credential is sent to a
-- candidate: the hospital shares it only when the clinician arrives.
UPDATE shift_claims SET check_in_pin = (((('x' || substr(replace(gen_random_uuid()::text, '-', ''),1,8))::bit(32)::bigint % 900000) + 100000)::text),
    check_in_qr_token = gen_random_uuid()::text WHERE status = 'CONFIRMED';

UPDATE platform_settings SET value = '0.0' WHERE key = 'PLATFORM_FEE_PERCENT';
UPDATE platform_settings SET value = '8.0' WHERE key = 'FACILITY_SHIFT_FEE_PERCENT';
