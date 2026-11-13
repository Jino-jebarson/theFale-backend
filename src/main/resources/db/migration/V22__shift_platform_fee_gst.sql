-- Historical reservations keep their original tax treatment. New posts snapshot 18% in code.
ALTER TABLE locum_shifts ADD COLUMN platform_fee_gst_percent NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE locum_shifts ADD COLUMN platform_fee_gst_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE shift_claims ADD COLUMN hospital_fee_gst_amount NUMERIC(12,2);
ALTER TABLE wallet_transactions ADD COLUMN platform_fee_amount NUMERIC(12,2);
ALTER TABLE wallet_transactions ADD COLUMN platform_fee_gst_amount NUMERIC(12,2);
