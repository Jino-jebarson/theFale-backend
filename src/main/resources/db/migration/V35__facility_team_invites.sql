CREATE TABLE facility_team_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL REFERENCES facility_profiles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_facility_team_invite_phone UNIQUE (facility_id, phone)
);

CREATE INDEX idx_facility_team_invites_facility ON facility_team_invites(facility_id, created_at DESC);
