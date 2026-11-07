-- Reset candidate credits default from legacy 3 to 0 for dynamic purchase model
ALTER TABLE facility_wallets ALTER COLUMN candidate_credits SET DEFAULT 0;

-- Reset any facilities currently stuck on legacy static 3 credits
UPDATE facility_wallets SET candidate_credits = 0 WHERE candidate_credits = 3;

