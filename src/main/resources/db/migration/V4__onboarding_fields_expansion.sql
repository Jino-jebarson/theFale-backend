-- =============================================================================
-- FALE Healthcare Platform - Flyway Migration V4 (Onboarding Fields Expansion)
-- Collect and persist all onboarding details for Candidates & Facilities
-- =============================================================================

-- 1. Candidate profile additional fields
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS council_state VARCHAR(100);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS sub_specialties TEXT;
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS employment_type VARCHAR(100);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS availability VARCHAR(100);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS current_salary VARCHAR(50);
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS skills TEXT;
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS languages TEXT;
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS preferred_cities TEXT;
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS open_to_work BOOLEAN DEFAULT TRUE;
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS relocate BOOLEAN DEFAULT FALSE;

-- 2. Facility profile additional fields
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS size VARCHAR(50);
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS about TEXT;
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS website VARCHAR(255);
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS gst VARCHAR(30);
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS designation VARCHAR(100);
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS departments TEXT;
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS hiring_needs TEXT;
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE facility_profiles ADD COLUMN IF NOT EXISTS facility_photos TEXT;

