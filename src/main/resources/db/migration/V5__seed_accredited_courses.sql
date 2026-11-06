-- =============================================================================
-- FALE Healthcare Platform — Flyway Migration V5 (Accredited CME Catalog)
-- Populates 22 high-quality clinical courses across all healthcare cadres:
-- Doctors, Critical Care Nurses, Pharmacists, Dialysis Techs, Physiotherapists,
-- Imaging Techs, and Clinical Trainees / Students.
-- =============================================================================

INSERT INTO courses (id, title, category, duration, cme_points, summary, for_students) VALUES
-- 1. DOCTORS / PHYSICIANS
('cme-ecg-101', 'Acute Coronary Syndromes & 12-Lead ECG Mastery', 'Critical Care', '6 Hours', 5, 'Stanford Medicine CME aligned: Systematic interpretation of STEMI, reciprocal ST depressions, subtle Wellens syndrome, and lethal ventricular arrhythmias.', false),
('cme-ards-102', 'Mechanical Ventilation in ARDS & COVID-19', 'Critical Care', '8 Hours', 8, 'Harvard Medical School protocol: Lung-protective ventilation, driving pressure titration, prone positioning, and weaning criteria in severe ARDS.', false),
('cme-sepsis-103', 'Emergency Sepsis Resuscitation Protocols', 'Emergency & Trauma', '5 Hours', 6, 'Surviving Sepsis Campaign 2024 / Johns Hopkins: 1-hour resuscitation bundle, dynamic lactate clearance, and norepinephrine titration.', false),
('cme-ams-104', 'Antimicrobial Stewardship in Hospital Inpatients', 'Clinical Pharmacology', '6 Hours', 5, 'AIIMS New Delhi / ICMR: Rational empiric prescribing, de-escalation strategies, and combating carbapenem-resistant enterobacteriaceae (CRE).', false),

-- 2. CRITICAL CARE & STAFF NURSES
('cme-acls-201', 'AHA Advanced Cardiovascular Life Support (ACLS) Core', 'Nursing Essentials', '8 Hours', 8, 'AHA & Indian Resuscitation Council: High-quality resuscitation, shockable rhythm defibrillation, post-cardiac arrest ROSC care, and Code Blue team dynamics.', false),
('cme-hemo-202', 'ICU Hemodynamic Monitoring & Arterial Line Care', 'Critical Care', '6 Hours', 6, 'King’s College London Critical Care: Arterial line cannulation maintenance, damping coefficient tests, CVP wave analysis, and vasoactive titration.', false),
('cme-clabsi-203', 'NABH Central Line (CLABSI) Infection Prevention', 'Nursing Essentials', '4 Hours', 4, 'NABH 5th Edition & CDC Guidelines: Maximum sterile barrier precautions, chlorhexidine dressing protocols, and daily central line necessity audits.', false),
('cme-pals-204', 'Pediatric Advanced Life Support (PALS) Triage', 'Emergency & Trauma', '7 Hours', 7, 'Stanford Children’s Health: Pediatric Glasgow Coma Scale, rapid decompensation detection, weight-based fluid resuscitation (Broselow tape), and septic shock.', false),

-- 3. CLINICAL PHARMACISTS
('cme-lasa-301', 'High-Alert Medication Safety & LASA Dispensing', 'Clinical Pharmacology', '4 Hours', 4, 'ISMP International Standards: Tall Man lettering, independent double checks, and mitigation of Look-Alike Sound-Alike drug dispensing errors.', false),
('cme-chemo-302', 'Chemotherapy Admixture & Cytotoxic Safety', 'Clinical Pharmacology', '6 Hours', 6, 'Tata Memorial Centre & WHO: Class II Type B2 Biological Safety Cabinets (BSC), hazardous drug spill kits, and extravasation management.', false),
('cme-tdm-303', 'Clinical Pharmacokinetics & Therapeutic Drug Monitoring', 'Clinical Pharmacology', '5 Hours', 5, 'Manipal Pharmacy / AIIMS: Therapeutic drug monitoring of Vancomycin, Aminoglycosides, and Digoxin, targeting AUC/MIC and peak-trough kinetics.', false),

-- 4. DIALYSIS TECHNICIANS
('cme-fistula-401', 'Hemodialysis Vascular Access & Fistula Complications', 'Dialysis & Renal', '5 Hours', 5, 'Johns Hopkins Nephrology: Cannulation buttonhole technique, stenosis detection, thrombosis prevention, and management of AV fistula venous rupture.', false),
('cme-ro-402', 'Water Treatment & Endotoxin Standards in Hemodialysis', 'Dialysis & Renal', '4 Hours', 4, 'AAMI & ISO 23500 Standards: Reverse Osmosis (RO) maintenance, chlorine breakthrough testing, microbial colony counting, and endotoxin LAL testing.', false),
('cme-crrt-403', 'CRRT Machine Priming, Anticoagulation & Troubleshooting', 'Dialysis & Renal', '6 Hours', 6, 'Cleveland Clinic Renal: Continuous Veno-Venous Hemodiafiltration (CVVHDF), regional citrate anticoagulation (RCA), and trans-membrane pressure (TMP) alarms.', false),

-- 5. PHYSIOTHERAPISTS
('cme-icu-rehab-501', 'Early ICU Mobilization & Post-Extubation Pulmonary Rehab', 'Physiotherapy & Rehab', '6 Hours', 6, 'Johns Hopkins Physical Medicine: ABCDEF ICU liberation bundle, passive cycling, tilt-table mobilization, and lung expansion inspiratory muscle training.', false),
('cme-tkr-502', 'Post-Operative TKR & THR Accelerated Mobilization', 'Physiotherapy & Rehab', '5 Hours', 5, 'Oxford NHS Trust: Day-0 knee flexion/extension milestones, gait retraining, quad lag correction, and DVT prophylaxis mechanical protocols.', false),
('cme-stroke-503', 'Neuro-Developmental Therapy in Acute Stroke Rehab', 'Physiotherapy & Rehab', '7 Hours', 7, 'NIMHANS Bengaluru: Bobath concept, trunk postural stability, constraint-induced movement therapy (CIMT), and spasticity inhibition techniques.', false),

-- 6. RADIOLOGY & IMAGING TECHNICIANS
('cme-trauma-ct-601', 'Multi-Slice CT Trauma Protocols & Contrast Safety', 'Radiology & Imaging', '5 Hours', 5, 'RSNA Radiology: Whole-body polytrauma CT protocols, IV contrast extravasation protocols, and anaphylactoid reaction emergency management.', false),
('cme-aerb-602', 'Radiation Safety & AERB Compliance in Cath Labs', 'Radiology & Imaging', '4 Hours', 4, 'Atomic Energy Regulatory Board (AERB) & CMC Vellore: ALARA principles, lead apron shielding inspection, collimation control, and TLD badge dosimetry.', false),

-- 7. STUDENTS, INTERNS & TRAINEES
('cme-bls-701', 'Foundational BLS & High-Quality Chest Compressions', 'Student Essentials', '3 Hours', 3, 'AHA Standardized Basic Life Support: Adult and pediatric CPR, AED pad placement, relief of choking, and clinical logbook certified.', true),
('cme-bmw-702', 'Hospital Hand Hygiene & Biomedical Waste Management', 'Quality & NABH', '4 Hours', 4, 'WHO 5 Moments of Hand Hygiene & NABH BMW Rules 2016: Color-coded bin segregation (Yellow, Red, White, Blue), sharp disposal, and audit logging.', true),
('cme-sbar-703', 'Clinical Empathy & SBAR Emergency Handover Protocols', 'Student Essentials', '3 Hours', 3, 'Harvard Macy Institute: Situation-Background-Assessment-Recommendation (SBAR) handover, bedside communication with anxious families, and escalation.', true)

ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  category = EXCLUDED.category,
  duration = EXCLUDED.duration,
  cme_points = EXCLUDED.cme_points,
  summary = EXCLUDED.summary,
  for_students = EXCLUDED.for_students;

