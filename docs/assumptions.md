# FALE Healthcare Platform — Architectural Decisions & Assumptions (v1.1)

> **Checkpoint Status**: Phases 1–3 Complete & Aligned with Technical Decisions  
> **Target Stack**: Java 21, Spring Boot 3.x, PostgreSQL 16+, Flyway, Bucket4j, Spring Security

---

## 1. Confirmed Business & Technical Decisions

1. **Persona & Role Architecture**:
   - Single Spring Security role for candidates: `ROLE_CANDIDATE`.
   - Distinct database column `candidate_level` (`STUDENT`, `FRESHER`, `PROFESSIONAL`).
   - Facilities hold `ROLE_FACILITY`; Compliance Operations hold `ROLE_ADMIN`.
   - **One account = one role**. A single account cannot simultaneously be a Candidate and a Facility.
2. **Student Persona Lockdown**:
   - Students are strictly barred from clinical patient locum shifts (`POST /api/v1/shifts/{id}/claim` throws HTTP 403 Forbidden).
   - Job browsing automatically forces `is_internship = true` for students; applying to non-internship clinical roles throws HTTP 403 Forbidden.
   - `candidate_level` is immutable by the candidate. Upgrading to Graduate requires submitting proof of graduation & council license via `POST /api/v1/candidates/upgrade-request`, which resets `kyc_status` to `PENDING` and awaits Admin approval.
3. **Authentication, Credentials & Admin Security**:
   - Candidate and Facility users use **passwordless OTP** (mobile / email). No `password_hash` is created or required for them.
   - Admin accounts use strong passwords (BCrypt strength 12) + mandatory TOTP MFA. Admin accounts are seeded or invited by existing admins; self-registration is disabled.
   - Onboarding APIs take phone and email directly from the authenticated security principal (`principal.phone` / `principal.email`), never from the request payload.
4. **Token Handling & CSRF Strategy**:
   - **Access Token**: Short-lived (15 minutes), passed in the response body, and sent in subsequent API requests via the `Authorization: Bearer <token>` header.
   - **Refresh Token**: Long-lived (7 days), stored hashed (`SHA-256`) in the database, and transmitted exclusively via an `HttpOnly`, `Secure`, `SameSite=Strict` cookie (`fale_refresh_token`).
   - **CSRF Defense**: Because state-changing REST endpoints authenticate via the `Authorization` header (which browsers do not attach automatically to cross-site requests), the API is inherently immune to CSRF attacks. The token refresh endpoint reads the cookie but is protected by `SameSite=Strict` and explicit `Origin` / `Referer` validation against the configured frontend domain.
5. **Money & Precision Convention**:
   - **Database**: `NUMERIC(12, 2)` for nodal balances, transactions, and shift fees.
   - **REST API**: Exclusively integer **paise** (1 INR = 100 paise) across all request and response schemas (e.g. `45000` paise = ₹450.00). Eliminates floating-point rounding errors.
6. **Shift Lifecycle & Financial Escrow**:
   - `locum_shifts`: `PENDING_APPROVAL`, `OPEN`, `FILLED`, `CANCELLED`, `REJECTED`, `COMPLETED`.
   - Shifts use `start_at` and `end_at` ISO-8601 timestamps (fully supporting overnight duties); `hours` is derived on the server.
   - Hourly rates must meet or exceed the statutory floor in `minimum_role_rates`.
   - Posting a shift locks `openings * hours * (rate + surge)` from available wallet balance into `locked_escrow_balance` inside a serializable transaction.
   - If a shift is cancelled or rejected by admin, the locked escrow is automatically refunded to available balance via a `REFUND` ledger entry.
   - `shift_claims` Lifecycle:
     `CLAIMED` &rarr; `CHECKED_IN` &rarr; `COMPLETED` (or `CANCELLED`, `NO_SHOW`, `DISPUTED`).
   - Upon facility confirmation (`confirm-completion`), escrow is released and a candidate payout record is generated with a full fee breakdown (`gross_amount`, `platform_fee`, `tds_amount`, `net_amount`).
7. **Candidate Payouts (24h SLA)**:
   - Candidate payouts are executed via a pluggable `PayoutProvider` interface. In v1, bank disbursements are confirmed by ops/admin entering the IMPS UTR number (`PUT /api/v1/admin/payouts/{id}/mark-paid`).
8. **File Storage**:
   - Local disk storage under `uploads/` for development profile (`dev`).
   - Private S3-compatible object storage (AWS S3, MinIO, or Cloudflare R2) with presigned URLs for production profile (`prod`).
9. **DigiLocker / Aadhaar KYC**:
   - Stubbed in v1: stores only verification status (`VERIFIED`, `PENDING`, `REJECTED`) and third-party reference token.
10. **Idempotency**:
    - Idempotency-Key header enforced for:
      - `POST /api/v1/employer/shifts`
      - `POST /api/v1/employer/wallet/topup`
      - `POST /api/v1/employer/credits/purchase`
      - `POST /api/v1/employer/candidates/{id}/unlock`

---

## 2. Frontend Changes Required for the Portal Switcher

Currently, in `src/components/fale/AppShell.tsx`:
```typescript
// Current frontend behavior: client-side in-memory toggle
const switchPortal = (p: Portal) => {
  setState({ portal: p });
  // Navigates directly between /home, /employer, and /admin
};
```

### What Needs to Change in the Frontend:
Because the backend enforces **One Account = One Role**:
1. A candidate account (`ROLE_CANDIDATE`) will receive **HTTP 403 Forbidden** on `/api/v1/employer/**` or `/api/v1/admin/**`.
2. A facility account (`ROLE_FACILITY`) will receive **HTTP 403 Forbidden** on `/api/v1/candidates/**` or `/api/v1/admin/**`.
3. **Frontend Implementation Adjustment**:
   - When the user selects a different portal in the Account Switcher drawer, the frontend should prompt:
     *"Switching to [Hospital Portal / Candidate Portal] requires signing in with an authorized organization or candidate account."*
   - Clear session tokens (`POST /api/v1/auth/logout`) and redirect to `/` with the appropriate portal mode selected (`?portal=facility` or `?portal=candidate`), allowing the user to authenticate into the desired persona.
   - Alternatively, support multi-account sessions by storing separate tokens per portal in `localStorage` / session storage.

---

## 3. Open Questions for the User

1. **Development OTP Delivery**:
   - In the dev profile, should the 6-digit OTP be printed to the application console logs, or returned in a custom response header (`X-Dev-OTP: 123456`), or both, for instantaneous manual browser testing?
2. **Platform Fee Default Percentage**:
   - We have seeded `PLATFORM_FEE_PERCENT` at `5.0%` and statutory `TDS_PERCENT` at `1.0%` (u/s 194J of the Indian Income Tax Act). Would you like different default values?
3. **Seed Credentials for Dev Admin**:
   - We have initialized the dev compliance admin as `admin@thefale.com` with a BCrypt hash for development. In production, this will be injected via `ADMIN_INITIAL_PASSWORD` environment variable. Does this meet your security deployment standards?
