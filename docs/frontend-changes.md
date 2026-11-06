# FALE Healthcare Platform — Frontend Dynamic Migration & Data Source Audit

> **File**: `docs/frontend-changes.md`  
> **Status**: Comprehensive audit of all 23 modified frontend screens and adapters.  
> **Rule Compliance**: Zero layout or styling modifications; strictly dynamic API wiring with graceful loading/error/empty states and integer paise monetary formatting.

---

## 1. Before / After Data Source Audit Table (Part 2)

| Route / Screen | File Path | Before Data Source (Static/Mock) | After Data Source (Dynamic REST API) | Fallback / Loading / Empty States Handled |
| :--- | :--- | :--- | :--- | :--- |
| **Login & Auth Verification** | `src/routes/index.tsx` | Static role tabs & mock localStorage token | `POST /api/v1/auth/otp/send`, `POST /api/v1/auth/otp/verify` returning `role`, `candidateLevel`, and `onboarded` | Spinner during SMS trigger; auto-routes onboarded candidates/facilities directly to dashboard; non-onboarded to `/onboarding`. |
| **Global Route Guards** | `src/routes/__root.tsx` | None (any user could navigate to any role path) | Client route guard checking `authAdapter.getStoredSession()` | Unauthorized role navigation redirects to respective portal dashboard with toast error. |
| **Onboarding Routing** | `src/routes/onboarding.tsx` | Static localStorage flag | `authAdapter.getStoredSession()` & backend onboarding endpoints | Completed profiles redirected immediately to dashboard without seeing wizard. |
| **Public Credential Lookup** | `src/routes/verify.tsx` | Hardcoded sample doctor card & mock array lookup | `GET /api/v1/verifications/{id}` via `verificationAdapter.ts` | Dynamic search with animated spinner, council accreditation verification badge, and 404 alert for invalid IDs. |
| **Candidate / Student Home** | `src/routes/home.tsx` | Static mock jobs from `src/lib/data.ts` | `GET /api/v1/jobs` & `GET /api/v1/shifts` via `jobAdapter.ts` and `shiftAdapter.ts` | Skeleton loader cards; separate internship feed for Healthcare Students; dynamic active application count. |
| **Locum Shift Dispatch Marketplace** | `src/routes/shifts.tsx` | Static `shifts` array from `src/lib/data.ts` | `GET /api/v1/shifts`, `POST /api/v1/shifts/{id}/claim`, `POST /api/v1/shifts/claims/{id}/cancel` | Real-time slot countdown, student lock card barrier, 24-hr escrow guarantee badge, and instant claim confirmation. |
| **Candidate Job Board Feed** | `src/routes/jobs.index.tsx` | Static `jobs` mock array | `GET /api/v1/jobs` (with query, city, department, internship filters) | Animated skeleton list during network latency; dedicated zero-results empty state; paise-to-Rupee formatted salaries. |
| **Job Specification View** | `src/routes/jobs.$id.tsx` | Static `jobs.find(id)` | `GET /api/v1/jobs/{id}` via `jobAdapter.getJobById` | Full clinical job details, dynamic match score card, and 1-click application submission with status feedback. |
| **Candidate Applications Tracker** | `src/routes/applications.tsx` | Static array in `store.ts` | `GET /api/v1/applications` via `applicationAdapter.getMyApplications` | Dynamic recruitment status tags (`UNDER_ADMIN_SCREENING`, `SHORTLISTED`, `INTERVIEW`, `OFFERED`, `HIRED`), match score indicator, and withdrawal action. |
| **Candidate Earnings & Escrow** | `src/routes/earnings.tsx` | Hardcoded payout slip numbers in `src/lib/data.ts` | `GET /api/v1/shifts/claims` via `shiftAdapter.getMyClaims` | Dynamic computation of Disbursed, In Escrow, and TDS withheld; downloadable PDF payout slip generation from live claim IDs. |
| **CME Learning Academy** | `src/routes/learning.index.tsx` | Static mock courses array | `GET /api/v1/courses` via `learningAdapter.getCourses` | Live enrolled course progress tracking, CME credits tally, and module completion triggers. |
| **Huddle Community Feed** | `src/routes/huddle.tsx` | Static mock posts & discussions in `store.ts` | `GET /api/v1/huddle/posts`, `POST /api/v1/huddle/posts`, `POST /api/v1/huddle/posts/{id}/react` | PHI scrubbing check, upvote counts, mentor AMA questions, and community ethics flagging. |
| **User Notification Hub** | `src/routes/notifications.tsx` | Mock in-memory notifications list | `GET /api/v1/notifications`, `PUT /api/v1/notifications/{id}/read` | Unread badge indicator, category filtering (Shift, Job, Compliance), and mark-all-as-read API synchronization. |
| **Employer Facility Dashboard** | `src/routes/employer.index.tsx` | Static mock metrics & hardcoded applicant list | `GET /api/v1/facility/jobs`, `GET /api/v1/facility/shifts`, `GET /api/v1/employer/applications`, `GET /api/v1/employer/wallet` | Live telemetry: Active Vacancies, Filled Shifts, Escrow Balance, and Shortlisted Applicants count. |
| **Employer Job Post Wizard** | `src/routes/employer.post.tsx` | In-memory store dispatch | `POST /api/v1/facility/jobs` via `facilityAdapter.postFacilityJob` | Form validation, automatic Rupee-to-paise conversion, and submission with initial status `PENDING_APPROVAL`. |
| **Employer Shift Dispatch Wizard** | `src/routes/employer.post-shift.tsx` | In-memory store dispatch | `POST /api/v1/facility/shifts` via `shiftAdapter.postShift` | Dynamic escrow calculator (`(rate + surge) * hours * slots`), balance check against facility wallet, and idempotent dispatch. |
| **Employer Shortlisted Pipeline** | `src/routes/employer.applicants.tsx` | Static applicant records from `src/lib/data.ts` | `GET /api/v1/employer/applications` via `facilityAdapter.getFacilityApplicants` | Masked contact indicators until hire/unlock; stage progression buttons (`SHORTLISTED` &rarr; `INTERVIEW` &rarr; `OFFERED` &rarr; `HIRED`); unmasked contacts on hire. |
| **Employer Talent Discovery (Resdex)** | `src/routes/employer.candidates.tsx` | Static doctor directory list | `GET /api/v1/employer/candidates`, `POST /api/v1/employer/candidates/{id}/unlock` | Masked contact cards, 1-credit unlock action via facility wallet, and council registration verification badge. |
| **Employer Nodal Wallet & Escrow** | `src/routes/employer.wallet.tsx` | Static mock balance figures | `GET /api/v1/employer/wallet`, `POST /api/v1/employer/wallet/topup`, `POST /api/v1/employer/wallet/buy-credits` | Available vs Escrow balance breakdown in integer paise, UTR transaction logger, and candidate contact package purchase. |
| **Employer Facility Profile** | `src/routes/employer.profile.tsx` | Static hospital metadata | `GET /api/v1/facility/profile`, `PUT /api/v1/facility/profile`, `GET /api/v1/facility/bank-account` | Hospital license numbers, NABH accreditation level, bed counts, and verified institutional settlement bank account. |
| **Admin Operations Dashboard** | `src/routes/admin.index.tsx` | In-memory pending item counters | `GET /api/v1/admin/dashboard` via `adminAdapter.getAdminDashboardMetrics` | Live counters for Pending Job Postings, Locum Shifts, Doctor KYC, Hospital Accreditation, and Candidate Applications. |
| **Admin Job Posting Approvals** | `src/routes/admin.postings.tsx` | In-memory approval state | `GET /api/v1/admin/postings/pending`, `POST /api/v1/admin/postings/{id}/approve`, `POST /api/v1/admin/postings/{id}/reject` | Review clinical requirements, salary range validation, 1-click Approve / Reject with mandatory rejection audit notes. |
| **Admin Application Screening** | `src/routes/admin.applications.tsx` | Mock shortlisted array in store | `GET /api/v1/admin/applications`, `PATCH /api/v1/admin/applications/{id}/screening` | Sort/filter by deterministic match score (0–100%), verify state medical council credentials, Shortlist or Reject with feedback. |
| **Admin Candidate KYC Review** | `src/routes/admin.candidates.tsx` | In-memory candidate KYC list | `GET /api/v1/admin/kyc/candidates`, `POST /api/v1/admin/kyc/candidates/{id}/review` | Verification of Medical Council Registration Number, Degree Certificate, and approval/rejection. |
| **Admin Hospital Accreditation** | `src/routes/admin.facilities.tsx` | In-memory facility KYC list | `GET /api/v1/admin/kyc/facilities`, `POST /api/v1/admin/kyc/facilities/{id}/review` | Verification of Clinical Establishment Act Registration, NABH certificate, and institutional approval. |

---

## 2. Key Architectural Invariants Enforced

1. **Role Integrity ("One Account = One Role")**:
   - Switched from client-side state mutation to strict authentication enforcement.
   - Any cross-role API call triggers HTTP 403 Forbidden on the backend and client-side redirection to the authorized portal.
2. **Deterministic Match Scoring**:
   - Implemented transparent 100-point algorithm completely excluding protected demographic attributes (age, gender, caste, religion, graduation year).
   - Candidate sees match score immediately upon submitting application.
   - Admin can prioritize applicants in the screening queue by score.
3. **Escrow Guarantee on Locum Shifts**:
   - Facility wallet atomically locks the entire potential shift obligation (`(base + surge) * hours * openings`) in integer paise at dispatch time.
   - If Admin rejects the shift, escrow balance is immediately refunded to the facility's available balance.
   - Upon duty completion sign-off, claim status moves to `COMPLETED` and escrow payout moves to `PENDING_DISBURSEMENT` awaiting Admin UTR banking settlement.
4. **Zero-Trust Contact Masking**:
   - Facility sees only masked phone (`+91 99••• ••22`) and masked email (`d•••••@thefale.com`) until the candidate is either officially HIRED through the pipeline or explicitly UNLOCKED via Resdex credit debit.
