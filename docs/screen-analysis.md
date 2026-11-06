# FALE Healthcare Platform — Comprehensive Frontend & Screen Analysis

> **Specification & Security Baseline Documentation**  
> **Workspace**: `theFaleOne-Frontend` (`FALE-healthcare-ui`) &rarr; `theFaleOne-Backend`  
> **Target Technology**: Spring Boot 3.x, Java 21, PostgreSQL, Spring Security, Flyway, Bucket4j  
> **Date**: November 2026

---

## Executive Summary & Persona Architecture

The FALE Healthcare platform is a mission-critical clinical career, locum duty dispatch, and hospital credentialing network tailored for the Indian healthcare ecosystem. The frontend consists of **33 routed views** and shared contextual modals serving **4 distinct personas**:

1. **Healthcare Student (`ROLE_CANDIDATE`, `candidate_level = STUDENT`)**:
   - Students pursuing Allied Health Sciences, Nursing (B.Sc, GNM), Physiotherapy (BPT, MPT), Pharmacy, or Medical programs.
   - **Restricted Access**: Strictly barred from claiming independent locum hospital shifts (`StudentLockCard`).
   - **Curated Experience**: Access to clinical internships, observerships, foundational CME modules, and the Huddle Academic Mentorship Circle with verified hospital consultants.
   - **Verification**: College ID card, bonafide enrollment certificate, university enrollment roll number.
2. **Graduate / Working Professional Candidate (`ROLE_CANDIDATE`, `candidate_level = FRESHER | PROFESSIONAL`)**:
   - Licensed nurses, doctors, technicians, and healthcare professionals.
   - **Full Career Access**: Permanent job applications, instant 24/7 on-call locum shift dispatch with 24-hour SLA nodal escrow payouts, full CV export, and peer clinical case discussions.
   - **Verification**: State Medical/Nursing Council licensure, DigiLocker Aadhaar/Credential KYC, degrees, and work history.
3. **Healthcare Facility / Employer (`ROLE_FACILITY`)**:
   - Accredited hospitals, diagnostic centers, and clinics.
   - **Capabilities**: Permanent job posting, emergency locum shift dispatch with automated escrow allocation, candidate pipeline tracking (screening, interview, offer), talent discovery (Resdex) with credit-based contact unlocking, and nodal escrow wallet top-ups via NEFT/RTGS/UPI UTR confirmation.
   - **Verification**: State health department hospital registration, NABH accreditation number, authorized medical superintendent/HR contact.
4. **Platform Administrator (`ROLE_ADMIN`)**:
   - Regulatory and operations compliance team.
   - **Capabilities**: Candidate KYC queue verification, hospital accreditation verification, job/shift safety moderation (wage floor and maximum duty hours compliance), system-wide application oversight, and clinical community (Huddle) case moderation.

---

## Detailed Screen-by-Screen Analysis

---

### Screen 1: App Shell & Root Navigation (`src/routes/__root.tsx`, `src/components/fale/AppShell.tsx`)
- **Route / Component**: `__root.tsx`, `AppShell.tsx`, `AdminLayout.tsx`, `AuthLayout.tsx`, `LegalLayout.tsx`
- **Personas**: All (Public, Student, Candidate, Facility, Admin)
- **Data Displayed**:
  - Top navigation bar: Active portal badge ("Candidate", "Hospital / Facility", "Admin Operations"), avatar with initials, online status indicator, theme switcher (Light / Dark / System).
  - Navigation items by role:
    - *Candidate / Student*: Home (`/home`), Jobs (`/jobs`), Shifts (`/shifts`), Earnings (`/earnings`), Applications (`/applications`), Learning (`/learning`), Huddle (`/huddle`), Notifications bell (`/notifications`).
    - *Facility*: Dashboard (`/employer`), Post a job (`/employer/post`), Post a shift (`/employer/post-shift`), Applicants (`/employer/applicants`), Candidates (`/employer/candidates`), Wallet & Escrow (`/employer/wallet`), Huddle (`/huddle`).
    - *Admin*: Overview (`/admin`), Candidate KYC (`/admin/candidates`), Facility KYC (`/admin/facilities`), Postings (`/admin/postings`), Applications (`/admin/applications`), Huddle Moderation (`/admin/huddle`).
  - Active Portal Switcher drawer / dropdown with role badges.
- **Data Submitted / Actions**:
  - Portal switch event (`setState({ portal })`).
  - Global sign-out event (`setState({ signedIn: false, ... })`).
  - Theme toggle (saved to `localStorage` key `fale-theme`).
- **Form Fields & Validation**:
  - None on root shell; triggers modals.
- **Filters / Pagination**: None.
- **Security View**:
  - *Sensitive Data*: User display name, role, organization affiliation.
  - *Authorization & IDOR*: Client currently allows free switching between portals via `switchPortal()`. **Server-Side Enforcement**: Backend JWT must encode granted authorities (`ROLE_CANDIDATE`, `ROLE_FACILITY`, `ROLE_ADMIN`). Endpoints must reject unauthorized portal operations regardless of client state.
  - *Abuse*: Switching active portal without server-validated token exchange must be prevented.

---

### Screen 2: Landing & Portal Gateway (`src/routes/index.tsx`)
- **Route**: `/`
- **Personas**: Public / Unauthenticated
- **Data Displayed**:
  - Two-mode gateway toggle: "Healthcare Professional & Student" vs "Healthcare Facility & Hiring".
  - Feature highlights, trust metrics (99.4% shift fulfillment, 24h escrow payouts, NABH compliance).
  - Auth modal / drawer: Phone number or Email input, 6-digit OTP verification input, DigiLocker sign-in badge.
- **Data Submitted / Actions**:
  - `POST /api/v1/auth/otp/send`: Phone number (+91) or email.
  - `POST /api/v1/auth/otp/verify`: Phone/email + 6-digit OTP string.
  - Social / DigiLocker auth initiation callback.
- **Form Fields & Client Validation**:
  - `phone`: 10-digit Indian mobile number (`/^[6-9]\d{9}$/`). Mandatory.
  - `email`: Standard email format.
  - `otp`: Exactly 6 numeric digits (`/^\d{6}$/`).
  - `mode`: `"pro"` | `"hire"`.
- **Security View**:
  - *Sensitive Data*: Phone numbers, emails, OTP secrets.
  - *Authorization*: Public endpoint.
  - *Abuse / OWASP Risks*:
    - **SMS OTP Flooding / Toll Fraud**: Strict rate limiting (max 3 OTP requests per IP/phone per 15 minutes via Bucket4j).
    - **Brute Force OTP**: Account lockout or OTP invalidation after 5 consecutive failed attempts.
    - **User Enumeration**: Return identical response `"OTP sent successfully if the account exists"` regardless of user presence.
    - OTPs must be stored hashed (BCrypt/SHA-256 with salt), valid for max 5 minutes, single-use only.

---

### Screen 3: Marketing & Platform Showcase (`src/routes/landing.tsx`)
- **Route**: `/landing`
- **Personas**: Public
- **Data Displayed**:
  - Marketing hero, interactive salary & locum calculator, hospital trust logos, verified clinical statistics, candidate testimonials, FAQ accordion, footer links.
- **Data Submitted / Actions**:
  - Calculator slider movements (local state).
  - Direct redirect buttons to `/onboarding?role=candidate` or `/onboarding?role=employer`.
- **Form Fields & Validation**: None.
- **Security View**:
  - Static marketing view. Ensure CSP disallows external script injection; cache static resources with immutable headers.

---

### Screen 4: Login Redirect (`src/routes/login.tsx`)
- **Route**: `/login`
- **Personas**: Public
- **Data Displayed**: Immediate redirect to `/` with auth dialog open.
- **Security View**: No backend endpoint needed beyond standard router redirect.

---

### Screen 5: Multi-Pathway Onboarding (`src/routes/onboarding.tsx`)
- **Route**: `/onboarding` (search query params: `?role=candidate|employer&portal=...`)
- **Personas**: Un-onboarded Candidate (Student or Graduate), Un-onboarded Facility
- **Data Displayed & Pathways**:
  1. **Candidate Pathway Chooser**:
     - Choice A: *"I'm a healthcare student"* &rarr; Launches **Student Pathway** (4 steps).
     - Choice B: *"No, I've graduated"* &rarr; Launches **Graduate/Professional Pathway** (6 steps).
  2. **Facility Onboarding Pathway** (5 steps).
- **Step-by-Step Data Submitted**:
  - **Student Pathway (4 steps)**:
    - *Step 1: Personal Details*: Full name, email, mobile phone, current city/area (Bengaluru zones).
    - *Step 2: Course & College*: Enrolled course (BPT, MPT, B.Sc Nursing, GNM, BMLT, DMLT, B.Sc Radiology, B.Pharm, B.Sc OT/Dialysis), College name, Study year (`1st year`, `2nd year`, `3rd year`, `4th year`, `Internship year`), expected graduation year.
    - *Step 3: Proof of Study*: Upload of College ID Card, College Bonafide Certificate, University Registration/Roll Number.
    - *Step 4: Review & Finish*: Submit student profile &rarr; Enters FALE platform with `candidate_level = STUDENT`.
  - **Graduate / Professional Candidate Pathway (6 steps)**:
    - *Step 1: Personal Details*: Name, gender, DOB, current city, address line, pincode.
    - *Step 2: Profession & Licensure*: Profession (Doctor, Nurse, Pharmacist, Physiotherapist, Lab Tech, Radiographer, OT Tech, Admin), Medical/Nursing Council name (KMC, TNC, DCI, etc.), State Council Registration Number, Registration year, DigiLocker credential import.
    - *Step 3: Education & Experience*: Highest degree, college/university, graduation year, work experience timeline (organization, designation, start month/year, end month/year or currently working).
    - *Step 4: Job Preferences*: Target departments/specialties, preferred cities, expected salary range, notice period, shift availability (Day, Night, Rotational).
    - *Step 5: Documents*: Government Photo ID (Aadhaar / Passport), State Council Registration Certificate, Degree Certificate, Resume / CV.
    - *Step 6: Review & Final Submission*: Final attestation of accuracy.
  - **Facility / Employer Pathway (5 steps)**:
    - *Step 1: Hiring Contact*: Contact person name, official hospital email, direct phone number, designation (Medical Superintendent, HR Director, Admin).
    - *Step 2: Organization Profile*: Hospital/Clinic legal name, facility type (Multi-specialty, Super-specialty, Diagnostic, Nursing Home), address, city, state, pincode, total bed capacity, ICU beds.
    - *Step 3: Verification & Accreditation*: Clinical establishment registration number, NABH accreditation level/number (Entry, Full, None), registration certificate PDF upload, authorized signatory letter.
    - *Step 4: Hiring Profile*: Core hiring departments (ICU, Emergency, OT, Ward, Diagnostics), active staffing requirements.
    - *Step 5: Review & Submit*: Legal declaration of statutory wage compliance.
- **Form Fields & Validation**:
  - Full Name: 2–100 chars, alphabetic + spaces/periods.
  - Mobile: 10 digits (`/^[6-9]\d{9}$/`).
  - Email: Valid email format, max 150 chars.
  - Reg Number: Alphanumeric with slashes (`/^[A-Z0-9\/-]{4,30}$/i`).
  - Beds: Integer between 1 and 5000.
  - Document Uploads: PDF, PNG, JPEG only; max 5MB per document.
- **Security View**:
  - *Sensitive Data*: High PII (Aadhaar numbers via DigiLocker, home addresses, dates of birth, medical council licenses, hospital registration certificates).
  - *Authorization*: Authenticated user matching the session user ID. Must prevent overwriting another user's onboarding data.
  - *File Upload Security*: Validate MIME type via Apache Tika/magic bytes (not solely client-sent `Content-Type`), generate secure random UUID file names, store outside web root or in private S3 bucket, enforce virus scanning.
  - *OWASP API*: Prevent mass assignment by binding only dedicated onboarding DTO fields to entities.

---

### Screen 6: Public Credential Verification Badge (`src/routes/verify.tsx`)
- **Route**: `/verify` (e.g. `?id=...&type=candidate|facility`)
- **Personas**: Public / Third-party verification
- **Data Displayed**:
  - Verification badge, clinician/facility masked name, council registration status ("Verified by State Council"), accreditation validity ("NABH Active"), verification timestamp.
- **Data Submitted / Actions**:
  - `GET /api/v1/verifications/{verificationId}`
- **Security View**:
  - *Sensitive Data*: Mask all personal PII (e.g., show "Dr. A**** P****", mask phone/email, show only last 4 digits of registration number).
  - *Rate Limiting*: Prevent automated scrapers from enumerating verification IDs (rate limit to 30 requests/min per IP).

---

### Screen 7: Candidate & Student Dashboard (`src/routes/home.tsx`)
- **Route**: `/home`
- **Personas**: `ROLE_CANDIDATE` (Student, Fresher, Professional)
- **Data Displayed**:
  - User greeting: Personalized for Student (*"Healthcare Student"*) vs Clinician (*"Dr. Ananya Patel"*).
  - Quick stats: Profile completeness score (%), Active applications count, Wishlisted roles.
  - Urgent Shift Recommendations (Graduates only: instant locum shift cards with hourly rate, surge badge, duty time).
  - Student Mode Variant:
    - Shift claim card shows `StudentLockCard` or notice: *"Shift duty restricted for active students. Independent locum shifts require verified council registration."*
    - Replaces shift list with **Student Clinical Internships & Observerships** (with monthly stipend e.g. ₹15,000–₹22,000/mo).
    - CV Export button: Downloads *Student Academic Portfolio* instead of standard NABH Clinical CV.
  - Recommended Jobs & Filter tabs: "All", "Internships", "Top match", "Urgent", "Day shift".
- **Data Submitted / Actions**:
  - `POST /api/v1/shifts/{id}/claim`: Claim emergency shift (Graduates only).
  - `POST /api/v1/jobs/{id}/save`: Bookmark/wishlist job.
  - `GET /api/v1/candidates/me/export-cv`: Download PDF CV/portfolio.
- **Security View**:
  - *Sensitive Data*: Candidate earnings, profile readiness, application statuses.
  - *Authorization & Student Lockdown*: Server-side service must check `candidate_level`: if `STUDENT`, reject `/shifts/{id}/claim` with HTTP 403 Forbidden.
  - *BOLA / IDOR*: CV export must strictly stream the authenticated user's CV.

---

### Screen 8: Job & Internship Marketplace (`src/routes/jobs.index.tsx`)
- **Route**: `/jobs` (query parameters: `?q=...&city=...&dept=...`)
- **Personas**: `ROLE_CANDIDATE` (Student, Fresher, Professional)
- **Data Displayed**:
  - Search bar with live keyword search.
  - Multi-faceted filter sidebar: Department (Critical Care, Emergency, OT, etc.), City (Bengaluru, Mumbai, Delhi, etc.), Qualification, Employment Type (Full-time, Locum, Internship), Shift type (Day, Night, Rotational), Salary range slider, Experience range.
  - Job Cards: Hospital name, logo, role title, department, match percentage (algorithm score), salary/stipend, openings count, urgency badge, "Save" icon.
  - Student Filter: If user is `STUDENT`, non-internship clinical roles are excluded by default or labeled as requiring graduation.
- **Data Submitted / Actions**:
  - `GET /api/v1/jobs`: Query parameters: `search`, `city`, `department`, `employmentType`, `minSalary`, `maxSalary`, `page`, `size`, `sort`.
  - `POST /api/v1/jobs/{id}/save`: Toggle saved state.
- **Form Fields & Validation**:
  - Max page size capped at 50 (server limit 100).
- **Security View**:
  - Publicly visible job postings must only show approved hospital postings (`status = APPROVED`).
  - Draft or rejected hospital postings must never be returned to candidates.

---

### Screen 9: Job & Internship Detail View (`src/routes/jobs.$id.tsx`)
- **Route**: `/jobs/:id`
- **Personas**: `ROLE_CANDIDATE` (Student, Fresher, Professional)
- **Data Displayed**:
  - Full role specifications: Hospital profile, department, openings, duty schedule, compensation breakdown, mandatory qualifications, council registration requirement, hospital location map coordinates.
  - Application status banner: "Not Applied", "Under Admin Screening", "Shortlisted", "Interview Scheduled", "Withdrawn".
  - Action buttons: "Apply Now" (triggers modal/confirmation), "Withdraw Application" (opens `WithdrawModal.tsx`).
- **Data Submitted / Actions**:
  - `POST /api/v1/jobs/{id}/apply`: Applies with candidate profile dossier.
  - `POST /api/v1/applications/{appId}/withdraw`: With reason string.
- **Security View**:
  - *Authorization*: Candidates can only withdraw their own applications.
  - *Business Logic Safety*: Prevent duplicate applications to the same active job posting via database unique constraint `(candidate_id, job_id) WHERE status != 'WITHDRAWN'`.

---

### Screen 10: Instant Locum Shifts Marketplace (`src/routes/shifts.tsx`)
- **Route**: `/shifts`
- **Personas**: `ROLE_CANDIDATE` (Graduate: Fresher & Professional only)
- **Data Displayed**:
  - Active locum duties: Shift date ("Tonight · 20:00–08:00"), hours (e.g. 12 hrs), hourly rate (e.g. ₹450/hr), surge bonus (e.g. "+₹50/hr Night Surge"), verified facility name, perks (Dinner provided, Cab drop), slots remaining.
  - Filter by date, specialty, night vs day.
  - **Student Persona Behavior**:
    - Complete view is masked with `StudentLockCard`.
    - Shows message: *"Shift duty restricted for active students. Independent locum shifts require verified council registration and clinical license."*
    - CTA buttons: "Explore Internships" &rarr; `/jobs`, "Earn Student CME Points" &rarr; `/learning`.
- **Data Submitted / Actions**:
  - `POST /api/v1/shifts/{id}/claim`: Claim shift slot.
- **Security View**:
  - *High-Risk Financial & Clinical Action*: Only council-verified clinicians may claim shifts.
  - *Concurrency & Race Conditions*: Two nurses claiming the last 1 opening simultaneously &rarr; **Must use Pessimistic Write Lock (`SELECT FOR UPDATE`)** or optimistic locking with `@Version` on the shift entity to prevent over-allocation.
  - *Student Guard*: Service must assert `candidate_level != STUDENT` and `kyc_status == VERIFIED`.

---

### Screen 11: Candidate Applications Pipeline (`src/routes/applications.tsx`)
- **Route**: `/applications`
- **Personas**: `ROLE_CANDIDATE` (Student, Fresher, Professional)
- **Data Displayed**:
  - Two tabs: "Active Applications" and "Archived / Closed".
  - Application cards: Job/Internship title, Hospital name, applied date, status badge ("Under Admin Screening", "Shortlisted", "Interview", "Offer Extended", "Rejected", "Withdrawn"), match score.
  - Student Mode Variant: Displays applied internships/observerships.
- **Data Submitted / Actions**:
  - `GET /api/v1/applications/me`: Lists candidate's own applications.
  - `POST /api/v1/applications/{id}/withdraw`: Withdraws application.
- **Security View**:
  - *BOLA / IDOR*: Candidate must only query their own application records (`WHERE candidate_id = :currentUserId`).
  - Withdrawing another user's application must return 403 Forbidden.

---

### Screen 12: Candidate Earnings & Escrow Payouts (`src/routes/earnings.tsx`)
- **Route**: `/earnings`
- **Personas**: `ROLE_CANDIDATE` (Graduate / Clinician, Student)
- **Data Displayed**:
  - Financial Metrics: Total Disbursed (₹), In Escrow under 24h SLA (₹), Pending Review (₹).
  - Bank / UPI Payout Method configuration card:
    - UPI ID (`username@bank`) or Bank Account (Account number, masked, IFSC, Bank name, Account holder name).
    - Status: "Verified" via Penny Drop or "Pending".
  - Locum Duty Payout History: Shift title, hospital name, date, hours worked, hourly rate, gross amount, deduction/TDS, net disbursement, UTR reference number, Payout slip PDF download button.
  - **Student Persona Behavior**:
    - Discloses: *"Independent shift payouts locked until graduation & council license. Stipends from verified clinical internships appear here."*
- **Data Submitted / Actions**:
  - `GET /api/v1/payouts/me`: Payout transaction history.
  - `PUT /api/v1/payouts/me/method`: Set or update payout method (UPI or Bank account).
  - `GET /api/v1/payouts/{payoutId}/slip`: Generate and stream official PDF payout slip with digital hash.
- **Form Fields & Validation**:
  - UPI ID: Valid VPA format (`/^[\w.-]+@[\w.-]+$/`).
  - Bank Account: 9–18 digits. Confirm account number must match.
  - IFSC: 11 characters (`/^[A-Z]{4}0[A-Z0-9]{6}$/`).
  - Account Holder Name: Min 2, max 100 characters.
- **Security View**:
  - *Extremely Sensitive Banking Data*:
    - Account numbers must be masked in responses (`••••••••1234`).
    - Raw bank account updates must be protected against CSRF and IDOR.
    - Rate-limit payout method changes to prevent unauthorized account hijacking.
  - *Financial Race Conditions*: Payout status transitions must execute inside serializable/isolated transactions with idempotency keys.

---

### Screen 13: Clinician & Student Profile Dossier (`src/routes/profile.tsx`)
- **Route**: `/profile`
- **Personas**: `ROLE_CANDIDATE` (Student, Fresher, Professional)
- **Data Displayed**:
  - Profile header: Avatar, full name, role/specialty, city, verification badge ("Council Verified" or "College Verified").
  - Tab 1: "Clinical Profile" (Bio, Work Experience timeline, Degree & Education, Council Registration details).
  - Tab 2: "Internship / Career Preferences" (Target cities, expected compensation, notice period, shift timings).
  - Tab 3: "Documents & Credentials" (Aadhaar KYC badge, Council Registration certificate, BLS/ACLS certificates, Degree certificates).
  - **Student Mode Variant**:
    - Header: *"Student Trainee"*, Course & College name, *"Internship Ready"*.
    - Section: **"College Affiliation & Student Enrollment"** (Student Roll Number, University Bona Fide status, University name).
    - Documents: College ID Card, Bonafide Letter, AHA BLS Student Credential.
    - Experience: Supervised Ward Rotations & Academic Postings.
    - Target stipend: ₹15,000–₹22,000 / month.
- **Data Submitted / Actions**:
  - `GET /api/v1/candidates/me/profile`: Fetch current profile.
  - `PUT /api/v1/candidates/me/profile`: Update profile fields.
  - `POST /api/v1/candidates/me/documents`: Upload new credential document.
  - `DELETE /api/v1/candidates/me/documents/{docId}`: Delete document.
- **Security View**:
  - *High PII*: Full legal name, date of birth, personal contact, address.
  - *Authorization*: Only authenticated candidate may edit their own profile. Council verification status cannot be self-modified by the candidate (read-only; set exclusively by admin).

---

### Screen 14: CME & Skills Academy Catalog (`src/routes/learning.index.tsx`)
- **Route**: `/learning`
- **Personas**: `ROLE_CANDIDATE` (Student, Fresher, Professional)
- **Data Displayed**:
  - Catalog of accredited CME courses: "Basic Life Support (BLS)", "Advanced Cardiac Life Support (ACLS)", "Infection Control in ICUs", "NABH Clinical Documentation Standards".
  - Search and Category filter ("Critical Care", "Nursing", "Diagnostics", "NABH Compliance").
  - "My Learning" tab with enrolled modules and progress percentage (0–100%).
  - Student Mode Variant: Highlighted foundational modules and student logbook competencies.
- **Data Submitted / Actions**:
  - `GET /api/v1/courses`: Search and list courses.
  - `POST /api/v1/courses/{id}/enroll`: Enroll in course module.
- **Security View**:
  - Standard authenticated read/write. Prevent enrollment tampering.

---

### Screen 15: Course Player & Module Progress (`src/routes/learning.$id.tsx`)
- **Route**: `/learning/:id`
- **Personas**: `ROLE_CANDIDATE`
- **Data Displayed**:
  - Video lecture / interactive slides, module outline, lesson completion checklist, CME credit points earned, certificate generation button upon 100% completion.
- **Data Submitted / Actions**:
  - `PUT /api/v1/courses/{id}/progress`: Update completed lesson IDs.
  - `POST /api/v1/courses/{id}/certificate`: Issue signed completion certificate.
- **Security View**:
  - Server must verify that all mandatory lessons and assessments are genuinely passed before issuing a CME certificate.

---

### Screen 16: Clinical Community Huddle (`src/routes/huddle.tsx`)
- **Route**: `/huddle`
- **Personas**: All authenticated personas (`ROLE_CANDIDATE` [Student, Fresher, Professional], `ROLE_FACILITY`)
- **Data Displayed**:
  - Community Feed: Clinical posts categorized as "Thought", "Experience", "Case Study" (de-identified), "Question".
  - Hospital Posts: "Hospital Update", "CME & Workshop Event".
  - Mentor Tab: AMA questions, consultant answers, verified mentor profiles.
  - Community Safety Check Indicator: Real-time analysis of post content warning against patient identifiable health information (PHI/HIPAA), contact sharing, or commercial advertising.
  - **Student Persona Behavior**:
    - Banner: *"Student Clinical Circle · Academic Mentorship Active"*.
    - Can ask questions on the Mentor Tab for viva prep and clinical case queries.
    - Badged as "Student Trainee"; cannot answer as a licensed consultant.
- **Data Submitted / Actions**:
  - `GET /api/v1/huddle/posts`: Feed with filters (`field`, `type`, `page`, `size`).
  - `POST /api/v1/huddle/posts`: Create post (title, content, type, field/specialty, de-identified tag).
  - `POST /api/v1/huddle/posts/{id}/react`: Upvote/like post.
  - `POST /api/v1/huddle/posts/{id}/comments`: Add clinical comment.
  - `POST /api/v1/huddle/questions`: Post mentor question.
  - `POST /api/v1/huddle/questions/{id}/answers`: Answer mentor question (Restricted to verified clinicians).
  - `POST /api/v1/huddle/posts/{id}/flag`: Flag post for clinical ethics or patient privacy violation.
- **Form Fields & Validation**:
  - Content: Min 10, max 3000 characters.
  - Title: Min 5, max 150 characters.
  - Allowed types: `Thought`, `Experience`, `Case`, `Question`, `Update`, `Event`.
- **Security View**:
  - *Clinical Safety & PHI Protection*: Strict server-side content filter against patient names, hospital MRN numbers, phone numbers, and solicitation links.
  - *Authorization*:
    - Facilities can only post `Update` and `Event`.
    - Students cannot post answers as mentors.
    - Flagged posts automatically queued for Admin review (`/admin/huddle`).
  - *Rate Limiting*: Max 10 posts and 30 comments per hour per user to stop spam.

---

### Screen 17: Notification Center (`src/routes/notifications.tsx`)
- **Route**: `/notifications`
- **Personas**: All authenticated users
- **Data Displayed**:
  - List of notifications: Application status changes, shift claim confirmations, KYC document approvals/rejections, escrow disbursements, Huddle mentions.
  - Filter by "All", "Applications", "Shifts", "System & KYC".
  - "Mark all as read" button.
- **Data Submitted / Actions**:
  - `GET /api/v1/notifications`: List user notifications.
  - `PUT /api/v1/notifications/{id}/read`: Mark single notification read.
  - `PUT /api/v1/notifications/mark-all-read`: Bulk update.
- **Security View**:
  - *IDOR*: Users must only access and modify notifications belonging to their own user ID.

---

### Screen 18: Support & Clinical Concierge (`src/routes/support.tsx`, `ClinicalConciergeWidget.tsx`)
- **Route**: `/support`
- **Personas**: All
- **Data Displayed**:
  - FAQ categories: Shifts & Escrow, Credentialing & DigiLocker, Hospital Hiring, Student Internships.
  - Ticket submission form: Issue category, subject, description, priority, attachment.
  - Active tickets with resolution status.
  - Floating 24/7 Clinical Concierge chat widget.
- **Data Submitted / Actions**:
  - `POST /api/v1/support/tickets`: Create support inquiry.
  - `GET /api/v1/support/tickets/me`: List my tickets.
- **Form Fields & Validation**:
  - Subject: 5–150 chars.
  - Description: 10–2000 chars.
  - Category: Enum (`ESCROW`, `KYC`, `SHIFT_DISPUTE`, `TECHNICAL`).
- **Security View**:
  - Sanitize input against XSS; tickets must only be viewable by the ticket creator and admins.

---

### Screen 19: Privacy Policy (`src/routes/privacy.tsx`) & Screen 20: Terms of Service (`src/routes/terms.tsx`)
- **Routes**: `/privacy`, `/terms`
- **Personas**: Public
- **Data Displayed**: Statutory privacy disclosure, NABH compliance terms, nodal escrow disclaimers.
- **Security View**: Static legal views.

---

### Screen 21: Hospital Employer Dashboard (`src/routes/employer.index.tsx`)
- **Route**: `/employer`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Hospital KPIs: Active Job Openings, Active Shift Coverage (%), Total Applicants in Pipeline, Escrow Wallet Balance (₹).
  - Live Shift Roster: Tonight's duty coverage, checked-in locum clinicians, emergency open slots.
  - Recent Applicants table: Candidate name, applied role, match score, council verification status, quick action buttons ("Shortlist", "Reject").
  - Quick post actions: "Post New Permanent Role", "Dispatch Locum Shift".
- **Data Submitted / Actions**:
  - `GET /api/v1/employer/dashboard`: Aggregated metrics.
- **Security View**:
  - *BOLA / Multi-Tenant Isolation*: Metrics and rosters must strictly filter by the authenticated facility ID (`facility_id = :currentFacilityId`).

---

### Screen 22: Post a Permanent / Contract Job (`src/routes/employer.post.tsx`)
- **Route**: `/employer/post`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Job creation wizard: Job Title, Department (ICU, Emergency, Cardiology, etc.), Employment Type (Full-time, Part-time, Contract, Internship), Openings count, Minimum experience required (Years), Salary range (Min–Max ₹/month), Minimum qualification required, Shift timings, Job description, Perks & benefits.
- **Data Submitted / Actions**:
  - `POST /api/v1/employer/jobs`: Creates job posting in `PENDING_APPROVAL` status.
- **Form Fields & Validation**:
  - Title: 3–100 chars.
  - Openings: Integer 1–100.
  - Min Salary: Must be >= statutory minimum wage floor.
  - Max Salary: Must be >= Min Salary.
  - Description: 20–5000 chars.
- **Security View**:
  - *Trust Boundary*: Hospitals cannot directly mark postings as `APPROVED` or `ACTIVE` if platform policy mandates admin screening (`PENDING_APPROVAL` default).
  - *CSRF / Double Submit*: Idempotency key header supported.

---

### Screen 23: Post an Emergency Locum Shift (`src/routes/employer.post-shift.tsx`)
- **Route**: `/employer/post-shift`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Emergency shift dispatch form: Shift Title (e.g. "ICU Night Staff Nurse"), Department, Duty Date, Start time & End time (e.g. 20:00–08:00, 12 hours), Hourly payout rate (₹/hour), Night surge bonus (₹/hour), Openings count (e.g. 2 slots), Included perks (Dinner provided, Cab drop, Rest quarters).
  - **Live Escrow Allocation Calculator**:
    - Calculates: `Total Lock Amount = (Hours * (Rate + Surge)) * Openings`.
    - Displays current Escrow Balance vs Required Allocation. If balance is insufficient, prompts wallet top-up.
- **Data Submitted / Actions**:
  - `POST /api/v1/employer/shifts`: Posts shift and locks escrow balance.
- **Form Fields & Validation**:
  - Hours: 4 to 24 hours.
  - Hourly Rate: Must satisfy minimum specialty floor (e.g., Min ₹250/hr for nurses, ₹800/hr for doctors).
  - Date: Must be present or future date (not past).
- **Security View**:
  - *Critical Financial Operation (Escrow Lock)*:
    - Server must recalculate the total lock amount server-side; **NEVER trust client-calculated total**.
    - Must execute inside a DB transaction with pessimistic lock on the facility wallet: `SELECT * FROM facility_wallets WHERE facility_id = :id FOR UPDATE`.
    - Verify `available_balance >= required_lock_amount`.
    - Atomic transfer: Deduct `available_balance`, add to `locked_escrow_balance`, create `wallet_transaction` record (`type = ESCROW_HOLD`).

---

### Screen 24: Hospital Hiring Pipeline (`src/routes/employer.applicants.tsx`)
- **Route**: `/employer/applicants`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Candidates applied to hospital postings: Candidate name, applied role, city, experience, council verification badge, match score (%), resume preview link.
  - Pipeline stages filter: "All", "Screening", "Shortlisted", "Interview", "Offered", "Hired", "Rejected".
  - Candidate Dossier modal: Detailed educational history, council registration number, verification documents, interview scheduling date/time picker.
- **Data Submitted / Actions**:
  - `GET /api/v1/employer/applications`: Filter by job, status.
  - `PUT /api/v1/employer/applications/{id}/status`: Transition stage (`SHORTLISTED`, `INTERVIEW`, `HIRED`, `REJECTED`).
  - `POST /api/v1/employer/applications/{id}/interview`: Schedule interview date and venue/link.
- **Security View**:
  - *BOLA / IDOR*: Hospitals can only view applicants who applied to *their own* postings.
  - State machine safety: Prevent invalid transitions (e.g. from `REJECTED` directly to `HIRED` without review).

---

### Screen 25: Talent Resdex & Credit Unlock System (`src/routes/employer.candidates.tsx`)
- **Route**: `/employer/candidates`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Talent search directory: Candidate cards with **Masked Contact Details** by default (e.g., `+91 ••••• •••••`, `•••••••@hospital.com`, `Reg #••••••••`).
  - Credit Balance counter: (e.g. "3 Credits Remaining").
  - "Unlock Contact" button: Deducts 1 credit to reveal phone, email, and council registration number.
  - "Buy Credits" modal: Package tiers (Starter: 10 credits ₹4,999; Growth: 30 credits ₹11,999; Enterprise: 100 credits ₹29,999).
- **Data Submitted / Actions**:
  - `GET /api/v1/employer/candidates`: Search candidates with filters (`role`, `city`, `verifiedOnly`). Returns masked fields for non-unlocked profiles.
  - `POST /api/v1/employer/candidates/{candidateId}/unlock`: Atomically consumes 1 credit and returns unmasked contact details.
  - `POST /api/v1/employer/credits/purchase`: Purchase credit package.
- **Security View**:
  - *Data Privacy & Anti-Scraping*:
    - **Crucial Server-Side Masking**: The API must never return raw phone, email, or full reg number in `GET /candidates`! Masking must occur in the backend DTO mapper, not on the frontend.
    - Unlock operation must be an atomic transaction checking `credits >= 1`, decrementing credit balance, recording in `unlocked_candidates` table, and logging an audit event.

---

### Screen 26: Hospital Escrow Wallet & Top-Up (`src/routes/employer.wallet.tsx`)
- **Route**: `/employer/wallet`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Financial Balances:
    - **Available Nodal Balance (₹)**: Readily allocatable for shifts.
    - **Locked Shift Escrow (₹)**: Committed to upcoming and active locum shifts.
    - **Total Lifetime Disbursed (₹)**.
  - Hospital Bank Account (for refunds/escrow sub-account): Bank name, account number (masked), IFSC, branch, verification status ("Verified", "Pending Penny Drop").
  - Transaction History: Top-up, Escrow allocation hold, Locum duty disbursement, Credit purchase, Standby credit, Refund. Each with date, amount, status, invoice ID, and UTR reference number.
  - Top-Up Modal: Enter top-up amount, bank NEFT/RTGS details, submit 12-digit UTR payment reference for administrative reconciliation.
- **Data Submitted / Actions**:
  - `GET /api/v1/employer/wallet`: Current balance and bank details.
  - `GET /api/v1/employer/wallet/transactions`: Paginated transaction ledger.
  - `POST /api/v1/employer/wallet/topup`: Submit top-up request with UTR ref number and amount.
  - `PUT /api/v1/employer/wallet/bank-account`: Update hospital bank account.
- **Form Fields & Validation**:
  - Top-up amount: Min ₹1,000, Max ₹10,00,000.
  - UTR: 12 to 22 alphanumeric characters (`/^[A-Z0-9]{12,22}$/i`).
- **Security View**:
  - *Critical Financial Integrity*:
    - Top-up requests must be recorded with status `PENDING_RECONCILIATION` until verified against bank statement or payment gateway webhook.
    - UTR numbers must be unique (`UNIQUE (utr)`) to prevent replay attacks and double crediting.
    - All wallet debits/credits must be logged with immutable financial ledger entries.

---

### Screen 27: Hospital Profile & Accreditation (`src/routes/employer.profile.tsx`)
- **Route**: `/employer/profile`
- **Personas**: `ROLE_FACILITY`
- **Data Displayed**:
  - Hospital public identity: Name, logo, banner photo, facility overview, bed count, ICU bed capacity, NABH accreditation badge and expiration date.
  - Key contact person details: Name, email, mobile phone, medical superintendent contact.
  - Campus photo gallery, specialties offered.
- **Data Submitted / Actions**:
  - `GET /api/v1/employer/profile`: Hospital details.
  - `PUT /api/v1/employer/profile`: Update facility description, photos, specialties.
- **Security View**:
  - Accreditation status and license numbers are read-only to the hospital once verified by admin; updates to license/NABH trigger a re-verification workflow.

---

### Screen 28: Admin Master Control Dashboard (`src/routes/admin.index.tsx`)
- **Route**: `/admin`
- **Personas**: `ROLE_ADMIN`
- **Data Displayed**:
  - System KPIs: Total Active Doctors & Clinicians, Total Accredited Hospitals, Active Locum Shifts, Escrow Under Management (₹).
  - Pending Action Queues: Pending Doctor KYC count, Pending Hospital KYC count, Pending Shift/Job approvals count, Flagged Huddle reports.
  - Platform financial volume graph, live compliance audit trail.
- **Data Submitted / Actions**:
  - `GET /api/v1/admin/dashboard`: Master metrics.
  - `GET /api/v1/admin/audit-logs`: Immutable system audit log.
- **Security View**:
  - *Strict Role Restriction*: Only users with `ROLE_ADMIN` can access `/api/v1/admin/**`. Return 403 Forbidden for all other roles.

---

### Screen 29: Admin Candidate & Student KYC Queue (`src/routes/admin.candidates.tsx`)
- **Route**: `/admin/candidates`
- **Personas**: `ROLE_ADMIN`
- **Data Displayed**:
  - Queue of candidate submissions pending verification.
  - Filter: "All", "Doctors / Clinicians", "Students & Interns".
  - Candidate details: Name, email, phone, profession, qualification, State Medical/Nursing Council, Reg Number, uploaded certificates.
  - **Student Verification Items**: College Name, Enrolled Course, Study Year, College ID Card preview, Bonafide letter preview, University registration number.
  - Action buttons: "Verify & Approve", "Reject with Feedback", "Request Re-upload".
- **Data Submitted / Actions**:
  - `GET /api/v1/admin/kyc/candidates`: Pending candidate queue.
  - `PUT /api/v1/admin/kyc/candidates/{id}/verify`: Approves KYC (`status = VERIFIED`).
  - `PUT /api/v1/admin/kyc/candidates/{id}/reject`: Rejects KYC with mandatory reason string.
- **Form Fields & Validation**:
  - Rejection reason: 5–500 characters.
- **Security View**:
  - *Audit Trail*: Every verification and rejection must record `admin_user_id`, `timestamp`, `action`, and `ip_address` in the immutable `audit_events` table.
  - Rejection reasons must not leak internal compliance criteria to untrusted parties.

---

### Screen 30: Admin Hospital Accreditation Queue (`src/routes/admin.facilities.tsx`)
- **Route**: `/admin/facilities`
- **Personas**: `ROLE_ADMIN`
- **Data Displayed**:
  - Queue of hospitals and healthcare facilities pending onboarding approval.
  - Facility Details: Hospital name, type, city, state, bed count, license number, NABH accreditation number, contact person phone/email, uploaded legal incorporation documents.
  - Actions: "Approve Facility", "Reject Facility", "Flag for Field Inspection".
- **Data Submitted / Actions**:
  - `GET /api/v1/admin/kyc/facilities`: Pending facility queue.
  - `PUT /api/v1/admin/kyc/facilities/{id}/approve`: Sets status to `VERIFIED`.
  - `PUT /api/v1/admin/kyc/facilities/{id}/reject`: Rejects with reason.
- **Security View**:
  - Critical trust boundary: An approved facility gains the authority to hire clinicians and disburse locum funds. Must be strictly auditable.

---

### Screen 31: Admin Job & Shift Posting Moderation (`src/routes/admin.postings.tsx`)
- **Route**: `/admin/postings`
- **Personas**: `ROLE_ADMIN`
- **Data Displayed**:
  - Hospital job and locum shift postings awaiting publication.
  - Checks: Statutory minimum wage floor check, maximum allowable duty hours (e.g. max 12 hours continuous duty for critical care), mandatory qualification check.
  - Actions: "Approve Posting", "Reject Posting".
- **Data Submitted / Actions**:
  - `GET /api/v1/admin/postings/pending`: Moderation queue.
  - `PUT /api/v1/admin/postings/{id}/approve`: Approves posting &rarr; publishes to candidate portal.
  - `PUT /api/v1/admin/postings/{id}/reject`: Rejects posting with reason.
- **Security View**:
  - Prevents non-compliant hospital duty postings from appearing on candidate feeds.

---

### Screen 32: Admin Applications Oversight (`src/routes/admin.applications.tsx`)
- **Route**: `/admin/applications`
- **Personas**: `ROLE_ADMIN`
- **Data Displayed**:
  - System-wide view of applications across all facilities.
  - Match scores, candidate council status, hospital hiring actions.
  - Dispute resolution tools for canceled shifts or contested locum attendance.
- **Data Submitted / Actions**:
  - `GET /api/v1/admin/applications`: Global application search.
  - `PUT /api/v1/admin/applications/{id}/override`: Compliance intervention.
- **Security View**:
  - Mask PII in bulk exports; enforce strict admin authorization.

---

### Screen 33: Admin Clinical Huddle Moderation (`src/routes/admin.huddle.tsx`)
- **Route**: `/admin/huddle`
- **Personas**: `ROLE_ADMIN`
- **Data Displayed**:
  - Live moderation queue: Flagged clinical posts, patient privacy (PHI) reports, commercial spam reports.
  - Post metrics: Upvotes, comment count, author role and verification level.
  - Mentor Session Approval Queue: Hospital consultant requests to host clinical AMA / mentorship circles.
  - Actions: "Dismiss Flag", "Remove Post", "Suspend Author Huddle Privileges", "Approve Mentor Session".
- **Data Submitted / Actions**:
  - `GET /api/v1/admin/huddle/flagged`: Flagged post list.
  - `DELETE /api/v1/admin/huddle/posts/{id}`: Soft-delete violating post with audit note.
  - `PUT /api/v1/admin/huddle/users/{id}/moderation-status`: Mute or ban user from Huddle.
- **Security View**:
  - Protect against clinical misinformation and patient confidentiality breaches; maintain immutable log of removed content.

---

## Shared Modals & Utility Components

| Component | Files | Primary Security & Functional Responsibilities |
| :--- | :--- | :--- |
| **DigiLocker Integration** | `src/components/fale/DigiLockerModal.tsx` | Simulates/connects to MeriPehchan / DigiLocker OAuth2 workflow. Transmits verified Aadhaar/NMC credentials. Must use state/PKCE to prevent CSRF. |
| **Resume Parser** | `src/components/fale/ResumeParserModal.tsx` | Parses PDF/DOCX resumes using client-side mammoth/pdfjs. Server must also validate and sanitize parsed fields before persisting. |
| **Student Shift Lock** | `src/components/fale/StudentLockCard.tsx` | Enforces behavioral boundary preventing active students from claiming clinical shifts. Backed by HTTP 403 in API. |
| **Application Withdrawal** | `src/components/fale/WithdrawModal.tsx` | Captures withdrawal reason; prevents re-application abuse. |
| **Cookie Consent** | `src/components/fale/CookieConsent.tsx` | Manages cookie preferences (Essential, Functional, Analytics). Strictly HttpOnly tokens for session. |
| **Clinical Concierge** | `src/components/fale/ClinicalConciergeWidget.tsx` | Real-time support widget for shift and escrow queries. |

---

## Security Matrix Summary

| Endpoint Group | Allowed Personas | OWASP / Security Safeguards |
| :--- | :--- | :--- |
| `/api/v1/auth/**` | Public | Rate limiting (Bucket4j: 3 req/15 min for OTP), generic error responses, timing-attack-safe comparisons. |
| `/api/v1/candidates/**` | `ROLE_CANDIDATE` (Student, Graduate) | Ownership checks (`WHERE candidate_id = :userId`), BOLA prevention, PII encryption at rest. |
| `/api/v1/shifts/{id}/claim` | `ROLE_CANDIDATE` (`candidate_level != STUDENT`) | Method security + service guard: Student lockdown. Concurrency protection via DB pessimistic lock (`FOR UPDATE`). |
| `/api/v1/employer/shifts` | `ROLE_FACILITY` | Recalculate escrow lock amount server-side; atomic wallet debit and escrow hold in serializable transaction. |
| `/api/v1/employer/candidates` | `ROLE_FACILITY` | Mask contact PII in response DTO by default. Atomically decrement credits upon unlock. |
| `/api/v1/employer/wallet/**` | `ROLE_FACILITY` | Unique constraint on UTR number to prevent replay. Manual admin reconciliation for top-ups. |
| `/api/v1/admin/**` | `ROLE_ADMIN` only | Method security `@PreAuthorize("hasRole('ADMIN')")`. Complete immutable audit logging (`audit_events`). |
| `/api/v1/huddle/**` | Authenticated | PHI detection, rate limiting on post/comment creation, soft-deletion on moderation. |
