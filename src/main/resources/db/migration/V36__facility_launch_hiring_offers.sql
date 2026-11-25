ALTER TABLE facility_profiles
    ADD COLUMN first_hire_free_used BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN first_hire_free_application_id UUID;

ALTER TABLE job_applications
    ADD COLUMN first_hire_fee_waived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE facility_wallets
    ADD COLUMN welcome_credits_granted BOOLEAN NOT NULL DEFAULT FALSE;

-- Do not retroactively grant the new first-hire offer to facilities that had
-- already hired through the platform before this launch.
UPDATE facility_profiles fp
SET first_hire_free_used = TRUE
WHERE EXISTS (
    SELECT 1
    FROM job_postings jp
    JOIN job_applications ja ON ja.job_id = jp.id
    WHERE jp.facility_id = fp.id
      AND UPPER(ja.status) = 'HIRED'
);
