-- =============================================================================
-- FALE Healthcare Platform — Flyway Initial Migration V1
-- PostgreSQL 16+ Production Schema (28 Tables)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. USERS TABLE
-- Password + MFA are strictly for seeded/invited ROLE_ADMIN accounts.
-- Candidate and Facility users authenticate via passwordless OTP.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(15) UNIQUE,
    email VARCHAR(150) UNIQUE,
    password_hash VARCHAR(255),
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    role VARCHAR(30) NOT NULL CHECK (role IN ('ROLE_CANDIDATE', 'ROLE_FACILITY', 'ROLE_ADMIN')),
    candidate_level VARCHAR(30) CHECK (candidate_level IN ('STUDENT', 'FRESHER', 'PROFESSIONAL')),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LOCKED')),
    is_onboarded BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_level ON users(role, candidate_level);

-- 2. REFRESH TOKENS (Stored as SHA-256 hashes, rotated with reuse detection)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- 3. OTP VERIFICATIONS (Hashed, 5-minute expiry, max 5 attempts)
CREATE TABLE otp_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact VARCHAR(150) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_otp_contact ON otp_verifications(contact);

-- 4. IDEMPOTENCY KEYS
CREATE TABLE idempotency_keys (
    key VARCHAR(100) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint VARCHAR(150) NOT NULL,
    response_code INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_idempotency_created ON idempotency_keys(created_at);

-- 5. AUDIT EVENTS (Append-Only)
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    ip_address VARCHAR(45),
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_events_actor ON audit_events(actor_id);
CREATE INDEX idx_audit_events_action ON audit_events(action);
CREATE INDEX idx_audit_events_created ON audit_events(created_at);

-- 6. PLATFORM SETTINGS (Dynamic fees & limits)
CREATE TABLE platform_settings (
    key VARCHAR(50) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. MINIMUM ROLE RATES (Statutory wage floors per hour)
CREATE TABLE minimum_role_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profession VARCHAR(50) NOT NULL UNIQUE,
    min_rate_per_hour NUMERIC(10, 2) NOT NULL CHECK (min_rate_per_hour > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. CANDIDATE PROFILES (Student & Graduate)
CREATE TABLE candidate_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    gender VARCHAR(20),
    dob DATE,
    city VARCHAR(50) NOT NULL,
    state VARCHAR(50),
    pincode VARCHAR(10),
    address_line TEXT,
    -- Student Specific
    course VARCHAR(100),
    college VARCHAR(150),
    study_year VARCHAR(30),
    university_reg_no VARCHAR(50),
    expected_graduation_year INT,
    -- Graduate Specific
    profession VARCHAR(50),
    specialty VARCHAR(100),
    qualification VARCHAR(100),
    council VARCHAR(100),
    reg_number VARCHAR(50),
    reg_year VARCHAR(10),
    experience_years VARCHAR(30),
    bio TEXT,
    expected_salary_min_paise BIGINT,
    expected_salary_max_paise BIGINT,
    notice_period VARCHAR(50),
    kyc_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    kyc_notes TEXT,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_candidate_city ON candidate_profiles(city);
CREATE INDEX idx_candidate_kyc ON candidate_profiles(kyc_status);
CREATE INDEX idx_candidate_profession ON candidate_profiles(profession);

-- 9. CANDIDATE DOCUMENTS
CREATE TABLE candidate_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    file_url TEXT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_candidate_docs_cand ON candidate_documents(candidate_id);

-- 10. CANDIDATE LEVEL HISTORY (Audit trail of upgrades from Student to Graduate)
CREATE TABLE candidate_level_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    from_level VARCHAR(30) NOT NULL,
    to_level VARCHAR(30) NOT NULL,
    changed_by UUID NOT NULL REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 11. STUDENT UPGRADE REQUESTS (Queue for admin approval)
CREATE TABLE student_upgrade_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    qualification VARCHAR(100) NOT NULL,
    council VARCHAR(100) NOT NULL,
    reg_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    admin_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_upgrade_requests_status ON student_upgrade_requests(status);

-- 12. CANDIDATE PAYOUT METHODS (Bank account & UPI)
CREATE TABLE candidate_payout_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL UNIQUE REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    method_type VARCHAR(20) NOT NULL CHECK (method_type IN ('upi', 'bank')),
    upi_id VARCHAR(100),
    bank_name VARCHAR(100),
    account_number_encrypted VARCHAR(255),
    account_number_last4 VARCHAR(4),
    ifsc VARCHAR(11),
    account_holder_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'Pending' CHECK (status IN ('Verified', 'Pending')),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 13. FACILITY PROFILES
CREATE TABLE facility_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    hospital_name VARCHAR(150) NOT NULL,
    facility_type VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    pincode VARCHAR(10),
    address_line TEXT,
    beds INT NOT NULL CHECK (beds > 0),
    icu_beds INT DEFAULT 0,
    license_number VARCHAR(50) NOT NULL,
    nabh_number VARCHAR(50),
    accreditation_level VARCHAR(50),
    contact_person VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(15) NOT NULL,
    contact_email VARCHAR(150) NOT NULL,
    kyc_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    kyc_notes TEXT,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_facility_city ON facility_profiles(city);
CREATE INDEX idx_facility_kyc ON facility_profiles(kyc_status);

-- 14. FACILITY WALLETS (Nodal Escrow)
CREATE TABLE facility_wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL UNIQUE REFERENCES facility_profiles(id) ON DELETE CASCADE,
    available_balance NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (available_balance >= 0),
    locked_escrow_balance NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (locked_escrow_balance >= 0),
    total_disbursed NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    candidate_credits INT NOT NULL DEFAULT 3 CHECK (candidate_credits >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 15. WALLET TRANSACTIONS
CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES facility_wallets(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL CHECK (type IN ('topup', 'escrow_hold', 'disbursement', 'credit_purchase', 'standby_credit', 'refund')),
    amount NUMERIC(12, 2) NOT NULL,
    balance_after NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('Completed', 'In Escrow', 'Processing', 'Refunded', 'Pending Reconciliation')),
    ref_number VARCHAR(50) NOT NULL UNIQUE,
    utr VARCHAR(50) UNIQUE,
    title VARCHAR(150) NOT NULL,
    subtitle VARCHAR(255),
    invoice_id VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_tx_wallet ON wallet_transactions(wallet_id);
CREATE INDEX idx_wallet_tx_utr ON wallet_transactions(utr);

-- 16. HOSPITAL BANK ACCOUNTS (Refunds & Sub-Accounts)
CREATE TABLE hospital_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL UNIQUE REFERENCES facility_profiles(id) ON DELETE CASCADE,
    bank_name VARCHAR(100) NOT NULL,
    account_number_encrypted VARCHAR(255) NOT NULL,
    account_number_last4 VARCHAR(4) NOT NULL,
    ifsc VARCHAR(11) NOT NULL,
    branch VARCHAR(100),
    account_holder_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(50) NOT NULL DEFAULT 'Current',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('Verified', 'Pending Penny Drop', 'Under Review')),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 17. JOB POSTINGS
CREATE TABLE job_postings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL REFERENCES facility_profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    department VARCHAR(50) NOT NULL,
    employment_type VARCHAR(50) NOT NULL,
    is_internship BOOLEAN NOT NULL DEFAULT FALSE,
    city VARCHAR(50) NOT NULL,
    openings INT NOT NULL CHECK (openings > 0),
    salary_min NUMERIC(12, 2) NOT NULL,
    salary_max NUMERIC(12, 2) NOT NULL,
    experience_required VARCHAR(50),
    qualification_required VARCHAR(100),
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL' CHECK (status IN ('PENDING_APPROVAL', 'OPEN', 'CLOSED', 'REJECTED')),
    moderated_by UUID REFERENCES users(id),
    moderation_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_jobs_city ON job_postings(city);
CREATE INDEX idx_jobs_dept ON job_postings(department);
CREATE INDEX idx_jobs_status_intern ON job_postings(status, is_internship);

-- 18. LOCUM SHIFTS (start_at, end_at timestamps with server-derived hours)
CREATE TABLE locum_shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL REFERENCES facility_profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    department VARCHAR(50) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    hours INT NOT NULL CHECK (hours >= 4 AND hours <= 24),
    rate_per_hour NUMERIC(10, 2) NOT NULL CHECK (rate_per_hour > 0),
    surge_rate_per_hour NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    surge_label VARCHAR(50),
    openings INT NOT NULL CHECK (openings > 0),
    slots_claimed INT NOT NULL DEFAULT 0 CHECK (slots_claimed <= openings),
    is_urgent BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('PENDING_APPROVAL', 'OPEN', 'FILLED', 'CANCELLED', 'REJECTED', 'COMPLETED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shifts_date_status ON locum_shifts(start_at, status);

-- 19. SHIFT CLAIMS (Multi-Stage Lifecycle & Full Financial Fee Breakdown)
CREATE TABLE shift_claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id UUID NOT NULL REFERENCES locum_shifts(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'CLAIMED' CHECK (status IN ('CLAIMED', 'CHECKED_IN', 'COMPLETED', 'NO_SHOW', 'CANCELLED', 'DISPUTED')),
    gross_amount NUMERIC(12, 2) NOT NULL,
    platform_fee NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    tds_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(12, 2) NOT NULL,
    payout_status VARCHAR(30) NOT NULL DEFAULT 'IN_ESCROW' CHECK (payout_status IN ('IN_ESCROW', 'PENDING_DISBURSEMENT', 'DISBURSED', 'REFUNDED')),
    utr VARCHAR(50),
    payout_slip_ref VARCHAR(50),
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    disbursed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_candidate_shift UNIQUE (shift_id, candidate_id)
);

CREATE INDEX idx_shift_claims_cand ON shift_claims(candidate_id);
CREATE INDEX idx_shift_claims_shift ON shift_claims(shift_id);
CREATE INDEX idx_shift_claims_status ON shift_claims(status, payout_status);

-- 20. JOB APPLICATIONS
CREATE TABLE job_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_SCREENING' CHECK (status IN ('PENDING_SCREENING', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    match_score INT NOT NULL DEFAULT 75,
    withdrawal_reason VARCHAR(255),
    interview_scheduled_at TIMESTAMP WITH TIME ZONE,
    interview_location_or_link VARCHAR(255),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_job_candidate UNIQUE (job_id, candidate_id)
);

CREATE INDEX idx_applications_cand ON job_applications(candidate_id);
CREATE INDEX idx_applications_job ON job_applications(job_id);

-- 21. SAVED JOBS (Wishlist)
CREATE TABLE saved_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_saved_job UNIQUE (candidate_id, job_id)
);

-- 22. UNLOCKED CANDIDATES (Resdex Unlocks)
CREATE TABLE unlocked_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id UUID NOT NULL REFERENCES facility_profiles(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    credits_spent INT NOT NULL DEFAULT 1,
    CONSTRAINT uk_facility_candidate_unlock UNIQUE (facility_id, candidate_id)
);

-- 23. HUDDLE POSTS
CREATE TABLE huddle_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('Thought', 'Experience', 'Case', 'Question', 'Update', 'Event')),
    field VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'LIVE' CHECK (status IN ('LIVE', 'FLAGGED', 'REMOVED')),
    upvotes INT NOT NULL DEFAULT 0,
    flag_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_huddle_posts_status ON huddle_posts(status);
CREATE INDEX idx_huddle_posts_field ON huddle_posts(field);

-- 24. HUDDLE COMMENTS
CREATE TABLE huddle_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES huddle_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_huddle_comments_post ON huddle_comments(post_id);

-- 25. HUDDLE QUESTIONS (Mentor AMA)
CREATE TABLE huddle_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    field VARCHAR(50) NOT NULL,
    question_text TEXT NOT NULL,
    answers_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 26. HUDDLE ANSWERS (Verified Clinician Mentor Answers)
CREATE TABLE huddle_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES huddle_questions(id) ON DELETE CASCADE,
    mentor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    answer_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_huddle_answers_question ON huddle_answers(question_id);

-- 27. HUDDLE FLAGS
CREATE TABLE huddle_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES huddle_posts(id) ON DELETE CASCADE,
    reported_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 28. COURSES (CME Academy) & ENROLLMENTS
CREATE TABLE courses (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    duration VARCHAR(30) NOT NULL,
    cme_points INT NOT NULL DEFAULT 0,
    summary TEXT NOT NULL,
    for_students BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE course_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id VARCHAR(50) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    progress_percent INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    certificate_ref VARCHAR(100),
    CONSTRAINT uk_user_course UNIQUE (user_id, course_id)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    action_url VARCHAR(255),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);

CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
