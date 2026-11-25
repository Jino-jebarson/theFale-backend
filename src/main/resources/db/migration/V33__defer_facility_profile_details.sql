-- Let a facility create an account with the short signup details, then complete its profile later.
ALTER TABLE facility_profiles ALTER COLUMN facility_type DROP NOT NULL;
ALTER TABLE facility_profiles ALTER COLUMN city DROP NOT NULL;
ALTER TABLE facility_profiles ALTER COLUMN state DROP NOT NULL;
ALTER TABLE facility_profiles ALTER COLUMN contact_email DROP NOT NULL;
