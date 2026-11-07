-- V15: Support 1-minute audio voice notes in Huddle clinical discussions
ALTER TABLE huddle_posts ADD COLUMN IF NOT EXISTS audio_url TEXT;
ALTER TABLE huddle_posts ADD COLUMN IF NOT EXISTS audio_duration INT;

