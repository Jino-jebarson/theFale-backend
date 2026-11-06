-- V6: Support Review status, image attachments, and anonymous posting for clinical Huddle
ALTER TABLE huddle_posts DROP CONSTRAINT IF EXISTS huddle_posts_status_check;
ALTER TABLE huddle_posts ADD CONSTRAINT huddle_posts_status_check CHECK (status IN ('LIVE', 'REVIEW', 'FLAGGED', 'REMOVED'));
ALTER TABLE huddle_posts ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE huddle_posts ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

