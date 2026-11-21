ALTER TABLE candidate_profiles
    ADD COLUMN IF NOT EXISTS expected_graduation_month VARCHAR(7);
