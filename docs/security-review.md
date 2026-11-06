# Security Audit & OWASP Alignment Review

**Platform**: FALE Healthcare Backend  
**Framework**: Spring Boot 3.3.5 / Java 21 / PostgreSQL 16  
**Date**: November 2026  
**Status**: PASSED (Phase 5 Verification)

---

## 1. Executive Summary

The FALE Healthcare backend was engineered using a **Zero-Trust, Secure-by-Default** architectural philosophy. All public endpoints are explicitly whitelisted; all other endpoints require authenticated JWT Bearer authorization. Critical healthcare compliance requirements (such as student candidate guardrails, locum shift escrow locks, patient health information [PHI] filtering, and dual-control financial operations) are enforced at the service and data layer.

---

## 2. OWASP Top 10 (2021) Compliance Matrix

| Vulnerability | Status | Architecture & Implementation Defense |
|---|---|---|
| **A01: Broken Access Control** | **COMPLIANT** | • Default Deny Spring Security architecture (`anyRequest().authenticated()`).<br>• Method security with `@PreAuthorize` role enforcement (`ROLE_CANDIDATE`, `ROLE_FACILITY`, `ROLE_ADMIN`).<br>• Student guard (`candidateLevel == STUDENT`) strictly forbids locum shift claims (HTTP 403 Forbidden).<br>• Ownership checks ensure facilities can only modify their own postings and candidates can only mutate their own claims. |
| **A02: Cryptographic Failures** | **COMPLIANT** | • BCrypt hashing with strong work factor for user passwords and OTP codes.<br>• HMAC-SHA256 with 256-bit+ secret key for stateless JWT Access Tokens.<br>• Refresh tokens issued in `HttpOnly`, `Secure`, `SameSite=Strict` cookies.<br>• No plaintext credentials, secrets, or OTPs stored in database or exposed in headers. |
| **A03: Injection** | **COMPLIANT** | • Spring Data JPA with Hibernate parameterized queries prevents SQL injection.<br>• Zero dynamic SQL concatenation.<br>• Strict Jakarta Validation (`@Pattern`, `@Size`, `@NotBlank`) on all input DTOs (e.g., regex checks for phone, Aadhaar, IFSC, PAN, GSTIN). |
| **A04: Insecure Design** | **COMPLIANT** | • Escrow architecture: 100% of shift payout + surge held in escrow at posting time, preventing insolvent shifts.<br>• Dual-control payouts: Admin approval and Ops UTR verification required before candidate disbursement.<br>• Bucket4j rate limiting applied to prevent brute-forcing OTP and auth endpoints. |
| **A05: Security Misconfiguration** | **COMPLIANT** | • **Dev OTP Fail-Safe**: System refuses to start in the `prod` profile if dev OTP is enabled (`IllegalStateException` thrown during startup).<br>• Dev admin/student/doctor seeders restricted exclusively to `@Profile("dev")`.<br>• Production admin bootstrapped strictly via environment variables with mandatory MFA.<br>• Standard security headers enforced: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security`, `Referrer-Policy: strict-origin-when-cross-origin`. |
| **A06: Vulnerable & Outdated Components** | **COMPLIANT** | • Built on current long-term releases: Java 21, Spring Boot 3.3.5, JJWT 0.12.6, Bucket4j 8.10.1, Apache Tika 2.9.2.<br>• OWASP Dependency-Check Maven plugin configured in `pom.xml` (`failBuildOnCVSS: 8`). |
| **A07: Identification & Authentication Failures** | **COMPLIANT** | • 6-digit numeric OTP generated via `SecureRandom` with 5-minute cryptographic expiration.<br>• Max 3 OTP verification attempts before automatic invalidation.<br>• Refresh token rotation: each refresh invalidates previous token and detects replay attacks.<br>• Explicit logout endpoint revokes active refresh tokens. |
| **A08: Software & Data Integrity Failures** | **COMPLIANT** | • Distributed Idempotency Filter with cache prevents duplicate transaction processing on all mutating/financial endpoints.<br>• Apache Tika magic-byte detection on file uploads prevents content-type spoofing and malicious executable uploads. |
| **A09: Logging & Monitoring Failures** | **COMPLIANT** | • Dedicated `audit_events` ledger recording `actor_id`, `action`, `resource_type`, `resource_id`, and `created_at`.<br>• Auditing executed in independent transactions (`REQUIRES_NEW`) to guarantee persistence even if outer operations fail.<br>• Sensitive PII/PHI and auth tokens excluded from log outputs. |
| **A10: Server-Side Request Forgery (SSRF)** | **COMPLIANT** | • Backend does not issue outbound HTTP requests to user-supplied URLs.<br>• DigiLocker and verification webhooks restricted to internal domain contracts. |

---

## 3. OWASP API Security Top 10 (2023) Compliance Matrix

| API Risk | Status | Defense Mechanism |
|---|---|---|
| **API1: Broken Object Level Authorization (BOLA)** | **COMPLIANT** | All resource lookups verify relationship against `CustomUserPrincipal.getId()`. Facilities cannot view or modify competitor shift applicants or wallet ledgers. |
| **API2: Broken Authentication** | **COMPLIANT** | Short-lived JWT access tokens (15 minutes). Long-lived refresh tokens stored exclusively in browser memory or secure HttpOnly cookies. |
| **API3: Broken Object Property Level Authorization (BOPLA)** | **COMPLIANT** | Strict Request/Response DTOs prevent mass assignment. Candidate phone numbers and emails are masked in Resdex until unlocked with facility credits. |
| **API4: Unrestricted Resource Consumption** | **COMPLIANT** | Bucket4j token bucket rate limiting applied per IP: 100 req/min for general API, 5 req/min for OTP send, 10 req/min for OTP verify. File uploads capped at 10 MB. |
| **API5: Broken Function Level Authorization (BFLA)** | **COMPLIANT** | Strict URL path and method authorization: `/api/v1/admin/**` requires `ROLE_ADMIN`; `/api/v1/employer/**` requires `ROLE_FACILITY`. |
| **API6: Unrestricted Access to Sensitive Business Flows** | **COMPLIANT** | Mandatory `Idempotency-Key` headers on financial mutations. Wallet balances checked with row-level locks before deductions. |
| **API7: Server Side Request Forgery (SSRF)** | **COMPLIANT** | No open URL redirect or fetching endpoints exist in the API contract. |
| **API8: Security Misconfiguration** | **COMPLIANT** | CORS restricted to configured frontend origins; detailed stack traces masked from clients in `GlobalExceptionHandler`. |
| **API9: Improper Inventory Management** | **COMPLIANT** | Complete, versioned OpenAPI 3.0 specification generated and synchronized with frontend contracts under `/api/v1/`. |
| **API10: Unsafe Consumption of APIs** | **COMPLIANT** | External dependencies (KYC, DigiLocker, SMS gateway) abstracted behind strict interfaces with sanitized payload boundaries. |

---

## 4. Healthcare Data Privacy & PHI Protection

Under the Indian Digital Personal Data Protection Act (DPDPA) and healthcare data ethics standards:
- **`PhiProtectionService`**: Automatically sanitizes or rejects Community Huddle clinical case discussions, questions, and answers containing:
  - Indian 10-digit mobile phone numbers
  - 12-digit Aadhaar numbers
  - Email addresses
  - Medical Record Numbers (MRN/UHID patterns)
  - Patient identifying phrases ("Patient [Name]", "Pt: [Name]")
- **Resdex Privacy Shield**: Candidate contact information remains masked until a verified healthcare facility spends platform credits to unlock.
