-- =============================================================================
-- FALE Healthcare Platform — Flyway Migration V3 (Approval Workflow Enhancements)
-- Adds approval statuses for job applications and locum shift claims,
-- and seeds the SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW platform setting.
-- =============================================================================

-- 1. Extend job_applications status check constraint to include UNDER_ADMIN_SCREENING
ALTER TABLE job_applications DROP CONSTRAINT IF EXISTS job_applications_status_check;
ALTER TABLE job_applications ADD CONSTRAINT job_applications_status_check
    CHECK (status IN ('UNDER_ADMIN_SCREENING', 'PENDING_SCREENING', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN'));

-- 2. Extend shift_claims status check constraint to include CLAIM_PENDING_ADMIN and CONFIRMED
ALTER TABLE shift_claims DROP CONSTRAINT IF EXISTS shift_claims_status_check;
ALTER TABLE shift_claims ADD CONSTRAINT shift_claims_status_check
    CHECK (status IN ('CLAIM_PENDING_ADMIN', 'CLAIMED', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW', 'CANCELLED', 'DISPUTED', 'REJECTED'));

-- 3. Seed platform setting for Shift Claim Admin Review
INSERT INTO platform_settings (key, value, description) VALUES
('SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW', 'true', 'Require Admin compliance review before graduate shift claims are confirmed')
ON CONFLICT (key) DO NOTHING;
