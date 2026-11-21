ALTER TABLE facility_profiles DROP CONSTRAINT IF EXISTS facility_profiles_beds_check;
ALTER TABLE facility_profiles ADD CONSTRAINT facility_profiles_beds_check CHECK (beds >= 0);
ALTER TABLE facility_profiles ALTER COLUMN license_number DROP NOT NULL;
ALTER TABLE facility_profiles ADD COLUMN group_name VARCHAR(150);
ALTER TABLE facility_profiles ADD COLUMN branch_name VARCHAR(150);
ALTER TABLE facility_profiles ADD COLUMN agreement_accepted_at TIMESTAMPTZ;
ALTER TABLE facility_profiles ADD COLUMN agreement_version VARCHAR(30);
ALTER TABLE facility_profiles ADD COLUMN agreement_accepted_by VARCHAR(100);
