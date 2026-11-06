-- V9: Allow admin-onboarded external clinical mentors and seed certified directory mentors
ALTER TABLE mentor_applications ALTER COLUMN candidate_id DROP NOT NULL;
ALTER TABLE mentor_applications ALTER COLUMN user_id DROP NOT NULL;

INSERT INTO mentor_applications (id, name, profession, specialty, years_experience, organization, status, applied_at, reviewed_at, admin_notes)
VALUES 
('a0000001-0000-0000-0000-000000000001', 'Dr. Arjun Desai', 'Senior Sports & Neuro Physiotherapist', 'Physiotherapy', 7, 'Koramangala Multispeciality', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Accredited Clinical Mentor'),
('a0000002-0000-0000-0000-000000000002', 'Joseph Thomas', 'Critical Care Nursing Specialist', 'Nursing', 5, 'Manipal Hospital', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Accredited Clinical Mentor'),
('a0000003-0000-0000-0000-000000000003', 'Dr. Sunita Rao', 'Pediatric Emergency Specialist', 'Pediatrics', 8, 'Rainbow Children''s Hospital', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Accredited Clinical Mentor'),
('a0000004-0000-0000-0000-000000000004', 'Priya Nair', 'Clinical Pharmacist & Pharmacology Guide', 'Pharmacy', 4, 'Apollo Hospitals', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Accredited Clinical Mentor')
ON CONFLICT (id) DO NOTHING;

