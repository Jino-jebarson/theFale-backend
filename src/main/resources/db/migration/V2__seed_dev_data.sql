-- =============================================================================
-- FALE Healthcare Platform — Flyway Migration V2 (Reference & Catalog Data)
-- Populates Statutory Minimum Wage Floors, Dynamic Platform Settings, and CME Courses.
-- NOTE: User/Admin accounts are NEVER seeded in migrations. In production, the
-- root admin is bootstrapped from environment variables (ADMIN_INITIAL_EMAIL,
-- ADMIN_INITIAL_PASSWORD). In dev, a dev-profile bootstrap creates dev accounts.
-- =============================================================================

-- 1. PLATFORM SETTINGS (Dynamic fees & limits)
INSERT INTO platform_settings (key, value, description) VALUES
('PLATFORM_FEE_PERCENT', '5.0', 'Percentage commission retained by FALE on locum duty claims'),
('TDS_PERCENT', '1.0', 'Section 194J statutory tax deduction at source on professional payouts'),
('MIN_ESCROW_TOPUP_PAISE', '100000', 'Minimum wallet top-up amount in paise (₹1,000.00)')
ON CONFLICT (key) DO NOTHING;

-- 2. STATUTORY MINIMUM HOURLY WAGE FLOORS
INSERT INTO minimum_role_rates (id, profession, min_rate_per_hour) VALUES
('a0000000-0000-0000-0000-000000000001', 'Doctor / General Physician', 800.00),
('a0000000-0000-0000-0000-000000000002', 'ICU / Critical Care Specialist', 1200.00),
('a0000000-0000-0000-0000-000000000003', 'Staff Nurse — General Ward', 250.00),
('a0000000-0000-0000-0000-000000000004', 'Staff Nurse — ICU / Night Duty', 350.00),
('a0000000-0000-0000-0000-000000000005', 'OT Technician', 250.00),
('a0000000-0000-0000-0000-000000000006', 'Lab Technician / Pathology', 200.00),
('a0000000-0000-0000-0000-000000000007', 'Radiographer / Imaging', 220.00),
('a0000000-0000-0000-0000-000000000008', 'Clinical Pharmacist', 180.00),
('a0000000-0000-0000-0000-000000000009', 'Physiotherapist', 300.00)
ON CONFLICT (profession) DO NOTHING;

-- 3. CME COURSES
INSERT INTO courses (id, title, category, duration, cme_points, summary, for_students) VALUES
('cme-bls-101', 'Basic Life Support (BLS) & Emergency Resuscitation', 'Critical care', '4 Hours', 5, 'Foundational AHA-aligned clinical CPR and automated external defibrillator (AED) workflows.', true),
('cme-icu-201', 'Infection Control & Sterilization Protocols in ICU', 'Nursing', '6 Hours', 8, 'Advanced NABH-compliant central line and surgical site infection prevention guidelines.', false),
('cme-nabh-301', 'NABH 5th Edition Clinical Documentation Standards', 'Operations', '3 Hours', 4, 'Comprehensive legal and clinical charting protocols for hospital accreditation.', false),
('cme-ecg-102', '12-Lead ECG Interpretation & Arrhythmia Identification', 'Diagnostics', '5 Hours', 6, 'Systematic approach to identifying STEMI, NSTEMI, heart blocks, and acute rhythm disturbances.', true)
ON CONFLICT (id) DO NOTHING;
