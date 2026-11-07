BEGIN;

-- 1. Locum Shifts & Shift Claims
TRUNCATE TABLE shift_claims CASCADE;
TRUNCATE TABLE locum_shifts CASCADE;

-- 2. Jobs & Applications
TRUNCATE TABLE job_applications CASCADE;
TRUNCATE TABLE saved_jobs CASCADE;
TRUNCATE TABLE unlocked_candidates CASCADE;
TRUNCATE TABLE job_postings CASCADE;

-- 3. Candidate Data
TRUNCATE TABLE candidate_documents CASCADE;
TRUNCATE TABLE candidate_payout_methods CASCADE;
TRUNCATE TABLE student_upgrade_requests CASCADE;
TRUNCATE TABLE candidate_level_history CASCADE;
TRUNCATE TABLE candidate_profiles CASCADE;

-- 4. Facility & Financial Data
TRUNCATE TABLE wallet_transactions CASCADE;
TRUNCATE TABLE hospital_bank_accounts CASCADE;
TRUNCATE TABLE facility_wallets CASCADE;
TRUNCATE TABLE facility_profiles CASCADE;

-- 5. Huddle Community & CME
TRUNCATE TABLE huddle_comments CASCADE;
TRUNCATE TABLE huddle_answers CASCADE;
TRUNCATE TABLE huddle_flags CASCADE;
TRUNCATE TABLE huddle_posts CASCADE;
TRUNCATE TABLE huddle_questions CASCADE;
TRUNCATE TABLE huddle_session_bookings CASCADE;
TRUNCATE TABLE huddle_sessions CASCADE;
TRUNCATE TABLE mentor_applications CASCADE;
TRUNCATE TABLE course_enrollments CASCADE;

-- 6. Operational & Security Tables
TRUNCATE TABLE notifications CASCADE;
TRUNCATE TABLE support_tickets CASCADE;
TRUNCATE TABLE otp_verifications CASCADE;
TRUNCATE TABLE idempotency_keys CASCADE;
TRUNCATE TABLE audit_events CASCADE;

-- 7. Tokens
DELETE FROM refresh_tokens;

-- 8. Users: Remove all candidates and facility accounts, keep strictly ROLE_ADMIN
DELETE FROM users WHERE role != 'ROLE_ADMIN';

COMMIT;