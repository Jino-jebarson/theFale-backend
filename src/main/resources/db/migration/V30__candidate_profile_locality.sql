ALTER TABLE candidate_profiles
    ADD COLUMN IF NOT EXISTS locality VARCHAR(100);
