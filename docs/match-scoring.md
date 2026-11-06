# FALE Healthcare — Deterministic Clinical Match Scoring Formula

## 1. Overview and Principles

The FALE healthcare matching algorithm computes an objective, transparent, and non-discriminatory match percentage (between 30% and 100%) whenever a candidate applies for a permanent job or clinical internship.

### Strict Non-Discrimination Invariant
In compliance with the Constitution of India, Equal Remuneration principles, and healthcare ethical standards, the scoring engine **strictly excludes protected personal attributes**:
- **Excluded**: Gender, age, date of birth, religion, caste, marital status, pregnancy status, or physical appearance.
- **Included**: Clinical qualifications, council/college credentials, department alignment, geographic location, and verified experience.

---

## 2. Match Scoring Formula (100 Point Scale)

The total match score is calculated as:
$$\text{Match Score} = \text{Base} + \text{Specialty} + \text{Qualification} + \text{Verification} + \text{Location} + \text{Experience}$$

| Component | Max Points | Evaluation Criteria |
| :--- | :---: | :--- |
| **Baseline Floor** | 30 | Base eligibility points awarded for complete candidate registration. |
| **Role & Specialty Alignment** | 25 | Direct match between candidate profession / specialty / study course and the job department or role title keywords (+25 pts); or broad clinical cadre alignment (e.g. Nurse to Nursing, Doctor to Clinical Officer) (+20 pts); otherwise (+10 pts). |
| **Qualification Match** | 15 | Direct match between required qualification (e.g. MBBS, GNM, B.Sc, B.Pharm) and candidate credentials (+15 pts); recognized accredited qualification on file (+10 pts). |
| **Verification & Regulatory Standing** | 15 | State Council / DigiLocker NMC / College bona fide status verified by FALE Compliance (`kyc_status = 'VERIFIED'`) (+15 pts); council registration number or university roll number submitted (+10 pts). |
| **City & Geographic Location** | 10 | Candidate residential city matches the hospital job location (+10 pts); inter-city relocation mobility (+5 pts). |
| **Experience / Track Readiness** | 5 | Clinical internship candidate match on academic track (+5 pts); or graduate experience range present (+5 pts). |

**Bounds**: $\min(\max(\text{Score}, 30), 100)$.

---

## 3. Transparency and Auditability

- The match score is computed server-side at the instant of application and persisted on the `job_applications` record (`match_score`).
- The computed percentage is visible to the **Compliance Admin** on `/admin/applications` for unbiased sorting, filtering, and shortlisting.
- Shortlisted candidates are presented to the hospital facility with their match percentage and verified credentials.
