# FALE Healthcare Platform — Screen-to-API Mapping Summary (v1.1)

> **Contract Alignment Matrix**  
> Covers all 33 frontend routes, shift lifecycle state transitions, money in integer paise,
> and complete Admin/Employer operational endpoints.

---

## 1. Authentication & Onboarding

| Route / Action | Persona | HTTP & Endpoint | Auth Guard | Notes & Parameters |
| :--- | :--- | :--- | :--- | :--- |
| `/verify` (Badge Check) | Public | `GET /api/v1/verifications/{id}` | Public | Masked verification badge for candidate/hospital |
| Sign In / Sign Up | Public | `POST /api/v1/auth/otp/send` | Public (Rate limit 3/15m) | Dispatches 6-digit OTP to mobile or email |
| OTP Verification | Public | `POST /api/v1/auth/otp/verify` | Public (Account lockout 5 fails) | Access token in body; refresh token in HttpOnly cookie |
| Admin Login | Admin | `POST /api/v1/auth/admin/login` | Public (Admin credentials) | Password check; initiates TOTP MFA session |
| Admin MFA Verify | Admin | `POST /api/v1/auth/admin/mfa/verify` | Public (MFA session) | Verifies TOTP code; issues admin session |
| Token Refresh | All | `POST /api/v1/auth/refresh` | Cookie-based (`fale_refresh_token`) | Rotates refresh token with reuse detection |
| Sign Out | All | `POST /api/v1/auth/logout` | Authenticated | Revokes refresh token in database & clears cookie |
| `/onboarding` (Candidate) | Candidate | `POST /api/v1/candidates/onboarding` | `ROLE_CANDIDATE` | Takes contact from principal; student vs graduate flow |
| `/onboarding` (Facility) | Facility | `POST /api/v1/employer/onboarding` | `ROLE_FACILITY` | Takes contact from principal; hospital verification |

---

## 2. Candidate & Student Experience

| Route / Action | Persona | HTTP & Endpoint | Auth Guard | Notes & Parameters |
| :--- | :--- | :--- | :--- | :--- |
| `/home` | Candidate | `GET /api/v1/candidates/me/profile` | `ROLE_CANDIDATE` | Profile completeness, readiness |
| `/home` (Internships) | Student | `GET /api/v1/jobs?internshipsOnly=true` | `ROLE_CANDIDATE` (`STUDENT`) | Auto-filtered internship feed |
| `/home`, `/profile` | Candidate | `GET /api/v1/candidates/me/export-cv` | `ROLE_CANDIDATE` | Standard NABH CV or Student Portfolio (PDF) |
| `/jobs` | All | `GET /api/v1/jobs` | Public / Candidate | Search & faceted filter; salary in integer paise |
| `/jobs/:id` | All | `GET /api/v1/jobs/{id}` | Public / Candidate | Job / internship specification |
| `/jobs/:id` (Apply) | Candidate | `POST /api/v1/jobs/{id}/apply` | `ROLE_CANDIDATE` | Blocked if student attempts non-internship role |
| `/jobs/:id` (Save) | Candidate | `POST /api/v1/jobs/{id}/save` | `ROLE_CANDIDATE` | Toggles bookmark |
| `/shifts` | Graduate | `GET /api/v1/shifts` | `ROLE_CANDIDATE` | Available locum shifts with startAt/endAt |
| `/shifts` (Claim) | Graduate | `POST /api/v1/shifts/{id}/claim` | `ROLE_CANDIDATE` (`!STUDENT`) | Status: `CLAIMED`. Pessimistic DB lock on slots |
| `/shifts` (Student Lock) | Student | `POST /api/v1/shifts/{id}/claim` | **HTTP 403 Forbidden** | Server-side guard: Students barred from locum shifts |
| Shift Check-in | Graduate | `POST /api/v1/shifts/claims/{id}/check-in` | `ROLE_CANDIDATE` | Status &rarr; `CHECKED_IN`, stamps `checked_in_at` |
| Shift Cancel | Graduate | `POST /api/v1/shifts/claims/{id}/cancel` | `ROLE_CANDIDATE` | Status &rarr; `CANCELLED`, slot returned to shift |
| Shift Dispute | Graduate | `POST /api/v1/shifts/claims/{id}/dispute` | `ROLE_CANDIDATE` | Status &rarr; `DISPUTED` for admin mediation |
| `/applications` | Candidate | `GET /api/v1/applications/me` | `ROLE_CANDIDATE` | Candidate's applications list |
| `/applications` (Withdraw)| Candidate | `POST /api/v1/applications/{id}/withdraw` | `ROLE_CANDIDATE` | Withdraws application |
| `/earnings` | Candidate | `GET /api/v1/payouts/me` | `ROLE_CANDIDATE` | Disbursed & escrow ledger in integer paise |
| `/earnings` (Method) | Candidate | `PUT /api/v1/payouts/me/method` | `ROLE_CANDIDATE` | Sets UPI or Bank account for IMPS payouts |
| `/earnings` (Slip PDF) | Candidate | `GET /api/v1/payouts/{id}/slip` | `ROLE_CANDIDATE` | Streams official PDF payout slip |
| `/profile` | Candidate | `GET /api/v1/candidates/me/profile` | `ROLE_CANDIDATE` | Full profile details |
| `/profile` (Update) | Candidate | `PUT /api/v1/candidates/me/profile` | `ROLE_CANDIDATE` | Updates bio, specialty; cannot edit candidateLevel |
| `/profile` (Doc Upload)| Candidate | `POST /api/v1/candidates/me/documents` | `ROLE_CANDIDATE` | Uploads KYC or student ID |
| `/profile` (Doc Delete)| Candidate | `DELETE /api/v1/candidates/me/documents/{id}` | `ROLE_CANDIDATE` | Deletes uploaded document |
| Student Upgrade Request | Candidate | `POST /api/v1/candidates/upgrade-request` | `ROLE_CANDIDATE` (`STUDENT`) | Requests upgrade to graduate; resets KYC to PENDING |

---

## 3. CME Learning Academy & Huddle Community

| Route / Action | Persona | HTTP & Endpoint | Auth Guard | Notes & Parameters |
| :--- | :--- | :--- | :--- | :--- |
| `/learning` | Candidate | `GET /api/v1/courses` | `ROLE_CANDIDATE` | Course modules (student foundational vs CME) |
| `/learning/:id` | Candidate | `GET /api/v1/courses/{id}` | `ROLE_CANDIDATE` | Course syllabus and lesson outline |
| `/learning/:id` (Enroll)| Candidate | `POST /api/v1/courses/{id}/enroll` | `ROLE_CANDIDATE` | Enrolls user |
| `/learning/:id` (Prog) | Candidate | `PUT /api/v1/courses/{id}/progress` | `ROLE_CANDIDATE` | Updates lesson progress percentage (0–100%) |
| `/learning/:id` (Cert) | Candidate | `POST /api/v1/courses/{id}/certificate` | `ROLE_CANDIDATE` | Issues signed CME certificate upon 100% completion |
| `/huddle` (Feed) | All | `GET /api/v1/huddle/posts` | Authenticated | Clinical discussion feed |
| `/huddle` (Create Post)| All | `POST /api/v1/huddle/posts` | Authenticated (PHI checked) | Creates case/thought/update/event |
| `/huddle` (Comments) | All | `GET /api/v1/huddle/posts/{id}/comments` | Authenticated | Post comments |
| `/huddle` (Add Comment)| All | `POST /api/v1/huddle/posts/{id}/comments` | Authenticated | Adds clinical comment |
| `/huddle` (Questions) | All | `GET /api/v1/huddle/questions` | Authenticated | Mentor AMA questions |
| `/huddle` (Ask Question)| All | `POST /api/v1/huddle/questions` | Authenticated | Asks mentor question |
| `/huddle` (Answer) | Graduate | `POST /api/v1/huddle/questions/{id}/answers` | `ROLE_CANDIDATE` (`!STUDENT`) | Consultant answers (students blocked) |
| `/huddle` (React) | All | `POST /api/v1/huddle/posts/{id}/react` | Authenticated | Upvotes post |
| `/huddle` (Flag) | All | `POST /api/v1/huddle/posts/{id}/flag` | Authenticated | Flags post for PHI or ethics breach |
| `/notifications` | All | `GET /api/v1/notifications` | Authenticated | User notification feed |
| `/notifications/:id/read`| All | `PUT /api/v1/notifications/{id}/read` | Authenticated | Marks single notification read |
| `/notifications/read-all`| All | `PUT /api/v1/notifications/mark-all-read` | Authenticated | Marks all read |
| `/support` (Ticket) | All | `POST /api/v1/support/tickets` | Authenticated | Submits help desk ticket |
| `/support` (My Tickets)| All | `GET /api/v1/support/tickets/me` | Authenticated | Lists user's tickets |

---

## 4. Employer / Facility Portal

| Route / Action | Persona | HTTP & Endpoint | Auth Guard | Notes & Parameters |
| :--- | :--- | :--- | :--- | :--- |
| `/employer` | Facility | `GET /api/v1/employer/dashboard` | `ROLE_FACILITY` | Openings, coverage, balances in integer paise |
| `/employer/profile` | Facility | `GET /api/v1/employer/profile` | `ROLE_FACILITY` | Hospital profile, NABH status, beds |
| `/employer/profile` | Facility | `PUT /api/v1/employer/profile` | `ROLE_FACILITY` | Updates beds, contact info |
| `/employer/roster` | Facility | `GET /api/v1/employer/roster` | `ROLE_FACILITY` | Live shift coverage & roster status |
| `/employer/post` | Facility | `POST /api/v1/employer/jobs` | `ROLE_FACILITY` | Creates job posting (pending admin approval) |
| `/employer/post-shift` | Facility | `POST /api/v1/employer/shifts` | `ROLE_FACILITY` (`Idempotency-Key`) | Locks required escrow atomically in paise |
| Shift Cancellation | Facility | `POST /api/v1/employer/shifts/{id}/cancel` | `ROLE_FACILITY` | Cancels shift; refunds escrow to available balance |
| Shift Completion | Facility | `POST /api/v1/employer/shifts/claims/{id}/confirm-completion` | `ROLE_FACILITY` | Status &rarr; `COMPLETED`; releases escrow to payout |
| Shift No-Show | Facility | `POST /api/v1/employer/shifts/claims/{id}/no-show` | `ROLE_FACILITY` | Status &rarr; `NO_SHOW`; refunds escrow to facility |
| Shift Dispute | Facility | `POST /api/v1/employer/shifts/claims/{id}/dispute` | `ROLE_FACILITY` | Status &rarr; `DISPUTED` for admin mediation |
| `/employer/applicants` | Facility | `GET /api/v1/employer/applications` | `ROLE_FACILITY` | Pipeline of candidates applied to postings |
| Stage Transition | Facility | `PUT /api/v1/employer/applications/{id}/status` | `ROLE_FACILITY` | Shortlisted, Interview, Hired, Rejected |
| Interview Schedule | Facility | `POST /api/v1/employer/applications/{id}/interview` | `ROLE_FACILITY` | Schedules interview date and link |
| `/employer/candidates` | Facility | `GET /api/v1/employer/candidates` | `ROLE_FACILITY` | Resdex search; contact details masked |
| Unlock Candidate | Facility | `POST /api/v1/employer/candidates/{id}/unlock` | `ROLE_FACILITY` (`Idempotency-Key`) | Debits 1 credit; unmasks phone/email |
| `/employer/wallet` | Facility | `GET /api/v1/employer/wallet` | `ROLE_FACILITY` | Nodal balances & credits in integer paise |
| Wallet Transactions | Facility | `GET /api/v1/employer/wallet/transactions` | `ROLE_FACILITY` | Paginated financial transaction ledger |
| Wallet Top-Up | Facility | `POST /api/v1/employer/wallet/topup` | `ROLE_FACILITY` (`Idempotency-Key`) | Enters `PENDING_RECONCILIATION` with UTR |
| Credit Purchase | Facility | `POST /api/v1/employer/credits/purchase` | `ROLE_FACILITY` (`Idempotency-Key`) | Bought from available wallet balance |
| Bank Account Config | Facility | `PUT /api/v1/employer/bank-account` | `ROLE_FACILITY` | Configures hospital refund account |

---

## 5. Admin Compliance Portal

| Route / Action | Persona | HTTP & Endpoint | Auth Guard | Notes & Parameters |
| :--- | :--- | :--- | :--- | :--- |
| `/admin` | Admin | `GET /api/v1/admin/dashboard` | `ROLE_ADMIN` only | Platform KPIs & pending queue counters |
| Audit Trail | Admin | `GET /api/v1/admin/audit-logs` | `ROLE_ADMIN` only | Paginated immutable audit events |
| Platform Settings | Admin | `GET /api/v1/admin/settings` | `ROLE_ADMIN` only | Dynamic settings (e.g. platform fee %) |
| Platform Settings | Admin | `PUT /api/v1/admin/settings` | `ROLE_ADMIN` only | Updates platform setting |
| `/admin/candidates` | Admin | `GET /api/v1/admin/kyc/candidates` | `ROLE_ADMIN` only | Pending candidate & student KYC queue |
| Approve Candidate KYC | Admin | `PUT /api/v1/admin/kyc/candidates/{id}/verify` | `ROLE_ADMIN` only | Approves KYC (`status = VERIFIED`) |
| Reject Candidate KYC | Admin | `PUT /api/v1/admin/kyc/candidates/{id}/reject` | `ROLE_ADMIN` only | Rejects KYC with audit reason |
| Upgrade Requests Queue | Admin | `GET /api/v1/admin/candidates/upgrade-requests` | `ROLE_ADMIN` only | Student-to-graduate upgrade queue |
| Approve Upgrade | Admin | `PUT /api/v1/admin/candidates/upgrade-requests/{id}/approve` | `ROLE_ADMIN` only | Upgrades level to FRESHER; resets KYC to PENDING |
| Reject Upgrade | Admin | `PUT /api/v1/admin/candidates/upgrade-requests/{id}/reject` | `ROLE_ADMIN` only | Rejects upgrade with reason |
| `/admin/facilities` | Admin | `GET /api/v1/admin/kyc/facilities` | `ROLE_ADMIN` only | Pending hospital accreditation queue |
| Approve Facility | Admin | `PUT /api/v1/admin/kyc/facilities/{id}/approve` | `ROLE_ADMIN` only | Approves hospital license & NABH status |
| Reject Facility | Admin | `PUT /api/v1/admin/kyc/facilities/{id}/reject` | `ROLE_ADMIN` only | Rejects hospital application with reason |
| `/admin/postings` | Admin | `GET /api/v1/admin/postings/pending` | `ROLE_ADMIN` only | Moderation queue for job vacancies |
| Approve Job Posting | Admin | `POST /api/v1/admin/postings/{id}/approve` | `ROLE_ADMIN` only | Approves job requirement; publishes to candidates (`status = OPEN`) |
| Reject Job Posting | Admin | `POST /api/v1/admin/postings/{id}/reject` | `ROLE_ADMIN` only | Rejects job requirement with mandatory audit notes |
| Pending Shifts Queue | Admin | `GET /api/v1/admin/shifts/pending` | `ROLE_ADMIN` only | Moderation queue for locum shifts with escrow holds |
| Approve Locum Shift | Admin | `POST /api/v1/admin/shifts/{id}/approve` | `ROLE_ADMIN` only | Approves shift; publishes to candidate feed (`status = OPEN`) |
| Reject Locum Shift | Admin | `POST /api/v1/admin/shifts/{id}/reject` | `ROLE_ADMIN` only | Rejects shift; automatically refunds locked escrow to hospital available balance |
| `/admin/applications` | Admin | `GET /api/v1/admin/applications` | `ROLE_ADMIN` only | Applications queue sorted/filtered by deterministic match score (0-100) |
| Screen Job Application | Admin | `PATCH /api/v1/admin/applications/{id}/screening` | `ROLE_ADMIN` only | SHORTLIST (forwards to facility pipeline) or REJECT with feedback |
| Shift Claims Queue | Admin | `GET /api/v1/admin/shifts/claims/pending` | `ROLE_ADMIN` only | Pending locum duty claims awaiting credential screening |
| Screen Shift Claim | Admin | `PATCH /api/v1/admin/shifts/claims/{id}/screening` | `ROLE_ADMIN` only | APPROVE (confirms duty slot) or REJECT (returns slot to shift) |
| Top-Up Reconciliation | Admin | `GET /api/v1/admin/wallet/topups` | `ROLE_ADMIN` only | Pending UTR top-up requests |
| Reconcile Top-Up | Admin | `PUT /api/v1/admin/wallet/topups/{id}/reconcile` | `ROLE_ADMIN` only | Credits hospital wallet; marks transaction COMPLETED |
| Pending Payouts Queue | Admin | `GET /api/v1/admin/payouts/pending` | `ROLE_ADMIN` only | Completed shifts awaiting bank transfer |
| Mark Payout Disbursed | Admin | `POST /api/v1/admin/payouts/{id}/disburse` | `ROLE_ADMIN` only | Records banking UTR and marks payout status DISBURSED |
| `/admin/huddle` | Admin | `GET /api/v1/admin/huddle/flagged` | `ROLE_ADMIN` only | Flagged clinical posts queue |
| Delete Huddle Post | Admin | `DELETE /api/v1/admin/huddle/posts/{id}` | `ROLE_ADMIN` only | Soft-deletes violating post with audit note |
| Moderate Huddle User | Admin | `PUT /api/v1/admin/huddle/users/{id}/moderation-status` | `ROLE_ADMIN` only | Mutes or suspends user from Huddle |
