-- V11: Clinical Mentorship & CME Sessions
CREATE TABLE IF NOT EXISTS huddle_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    host_type VARCHAR(50) NOT NULL DEFAULT 'mentor',
    host_id UUID NOT NULL,
    host_name VARCHAR(255) NOT NULL,
    host_sub VARCHAR(255),
    session_when TIMESTAMP WITH TIME ZONE NOT NULL,
    session_where VARCHAR(255) NOT NULL,
    seats INT NOT NULL DEFAULT 30,
    booked_count INT NOT NULL DEFAULT 0,
    description TEXT,
    for_roles VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS huddle_session_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES huddle_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_user UNIQUE (session_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_huddle_sessions_when ON huddle_sessions(session_when);
CREATE INDEX IF NOT EXISTS idx_huddle_session_bookings_user ON huddle_session_bookings(user_id);

