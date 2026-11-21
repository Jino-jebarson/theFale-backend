ALTER TABLE candidate_profiles
    ADD COLUMN IF NOT EXISTS experience_history TEXT,
    ADD COLUMN IF NOT EXISTS certifications TEXT;
