-- V8: Support Dynamic Clinical Mentor Applications and Verification
CREATE TABLE IF NOT EXISTS mentor_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    profession VARCHAR(50),
    specialty VARCHAR(100),
    years_experience INT NOT NULL DEFAULT 3,
    organization VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    admin_notes TEXT,
    CONSTRAINT fk_mentor_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profiles(id) ON DELETE CASCADE
);

ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS is_mentor BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE candidate_profiles ADD COLUMN IF NOT EXISTS mentor_status VARCHAR(30) NOT NULL DEFAULT 'NONE';

