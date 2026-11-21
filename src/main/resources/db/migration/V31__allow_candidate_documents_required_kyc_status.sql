ALTER TABLE candidate_profiles
    DROP CONSTRAINT IF EXISTS candidate_profiles_kyc_status_check;

ALTER TABLE candidate_profiles
    ADD CONSTRAINT candidate_profiles_kyc_status_check
    CHECK (kyc_status IN ('DOCUMENTS_REQUIRED', 'PENDING', 'VERIFIED', 'REJECTED'));
