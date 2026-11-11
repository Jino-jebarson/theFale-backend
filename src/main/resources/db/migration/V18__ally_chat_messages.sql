-- V18: Persistent cross-device Ally AI chat history per user and role scope
CREATE TABLE IF NOT EXISTS ally_chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_scope VARCHAR(30) NOT NULL,
    sender VARCHAR(20) NOT NULL,
    message_text TEXT NOT NULL,
    action VARCHAR(60),
    action_label VARCHAR(120),
    suggestions_json TEXT,
    ticket_id VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ally_chat_user_role_created
    ON ally_chat_messages(user_id, role_scope, created_at ASC);

