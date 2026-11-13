-- This entity predates the shift changes but had no migration, preventing a
-- fresh migrated database from starting with Hibernate schema validation.
CREATE TABLE IF NOT EXISTS candidate_profile_views (
    id bigserial PRIMARY KEY,
    candidate_id uuid NOT NULL,
    facility_id uuid,
    facility_name varchar(255),
    viewed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_candidate_views_cid_date ON candidate_profile_views(candidate_id, viewed_at);

CREATE TABLE IF NOT EXISTS facility_documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id uuid NOT NULL,
    document_type varchar(50) NOT NULL,
    file_url text NOT NULL,
    original_file_name varchar(255) NOT NULL,
    file_size_bytes bigint NOT NULL,
    mime_type varchar(100) NOT NULL,
    verified boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_facility_documents_facility ON facility_documents(facility_id);
