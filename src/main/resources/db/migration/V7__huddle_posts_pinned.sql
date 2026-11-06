-- V7: Support pinned posts in Huddle community
ALTER TABLE huddle_posts ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

