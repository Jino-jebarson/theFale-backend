-- FALE Healthcare Platform — Wipe Development Database
-- WARNING: Wipes all tables, sequences, and views in the public schema.
-- Safe for both superusers (postgres) and standard application users (fale_admin).
-- Re-run Flyway migrations by starting/restarting the backend application.

DO $$ DECLARE
    r RECORD;
BEGIN
    -- Drop all tables (including flyway_schema_history) with CASCADE
    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;

    -- Drop all sequences
    FOR r IN (SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public') LOOP
        EXECUTE 'DROP SEQUENCE IF EXISTS public.' || quote_ident(r.sequence_name) || ' CASCADE';
    END LOOP;

    -- Drop all views
    FOR r IN (SELECT table_name FROM information_schema.views WHERE table_schema = 'public') LOOP
        EXECUTE 'DROP VIEW IF EXISTS public.' || quote_ident(r.table_name) || ' CASCADE';
    END LOOP;
END $$;
