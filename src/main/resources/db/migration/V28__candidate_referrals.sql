CREATE TABLE candidate_referral_codes (
    candidate_id UUID PRIMARY KEY REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    referral_code VARCHAR(12) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE candidate_referrals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_candidate_id UUID NOT NULL REFERENCES candidate_profiles(id) ON DELETE RESTRICT,
    referred_candidate_id UUID NOT NULL UNIQUE REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    referral_code VARCHAR(12) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    reward_type VARCHAR(20),
    reward_amount NUMERIC(10,2),
    reward_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    reward_reason TEXT,
    qualified_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    payment_reference VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_candidate_referral_status CHECK (status IN ('JOINED','VERIFIED','WORKED','HIRED')),
    CONSTRAINT chk_candidate_referral_reward_status CHECK (reward_status IN ('NONE','DUE','HELD','PAID')),
    CONSTRAINT chk_candidate_referral_reward_type CHECK (reward_type IS NULL OR reward_type IN ('FIRST_SHIFT','HIRED'))
);

CREATE INDEX idx_candidate_referrals_referrer_created ON candidate_referrals(referrer_candidate_id, created_at DESC);
CREATE INDEX idx_candidate_referrals_reward_status ON candidate_referrals(reward_status, qualified_at);
CREATE UNIQUE INDEX uk_candidate_referral_payment_reference ON candidate_referrals(payment_reference) WHERE payment_reference IS NOT NULL;
