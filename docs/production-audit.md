# FALE Healthcare Platform — Full Production-Readiness Audit

**Scope**:
- Backend: `F:\theFaleOne-Backend` (Spring Boot 3.3.5 / Java 21 / PostgreSQL + Flyway)
- Frontend: `F:\theFaleOne-Frontend\FALE-healthcare-ui` (React 19 / Vite 8 / TanStack Router / Vitest)
- Mode: **Strict Read-Only Audit** — Zero application or config source files were modified.

---

## Executive Verification Runs (Observed Tool Output)

| Check | Command Run | Observed Result | Label |
| :--- | :--- | :--- | :--- |
| **Backend Test Suite** | `.\mvnw.cmd test` | **BUILD FAILURE** — `Tests run: 19, Failures: 0, Errors: 9, Skipped: 0`. `AdminAndHuddleIntegrationTest` failed with `SchemaManagementException: Schema-validation: missing table [candidate_profile_views]`. Subsequent integration tests aborted on `ApplicationContext failure threshold (1) exceeded`. | `verified by running it` |
| **Frontend Test Suite** | `npm test -- --run` | **FAILED** — `Test Files 1 failed \| 1 passed (2)`, `Tests 1 passed (1)`. `address-field.test.tsx` passed (1). `app-routing.test.tsx` crashed at import: `TypeError: Cannot set properties of null (setting 'fillStyle')` at `lottie_light.js:1119` (jsdom lacks `HTMLCanvasElement.prototype.getContext`). | `verified by running it` |
| **Frontend Production Build** | `npm run build` | **Built in 1m 12s** with chunk size warning (`dist/assets/onboarding-Ben2wsYM.js` = `907.02 kB`, gzip `259.74 kB`, >500 kB limit). | `verified by running it` |
| **Frontend Dependency Audit** | `npm audit --omit=dev` | **FAILED (Exit 1)** — 2 High vulnerabilities: `vite 8.0.0 - 8.1.5` (5 advisories incl. arbitrary command execution & `server.fs.allow` bypass) and `zod <4.6.6` (`GHSA-mrhx-c7xc-m3w5` DoS). | `verified by running it` |
| **Backend Dependency Audit** | `.\mvnw.cmd org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=11` | **FAILED (`SocketTimeoutException`)** — Could not download RetireJS/NVD feeds from `raw.githubusercontent.com`. | `not verified` (network blocked) |
| **Live App End-to-End Runtime** | Live running Spring Boot + React UI | Not booted together as full stack against live DB during this read-only audit; observed user-submitted runtime error logs for `GeminiAiClient` (404 on `gemini-2.5-flash`/`2.0-flash`/`1.5-flash`, 503 on `gemini-3.1-flash-lite`) and fallback output. | `not verified` (full stack) / `from reading the code only` |

---

# AUDIT 1: STATIC FALLBACKS AND DUMMY DATA (BACKEND)

## 1.1 Catch Blocks / Error Paths Returning Defaults, Empty Lists, Fake Success, or Hardcoded Responses

| ID | File Path & Function | Snippet | Severity | Production Impact | Exact Fix | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **B-1.1** | `src/main/java/com/thefale/service/ai/AiService.java:79-85` (`executeTask`) | `} catch (Exception e) { log.warn("Live AI provider ({}) call failed...", ...); }` -> `return fallbackEngine.executeFallback(request);` | **Blocker** | Every live Gemini/Claude failure (503, 404, 429 quota, timeout, bad API key) is swallowed and returns HTTP `200 OK` with `"success": true` and fabricated metrics (e.g., `"4 candidate KYC"`, `"2 hospital facilities"`, `"3 ICU shifts"` in `handleDailyBrief`). | Propagate HTTP `503 Service Unavailable` / error envelope on LLM failure unless caller explicitly opts into deterministic local fallback, and never emit hardcoded operational numbers as live status. | `from reading the code only` (corroborated by user runtime log) |
| **B-1.2** | `src/main/java/com/thefale/service/AdminService.java:1108-1110` (`mapToFacilityDto`) & `FacilityService.java:320-322` (`mapToDto`) | `} catch (Exception e) { log.warn("Failed to load facility docs...", e.getMessage()); }` | **High** | Any DB/query failure when reading facility documents silently returns `Collections.emptyList()`. Compliance reviewers see zero documents and may falsely reject or wrongly clear hospitals. | Remove the broad `try/catch`; let `DataAccessException` propagate to `GlobalExceptionHandler` as 500. | `from reading the code only` |
| **B-1.3** | `src/main/java/com/thefale/service/ShiftClaimService.java:412-422` (`getSettingAsDecimal`) | `try { return new BigDecimal(setting.getValue()); } catch (Exception e) { return defaultValue; }` | **High** | Corrupt or unparseable `PLATFORM_FEE_PERCENT` / `TDS_PERCENT` rows in `platform_settings` silently revert to hardcoded `5.0` / `1.0` without alert, causing silent tax/fee miscalculations. | Fail fast by throwing `IllegalStateException("Invalid numeric setting for key: " + key)` if DB value is corrupt. | `from reading the code only` |
| **B-1.4** | `src/main/java/com/thefale/service/CandidateService.java:87-91` & `130-133` (`onboardCandidate`) | `try { profile.setDob(LocalDate.parse(...)); } catch (Exception e) { log.warn(...); }` and `catch (NumberFormatException ignored) {}` | **Medium** | Malformed DOBs or graduation years are silently dropped and stored as `null` without notifying the applicant, breaking KYC validation downstream. | Catch `DateTimeParseException` / `NumberFormatException` and throw `BadRequestException` with explicit format requirements. | `from reading the code only` |
| **B-1.5** | `src/main/java/com/thefale/service/CandidateService.java:517, 527` & `FacilityService.java:453, 465` (`jsonToList` / `listToJson`) | `catch (Exception e) { log.warn("Failed to deserialize JSON to list", e); return Collections.emptyList(); }` | **Medium** | Corrupt JSON columns (`skills`, `languages`, `specialties`, `departments`) return empty lists instead of failing, erasing clinician skills silently. | Use a strict JPA `AttributeConverter` and throw `IllegalStateException` on corrupt persisted JSON. | `from reading the code only` |
| **B-1.6** | `src/main/java/com/thefale/service/AuditLogService.java:43-45` (`logEvent`) | `} catch (Exception e) { log.error("Failed to persist audit event...", ...); }` | **Medium** | Failed compliance audit inserts (Payouts, KYC, Upgrades) are swallowed while the financial/KYC mutation commits, leaving gaps in statutory NABH audit trails. | Re-throw on critical compliance mutations or route failures to an alertable dead-letter mechanism. | `from reading the code only` |
| **B-1.7** | `src/main/java/com/thefale/security/IdempotencyFilter.java:97-101` (`doFilterInternal`) | `try { idempotencyService.saveResponse(...); } catch (Exception e) { log.warn("Could not save idempotency record..."); }` | **Medium** | Failure to save idempotency response still returns HTTP 200 to the caller; a subsequent client retry with the same `Idempotency-Key` will double-charge or double-create. | Reserve the key with an atomic pre-lock before executing the chain; fail/alert if persistence fails. | `from reading the code only` |
| **B-1.8** | `src/main/java/com/thefale/security/JwtAuthenticationFilter.java:38-40` | `} catch (Exception ex) { log.warn("Could not set user authentication..."); }` | **Low** | Swallows unexpected principal construction exceptions and continues request as anonymous user, yielding misleading 401/403 downstream. | Separate expected `JwtException` from runtime errors; log unexpected exceptions at `ERROR`. | `from reading the code only` |

## 1.2 Methods Returning Hardcoded Lists, Maps, Numbers, Strings, or Fake IDs

| ID | File Path & Line | Snippet | Severity | Production Impact | Exact Fix | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **B-1.9** | `src/main/java/com/thefale/service/FacilityResdexService.java:119-121, 134-143` (`mapToResdexDto`) | `String rawPhone = ... : "+91 98401 23456";` / `rawEmail = ... : "candidate@fale.care";` / `.city(... : "Chennai")` / `.matchScore(94)` / `.experienceYears(... : "1 year")` | **Blocker** | Incomplete profiles are returned to paying hospitals with fabricated Indian mobile numbers (`+91 98401 23456`), fake emails (`candidate@fale.care`), hardcoded city `"Chennai"`, `"1 year"`, and hardcoded `94%` match score. | Return `null` for missing optional fields; compute match score dynamically; reject incomplete profiles from Resdex search. | `from reading the code only` |
| **B-1.10** | `src/main/java/com/thefale/service/FacilityJobManagementService.java:391-394, 467-468, 530` | `phone = maskPhone(user != null ? user.getPhone() : "+919876543210");` / `email = ... : "candidate@fale.care";` / `.matchScore(96)` | **Blocker** | Shift claims in hospital applicant view are hardcoded to `matchScore(96)` and invent dummy phone/email if missing. | Compute match score via `ShiftClaimService.calculateShiftMatchScore`; return `null` when contact is missing. | `from reading the code only` |
| **B-1.11** | `src/main/java/com/thefale/service/AdminService.java:927-943` | `.matchScore(85)` / `"Pan-India"` / `"Partner Facility"` | **High** | Admin shift-claim screening table shows a static `85%` match score for every claim regardless of specialty, council KYC, or location. | Invoke `ShiftClaimService.calculateShiftMatchScore(candidate, shift)`. | `from reading the code only` |
| **B-1.12** | `src/main/java/com/thefale/controller/PayoutController.java:66` & `CandidateService.java:469` | `String slipUrl = "https://api.thefale.com/api/v1/payouts/" + payoutId + "/payout-slip.pdf";` | **Blocker** | Returns fabricated static PDF URLs on `api.thefale.com` that do not exist (404 when clicked) and performs zero ownership check on `payoutId` (IDOR). | Verify claim ownership against `principal`, generate real PDF bytes (OpenPDF/iText) or S3 signed URL, and stream `application/pdf`. | `from reading the code only` |
| **B-1.13** | `src/main/java/com/thefale/service/ai/FaleClinicalAiEngine.java:1060-1084` (`handleDailyBrief`, `handleDemandForecast`, etc.) | `"• 4 candidate KYC submissions awaiting...\n• 2 hospital facilities...\n• 3 ICU shifts tonight..."` & `"Expect a 35% surge in Critical Care ICU Nurses in South Bengaluru..."` | **High** | Admin morning brief and demand forecast return completely fake counts and locations whenever Gemini/Claude fails or is unconfigured. | Delete canned numbers; compute briefs from live repository counts/queries and surface LLM errors honestly. | `from reading the code only` (corroborated by user runtime log) |
| **B-1.14** | `src/main/java/com/thefale/controller/AiController.java:34-38` (`getStatus`) | `Map.of("activeProvider", aiService.getActiveProvider(), "status", "ONLINE", "supportedTasksCount", 20)` | **Medium** | Always returns `"ONLINE"` even when external LLM keys are missing or returning 404/503. | Ping/verify `geminiAiClient.isConfigured()` / `claudeAiClient.isConfigured()` and report `"DEGRADED"`/`"OFFLINE"` accurately. | `from reading the code only` |
| **B-1.15** | `src/main/java/com/thefale/service/CourseService.java:32-60` (`initDefaultCoursesIfEmpty`) | `if (courseRepository.count() < 20) { courseRepository.saveAll(defaults); }` | **Medium** | Re-seeds 23 hardcoded courses on boot whenever table count falls below 20, making admin course deletion/curation impossible across restarts. | Move seed catalog into a versioned Flyway migration and delete `@PostConstruct` seeding. | `from reading the code only` |
| **B-1.16** | `src/main/java/com/thefale/service/ShiftClaimService.java:46-55` (`claimShift`) | `if (candidate == null) { candidate = CandidateProfile.builder()...kycStatus("VERIFIED").city("Chennai").experienceYears("1").build(); }` | **Blocker** | If a candidate without a profile calls `/api/v1/shifts/{id}/claim`, backend auto-creates a profile marked **`kycStatus("VERIFIED")`** with `"Chennai"` / `"1"`, completely bypassing admin KYC verification! | Require an existing `CandidateProfile` with `kycStatus == "VERIFIED"` (`ForbiddenException` otherwise); never auto-verify on shift claim. | `from reading the code only` |

## 1.3 Defaults in Code or Config Hiding Missing Values in Production

| ID | File Path & Line | Snippet | Severity | Production Impact | Exact Fix | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **B-1.17** | `src/main/resources/application.yml:23` | `secret: ${JWT_SECRET:4f7d9a3b6e8c2a1f0d5b7e9a3c6f8b2d1e0a5c7b9d3f6a8b2c4e1f0d7b9a3c6e}` | **Blocker** | `application-prod.yml` does **not** override `fale.security.jwt.secret`. If `JWT_SECRET` env var is omitted in prod, backend boots with the public repo secret and anyone can forge `ROLE_ADMIN` tokens. | Remove fallback default in `application.yml` or set `${JWT_SECRET}` without default in `application-prod.yml`; reject the known default string in `JwtTokenProvider.init()`. | `from reading the code only` |
| **B-1.18** | `src/main/resources/application.yml:4-5` | `profiles: active: dev` | **High** | Running `java -jar app.jar` without `-Dspring.profiles.active=prod` boots in `dev` profile, enabling Dev OTP console logging (`dev-otp-enabled: true`), Swagger UI, and default `admin@thefale.com / admin123`. | Default `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:prod}` or fail fast if profile isn't explicitly provided. | `from reading the code only` |
| **B-1.19** | `src/main/java/com/thefale/service/AuthService.java:52, 240, 267` | `public static final String ADMIN_SECURE_PASSCODE = "FALE907010";` | **Blocker** | Anyone knowing static string `FALE907010` can register new `ROLE_ADMIN` accounts (`/api/v1/auth/admin/signup`, `permitAll()`) or reset any admin password (`/api/v1/auth/admin/forgot-password`, `permitAll()`). | Remove static passcode and public admin signup/forgot-password endpoints; implement invite tokens and email reset links. | `from reading the code only` |
| **B-1.20** | `src/main/java/com/thefale/service/AdminBootstrapService.java:30-44` | `if (isDev) { adminEmail = "admin@thefale.com"; adminPassword = "admin123"; }` | **High** | Hardcoded admin creds in `dev`; in `prod`, if `ADMIN_INITIAL_EMAIL`/`ADMIN_INITIAL_PASSWORD` are unset, app boots cleanly with zero admin users and no way to manage the platform. | Require `ADMIN_INITIAL_EMAIL` + `ADMIN_INITIAL_PASSWORD` in `prod` if `userRepository.countByRole("ROLE_ADMIN") == 0`. | `from reading the code only` |
| **B-1.21** | `src/main/resources/application.yml:28, 30-33` | `allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}`, `type: ${STORAGE_TYPE:local}`, `dev-otp-enabled: ${DEV_OTP_ENABLED:true}` | **Medium** | Missing `CORS_ALLOWED_ORIGINS` silently locks out real web origin; missing `STORAGE_TYPE` stores uploaded KYC files on ephemeral container disk. | Override in `application-prod.yml` with `${CORS_ALLOWED_ORIGINS}` (no localhost default) and `STORAGE_TYPE=s3`. | `from reading the code only` |

## 1.4 Stubs and Placeholders Assessment (Backend)

| Integration / Subsystem | Real / Stub / Fake | Evidence (`File:Line`) | What it Does in Production Today | Verification |
| :--- | :--- | :--- | :--- | :--- |
| **DigiLocker Integration** | **Fake / Absent** | 0 Java files reference DigiLocker; UI uses `DigiLockerModal.tsx` | Backend has zero DigiLocker OAuth/API client. Identity verification depends entirely on manual admin file inspection. | `from reading the code only` |
| **Bank Penny Drop** | **Fake / Stub** | `CandidateService.java:360`, `FacilityService.java:288,294` | Immediately sets `status = "Verified"` and `verifiedAt = Instant.now()` on submit with zero bank API call; stores fake `"ENC_" + rawAcc`. | `from reading the code only` |
| **Payment Gateway (Escrow Top-Up & Credits)** | **Fake / Stub** | `FacilityWalletService.java:89-110, 172-186` | `topUp` instantly credits `availableBalance` from a self-typed `utr` string without Razorpay/Cashfree webhook; `buyCredits` gives free credits on ₹0 balance AND lets client override `request.getCredits()`. | `from reading the code only` |
| **PayoutProvider (Disbursement)** | **Manual Stub** | `AdminService.java` (`disbursePayout`) | Admin manually enters an arbitrary UTR string; zero RazorpayX / Cashfree Payouts IMPS/NEFT API call is triggered. | `from reading the code only` |
| **SMS / Email Sending** | **Fake / No-Op in Prod** | `AuthService.java:98-101` | When `devOtpEnabled == false`, only executes `log.info("Production SMS dispatched to masked phone...")`. **No OTP SMS or email is ever sent in production.** | `from reading the code only` |
| **File Scanning / Upload Validation** | **Fake / Unused** | `pom.xml` has `tika-core`, 0 refs in `src/`; `CandidateService.java:444-445` | Accepts JSON `fileUrl` strings instead of `MultipartFile`; hardcodes `fileSizeBytes(512000L)` / `256000L`; zero Apache Tika magic-byte check or ClamAV scan. | `from reading the code only` |
| **PHI Filter** | **Naive Regex Stub** | `PhiProtectionService.java:11-18` | 8 regex patterns throw `BadRequestException` on text posts/questions. False positives on phrases like `"patient presenting..."` or 10-digit lab values; misses PAN, ABHA IDs, DOBs, and image EXIF/DICOM. | `from reading the code only` |
| **Match Scoring** | **Partial (Bypassed in 3 Services)** | `JobService.java:299`, `ShiftClaimService.java:453`, `FacilityResdexService.java:143` | `JobService.calculateMatchScore` is real rule-based scoring; `ShiftClaimService` checks `candidate.getCity() != null` (always +10 without comparing facility city!); `FacilityResdexService` (`94`), `FacilityJobManagementService` (`96`), and `AdminService` (`85`) hardcode constants. | `from reading the code only` |
| **AI Provider Engine** | **Live LLM + Fake Engine Fallback** | `GeminiAiClient.java:24-28`, `AiService.java:79-85`, `FaleClinicalAiEngine.java` | Live Gemini hits 404 on `gemini-2.5-flash`/`2.0-flash`/`1.5-flash` and 503 on `3.1-flash-lite`; `AiService` silently hides failure with `FaleClinicalAiEngine` canned metrics. | `from reading the code only` + runtime log |
| **Verification Badge** | **Internal DB Check Only** | `VerificationController.java:31-73` | Queries internal `kycStatus` column only; no connection to NMC / INC / state council registers. | `from reading the code only` |

## 1.5 Non-Test Keyword Search Summary (`src/main/**`)

Search command run across `F:\theFaleOne-Backend\src\main`: `Select-String -Pattern "TODO|FIXME|mock|dummy|sample|placeholder|hardcoded|demo"`.
- `TODO` / `FIXME`: **0 occurrences** in `src/main/**`.
- Notable hits: `CandidateService.java:360` (`// Instant verification for dev/demo or Penny Drop in production`), `FaleClinicalAiEngine.java:371` (`boolean isDummy = regNo.isEmpty() || ... "test" ...`), `GeminiAiClient.java:40,90` & `ClaudeAiClient.java:45` (`equalsIgnoreCase("placeholder")`), `DevProfileBootstrapService.java:15`, `V10__clean_mock_mentors.sql:1`.

---

# AUDIT 2: STATIC PAYLOADS AND FALLBACK DATA (FRONTEND)

## 2.1 `src/lib/data.ts`, `src/lib/store.ts`, and Mock Leaks into `VITE_USE_API=true`

Search across `src/` showed `@/lib/data` imported across `landing.tsx`, `onboarding.tsx`, `home.tsx`, `jobs.index.tsx`, `jobs.$id.tsx`, `applications.tsx`, `shifts.tsx`, `learning.index.tsx`, `learning.$id.tsx`, `huddle.tsx`, `profile.tsx`, `employer.index.tsx`, `employer.post.tsx`, `employer.post-shift.tsx`, `employer.applicants.tsx`, `employer.candidates.tsx`, `employer.profile.tsx`, `admin.candidates.tsx`.
While static Indian lookup catalogs (`professions`, `indianStates`, `indianCities`, councils, `departmentsByProfession`, `skillsByProfession`) are legitimate reference metadata, **mock entity datasets (`jobs`, `shifts`, `courses`, `applicants`, `initialHuddlePosts`) leak directly into `VITE_USE_API=true` mode** in the following places:

| File:Line | What Leaks / Fallback Pattern | Production Impact (`VITE_USE_API=true`) | Severity | Verification |
| :--- | :--- | :--- | :--- | :--- |
| `src/lib/adapters/authAdapter.ts:131-147, 192-212, 236-241` | `if (!isApiEnabled \|\| err?.message?.includes("Failed to fetch") \|\| err?.status === 0) { ... mockAdmin ... }` | If backend is unreachable (`Failed to fetch` / `status === 0`) in API mode, **admin login and admin signup silently succeed locally with a fake `mock-admin-token-...`**. | **Blocker** | `from reading the code only` |
| `src/routes/employer.wallet.tsx:282` | Uses `mockTaxInvoices` fallback when `apiTransactions.length === 0` | New facilities with zero wallet history see fabricated tax invoices with fake rupee figures in production. | **High** | `from reading the code only` |
| `src/routes/applications.tsx:175-180`, `employer.applicants.tsx:232`, `admin.applications.tsx:245` | Merges or falls back to `allJobs` from `src/lib/data.ts` | Displays fake hospitals/jobs from `data.ts` when job lookup is missing or during state sync. | **High** | `from reading the code only` |
| `src/routes/admin.huddle.tsx:332-340` | `const storeCases = storePosts.filter((p) => !apiIds.has(p.id)); return [...apiFlaggedPosts, ...apiReviewCases, ...apiLivePosts, ...storeCases];` | Merges Zustand store posts into the live admin moderation queue even when `isApiEnabled === true`. | **Medium** | `from reading the code only` |
| `src/lib/adapters/learningAdapter.ts` & `src/routes/learning.index.tsx:173-178` | Falls back to `COURSE_CURRICULUM_REGISTRY` / `"Dr. K. Senthil Nathan"` & mock hospitals on empty/error API response | Failed `/courses` requests show fake curriculum & fake faculty names as real accredited CME courses. | **High** | `from reading the code only` |
| `src/routes/admin.candidates.tsx:279` & `admin.facilities.tsx:247` | Fallback `generateClinicalDocumentBlob(doc)` / `generateFacilityDocumentBlob(doc)` | When fetching a document URL fails or returns non-PDF bytes, the UI **generates a synthetic PDF via jsPDF** and displays it in the KYC viewer as if it were the user's real government/council certificate! | **Blocker** | `from reading the code only` |
| `src/routes/employer.candidates.tsx:253-278` & `employer.applicants.tsx:211-298` | Fabricates skills, bios, `"BLS / ACLS Certified"`, `"NABH Clinical Protocols"` badges, and `"94%"` match score | Presents unverified fabricated clinical certifications to hiring hospitals. | **Blocker** | `from reading the code only` |
| `src/lib/adapters/aiAdapter.ts:52-74` | `executeAiTask` catches backend error and returns `getLocalAiFallback(req)` | Masks live AI failures in frontend too; `kyc_precheck` (L922) returns `99.2%` confidence purely from `regNo.length >= 4`. | **High** | `from reading the code only` |
| `src/components/fale/FlowAIFloatingButton.tsx:240-250` | `catch { setMessages(... "I'm connected to the FALE Healthcare platform..." ) }` | Even if both backend & adapter throw, Ally claims `"I'm connected"` and gives fake suggestions. | **Medium** | `from reading the code only` |
| `src/routes/admin.support.tsx:189-192` | `catch` sets canned WhatsApp draft text instead of surfacing error | Masks AI handoff draft failure silently. | **Low** | `from reading the code only` |

## 2.2 Static Payloads & Hardcoded Values Sent or Rendered in Components

| Screen / File | Role | Element / Line | Source (`API` / `mock` / `hard-coded`) | Acceptable in Prod? | Notes (`from reading the code only`) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/` (`index.tsx`) | Public / Candidate / Facility | Email Sign-In (`L338-348`) & Social Login buttons (`L357-359`) | **Hard-coded / Local Only** | **NO (Blocker)** | Calls `completeSignIn(v)` locally with zero backend call or JWT; enters `/onboarding` unauthenticated. |
| `/landing` (`landing.tsx`) | Public | Marketing trust bar (`100% Pre-Verified Talent Pool`, `NABH-Aligned Background Checks`, L1281-1285) | **Hard-coded** | **NO** | Misleading claims while DigiLocker & council verification APIs do not exist yet. |
| `/landing` (`landing.tsx`) | Public | Live Job search results (`liveJobs` L208 vs `allJobs` import L4) | **API** (in API mode), static bundled in JS | **Partial** | Works with API, but bundles all mock jobs in JS chunk. |
| `/verify` (`verify.tsx`) | Candidate / Facility | Fallback OTP phone `+919876543210` (`L92`) | **Hard-coded** | **NO** | Sends/displays wrong fallback phone if state missing. |
| `/admin/login` (`admin.login.tsx`) | Admin | `handleQuickFill()` sets `admin@thefale.com / admin123` (`L246-253`); `ADMIN_STATIC_PASSCODE = "FALE907010"` (`authAdapter.ts:165`) | **Hard-coded** | **NO (Blocker)** | Admin passcode shipped in client JS bundle; demo creds embedded in login page code. |
| `/onboarding` (`onboarding.tsx`) | Candidate / Student / Facility | `DigiLockerModal` (`DigiLockerModal.tsx:23,73-85`) | **Hard-coded Fake UI** | **NO (Blocker)** | Simulates UIDAI OTP (`123456`), waits `setTimeout(1200)`, outputs `"Aadhaar-DigiLocker-Verified.pdf"`. |
| `/onboarding` (`onboarding.tsx`) | Candidate / Facility | Sample Resume Auto-Fill (`resumeParser.ts:567-600`, `"Priya Sharma"`, `9840123456`) | **Hard-coded** | Acceptable only if dev-gated | Should be removed or hidden behind `import.meta.env.DEV` so real clinicians don't onboard with sample identity. |
| `/jobs` (`jobs.index.tsx` / `jobAdapter.ts`) | Candidate | `match: 95` (`L100`), hash-invented `shift` (`L74-75`) & `isUrgent` (`L86`), hardcoded `perks` (`L104`), fallback `"Bengaluru"` (`L92`) | **Hard-coded** | **NO** | Invents shift timing, urgency badges, perks, and 95% match scores in browser. |
| `/shifts` (`shifts.tsx` / `shiftAdapter.ts`) | Candidate | Fallback org `"Hospital Partner"`, city `"Chennai"`, hardcoded `perks: ["Guaranteed 24-hr Payout", "Malpractice Covered"]` | **Hard-coded** | **NO** | Legal insurance claim `"Malpractice Covered"` is fabricated in UI adapter (`shiftAdapter.ts`). |
| `/earnings` (`earnings.tsx`) | Candidate | `mockTdsRecords` (`L77-81`: `TDS-2026-Q3-01`, `10% (Sec 194J)`) vs API mapping (`L355`: hardcoded `1% (Sec 194J)` / `0%`) & support email `support@thefale.com` | **Hard-coded / Mock** | **NO** | Mock TDS rows have wrong rate (10% vs 1% backend); API mapper hardcodes `1%` instead of reading `platform_settings` / `claim.tdsAmount`. |
| `/profile` (`profile.tsx`) | Candidate | Fallback salary `"₹60,000 – ₹90,000/mo"`, fabricated skills/languages/hospital (`L237, 251-276`) | **Hard-coded** | **NO** | Renders fake salary expectations & hospital history on empty profiles. |
| `/notifications` (`notifications.tsx` / `notificationAdapter.ts`) | All | `hospital: "FALE Healthcare Network"`, `hospitalCode: "FL"` (`notificationAdapter.ts:59-61`) | **Hard-coded** | **NO** | Overwrites actual hospital source with generic string. |
| `/learning` (`learning.index.tsx` / `learning.$id.tsx`) | Candidate / Student | Fallback `COURSE_CURRICULUM_REGISTRY`, `"Dr. K. Senthil Nathan"` | **Mock Fallback** | **NO** | Displays static mock courses when API fails or returns empty. |
| `/employer/post-shift` (`employer.post-shift.tsx`) | Facility | Hardcoded date `"2026-10-31"` (`L174`), fake `estimatedTalentCount` (`L410-413`), hardcoded `5000` paise surge | **Hard-coded** | **NO** | Hardcoded past/static date breaks scheduling; fake talent pool estimate misleads employers. |
| `/employer/candidates` (`employer.candidates.tsx`) | Facility | `94%` match, fake `BLS / ACLS Certified` & `NABH Clinical Protocols` certs (`L253-278`) | **Hard-coded** | **NO (Blocker)** | Fabricates clinical life-support certifications candidates may not possess. |
| `/employer/applicants` (`employer.applicants.tsx`) | Facility | Fabricated skills/bios/certs + `allJobs` merge (`L211-298`) | **Hard-coded** | **NO (Blocker)** | Shows unverified qualifications to hospital HR. |
| `/employer/wallet` (`employer.wallet.tsx`) | Facility | `mockTaxInvoices` when transactions empty (`L282`); static package prices (`₹1,999`, `₹4,999`, `₹12,999`) | **Mock Fallback / Hard-coded** | **NO** | Fake tax invoices shown to new hospitals; package prices duplicated from Java switch. |
| `/admin/candidates` & `/admin/facilities` | Admin | `generateClinicalDocumentBlob` / `generateFacilityDocumentBlob` PDF generator (`L279`, `L247`) | **Hard-coded JS PDF** | **NO (Blocker)** | Fabricates fake KYC documents inside Admin verification console. |
| `ClinicalConciergeWidget.tsx` | All | `+91 73383 67726`, `contact@thefale.com` (vs `support@thefale.com` in `landing.tsx`) | **Hard-coded** | Acceptable only if canonicalized | Email mismatch (`contact@thefale.com` vs `support@thefale.com`). |

---

# AUDIT 3: DUMMY OR NON-FUNCTIONAL UI (ALL ROLES & ROUTES)

## 3.1 Broken / Mock-Only / Fake-Success Interactions by Route & Role

| Route | Role(s) | UI Element / Action | Actual Behaviour (`from reading the code only`) | Field / DTO Sync Gap | Severity |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/` (`index.tsx:338-359`) | Public / Candidate / Facility | "Continue with Email" & Google / LinkedIn / Apple buttons | No backend auth endpoint exists. Calls `completeSignIn()` locally, sets React store `signedIn: true` with no JWT, redirects to `/onboarding`. All subsequent API calls return 401. | `email` / OAuth provider not sent to backend. | **Blocker** |
| `/verify` (`verify.tsx:279-286`) | Public / Candidate / Facility | "Resend code" button | Resets countdown timer UI and shows toast without calling `sendOtp()`. | Zero API call fired. | **High** |
| `/onboarding` (`onboarding.tsx:775-817, 969-1033`) | Student & Graduate | Student & Graduate/Fresher "Complete Onboarding" submit | `onboardCandidate(...)` is **not awaited**; catches error via `console.error` while unconditionally marking `onboarded: true` and showing success toast. | User proceeds to dashboard while backend persistence failed. | **High** |
| `/onboarding` (`DigiLockerModal.tsx`) | Candidate | "Verify via DigiLocker (Aadhaar)" modal | Local `setTimeout(1200)` with demo OTP `123456`; creates `"Aadhaar-DigiLocker-Verified.pdf"` filename string with zero UIDAI OAuth or real document bytes. | Document URL/bytes not uploaded. | **Blocker** |
| `/jobs/$id` (`jobs.$id.tsx:154-208`) | Graduate / Student | "Apply Now" & "Withdraw" buttons | Not awaited; updates local state & displays success toast even on 400/403/500 failure. Sends mock fallback `"KMC/DOC/89421"`. | Backend `403 Student barred` / `409 Conflict` hidden behind fake success. | **High** |
| `/applications` (`applications.tsx:248-280`) | Graduate / Student | "Withdraw" & "Re-apply" buttons | `withdrawApplication` is not awaited; `handleReapply` is 100% local store mutation with zero backend call. | `Re-apply` never creates backend `JobApplication`. | **High** |
| `/learning` & `/learning/$id` (`learning.index.tsx:200-215`, `learning.$id.tsx:257-298`) | Candidate / Student | Enroll, Progress, & Unenroll buttons | `enrollCourse` / `updateCourseProgress` not awaited; **Unenroll button erroneously calls `enrollCourse()`** instead of unenrolling! | Server enrollment never removed; quiz completion not validated server-side. | **High** |
| `/notifications` (`notifications.tsx:433-435, 499-519`) | All Authenticated | WhatsApp/SMS/Email preference toggles; "Accept Shift" & "Confirm Interview" quick buttons | Only mutate local component state + toast; zero backend API endpoints exist or are called. | Notification preferences & quick actions lost on page refresh. | **High** |
| `/support` (`support.tsx:360-366, 436-441`) | All Authenticated | Support ticket `urgency`, `fullName`, `email` inputs; FAQ "Helpful Yes/No" buttons | `supportAdapter.ts:32-44` sends only `{ subject, category, description }`. `urgency`, `fullName`, `email` are collected and discarded; FAQ feedback does nothing on backend. | Triage urgency never stored or visible in `/admin/support`. | **Medium** |
| `/huddle` (`huddle.tsx:451-453`) | Candidate / Student | Posting / Verified Doctor badge | Checks `kycStatus !== "pending"` (lowercase) against backend `"PENDING"` (uppercase) -> marks unverified KYC candidates as `isVerified = true`. | Unverified users gain verified seal badge in Huddle UI. | **High** |
| `/employer/post` (`employer.post.tsx:248-271, 340, 496, 633`) | Facility | Publish job, Save Draft, Profession, Shifts, Mark as Urgent | `postFacilityJob` not awaited (shows `"Submitted for Admin Verification!"` even on API error); **"Save draft"** only saves to volatile store (lost on refresh); `profession`, `shifts`, `isUrgent` collected in form but **never sent** to backend (`PostJobRequest` lacks them). | Drafts lost; urgent/shift tags discarded. | **High** |
| `/employer/post-shift` (`employer.post-shift.tsx:174, 429-435, 560`) | Facility | Shift form, Draft button, Unit/Floor, Perks, Supervisor Contact | Hardcoded default date `"2026-10-31"`; Save Draft is volatile; `unitFloor`, `selectedPerks`, `supervisorName`, `supervisorPhone` collected but not persisted by `PostShiftRequest`. | Shift supervisors & unit location lost on save. | **High** |
| `/employer/candidates` (`employer.candidates.tsx:247-284`) | Facility | "Verified Only" filter toggle | Completely ignored when `isApiEnabled === true`; shows unverified profiles with fake `BLS/ACLS` badges. | Broken search filter. | **Medium** |
| `/admin` (`AdminLayout.tsx:193-196`) | Admin | Top-bar `"Search candidate, council reg, hospital…"` input | Bare `<input>` with zero `value`, `onChange`, or `onSubmit` handler — purely decorative. | Admin global search bar does nothing. | **Medium** |
| `/admin/huddle` (`admin.huddle.tsx:358-420`) | Admin | "Issue Formal Warning", Approve/Dismiss/Delete post | `handleWarnConfirm` (`L411-419`) only updates Zustand store (`warnHuddleAuthor`) with **zero backend API call**; Approve/Dismiss/Delete fire async `.catch(console.error)` while showing immediate success toasts. | Warnings evaporate on reload; failed moderations look successful. | **High** |
| `/admin/candidates` & `/admin/facilities` | Admin | Document preview modal / "View Document" | Generates synthetic PDFs on fetch failure (`B-1.12` / Audit 2) AND constructs HTML via `win.document.body.innerHTML = '<img src="' + doc.fileUrl + ...` without escaping (`XSS`). | Admin approves KYC against fake PDF; stored XSS vector via filename/URL. | **Blocker** |

## 3.2 Backend Fields Unused by UI vs UI Fields Missing from Backend

- **Backend DTO fields collected/supported or returned with UI mismatch**:
  - `PostShiftRequest` & `LocumShiftDto`: Missing `unitFloor`, `perks`, `supervisorName`, `supervisorPhone` collected on `/employer/post-shift`.
  - `PostJobRequest` & `JobPostingDto`: Missing `profession`, `shifts`, `urgent` collected on `/employer/post`.
  - `CreateSupportTicketRequest` & `SupportTicketDto`: Missing `urgency` / `priority`, `contactName`, `contactEmail` collected on `/support`.
  - `AdminMfaVerifyRequest`: Exists in backend `com.thefale.dto`, unused by both backend controller and frontend UI.
  - `Notification`: Backend tracks `read` / `title` / `message` / `actionUrl`, has no facility name/code fields (so `notificationAdapter.ts:59-61` hardcodes `"FALE Healthcare Network"` / `"FL"`) and no notification preference settings endpoint.

---

# AUDIT 4: STATIC VALUES AND MAGIC NUMBERS IN CODE

## 4.1 Hardcoded Business Values (Backend & Frontend)

| Item | Location | Hardcoded Value | Recommended Target | Severity | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Credit Packages & Prices** | `FacilityWalletService.java:149-174` & `employer.wallet.tsx` | `pkg_10` (₹1,999), `pkg_30` (₹4,999), `pkg_100` (₹12,999) **+ client `request.getCredits()` override!** | DB table `credit_packages` + remove client quantity override | **Blocker** | `from reading the code only` |
| **Low Escrow Alert Threshold** | `ShiftManagementService.java:110` | `BigDecimal("10000")` (₹10,000) | `platform_settings` | **Medium** | `from reading the code only` |
| **Shift Hours Bounds** | `ShiftManagementService.java:58` | `hours < 4 \|\| hours > 24` | `platform_settings` | **Medium** | `from reading the code only` |
| **Minimum Mentor Experience** | `HuddleService.java:484` | `years < 3` | `platform_settings` | **Medium** | `from reading the code only` |
| **OTP Expiry & Max Attempts** | `AuthService.java:81, 122` | `5 minutes`, `5 attempts` | `AppProperties` / env | **Medium** | `from reading the code only` |
| **Rate Limit Thresholds & `Retry-After`** | `RateLimitService.java:42-56`, `RateLimitFilter.java:47` | OTP 3/15m, Verify 5/15m, Global 100/1m; `Retry-After: "60"` hardcoded | `AppProperties` + Redis Bucket4j + dynamic refill nanos | **High** | `from reading the code only` |
| **Hardcoded `"2026"` Year Refs & Dates** | `ShiftClaimService.java:104`, `FacilityWalletService.java:112,188`, `employer.post-shift.tsx:174`, `earnings.tsx:78-80` | `"PAY-2026-FALE-"`, `"INV-2026-"`, `"2026-10-31"` | `Year.now().getValue()` / `new Date()` | **Medium** | `from reading the code only` |
| **Hardcoded TDS Label in Frontend** | `earnings.tsx:355` vs `earnings.tsx:78` | `"1% (Sec 194J)"` (API map) vs `"10% (Sec 194J)"` (`mockTdsRecords`) | Derive from `claim.tdsAmountPaise / claim.grossAmountPaise` | **Medium** | `from reading the code only` |
| **Dev/Filesystem Path in Java** | `GeminiAiClient.java:60` | `"f:/theFaleOne-Backend/.env"` | Standard Spring `@Value` / Secret Manager (delete filesystem lookup) | **High** | `from reading the code only` |
| **Hardcoded Default Fallback Profile/Job Values** | `FacilityResdexService.java:134-143`, `jobAdapter.ts:92-104`, `shiftAdapter.ts:117-135` | `"Chennai"`, `"Bengaluru"`, `"1 year"`, `94`, `95`, `96`, `85` | Remove fake defaults; return `null` or compute server-side | **Blocker** | `from reading the code only` |

## 4.2 Leaked Secrets, Passwords, Keys, and Sensitive Artifacts

| ID | Location | Finding (Redacted) | Severity | Production Impact | Exact Fix | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **S-1** | `F:\theFaleOne-Backend\.env:2-3` | Live Gemini API key `GEMINI_API_KEY=AQ.Ab8RN6...<REDACTED>` & duplicate `AI_API_KEY` on disk. | **Blocker** | Unencrypted API key on disk; though `.env` is in `.gitignore`, if exposed it grants paid/quota access to Gemini. | Rotate key in Google AI Studio; load via runtime secret manager in prod, not `.env` file reads (`GeminiAiClient.resolveKeyFromDotEnv`). | `from reading the code only` |
| **S-2** | `AuthService.java:52` & `src/lib/adapters/authAdapter.ts:165` | `ADMIN_SECURE_PASSCODE = "FALE907010"` hardcoded in Java AND shipped in public frontend JS bundle! | **Blocker** | Any visitor inspecting browser JS devtools extracts `FALE907010` and can register an admin account or reset any admin's password via public `permitAll()` endpoints. | Delete static passcode from both repos; remove public admin signup/forgot-password; rotate all admin passwords. | `from reading the code only` |
| **S-3** | `application.yml:23`, `.env.example:7`, `README.md` (git commit `c7063f8`) | Hardcoded 256-bit `JWT_SECRET=4f7d9a3b6e8c...` committed in git history & used as YAML fallback default. | **Blocker** | Anyone cloning repo can forge valid HS256 JWTs for any user or admin if prod fails to override `JWT_SECRET`. | Strip fallback default in YAML, enforce startup check rejecting known dev key, rotate prod `JWT_SECRET`. | `from reading the code only` |
| **S-4** | `Hash.java:3`, `AdminBootstrapService.java:42`, `admin.login.tsx:247-251` | Hardcoded `"admin123"` password committed in `Hash.java` (`c7063f8`), dev bootstrap, and `handleQuickFill()` in `admin.login.tsx`. | **High** | Exposes standard default admin credential in git history and client JS bundle. | Delete `Hash.java`, guard or remove `handleQuickFill()` from prod builds, force unique prod admin credentials. | `from reading the code only` |
| **S-5** | `GeminiAiClient.java:132` | Sends `?key=` + `apiKey` in URL query string instead of `x-goog-api-key` header. | **Medium** | Leaks Gemini API key into proxy/firewall/HTTP access logs and exception traces. | Pass key via `.header("x-goog-api-key", apiKey)`. | `from reading the code only` |

---

# AUDIT 5: BACKEND ERROR HANDLING AND RESILIENCE

## 5.1 Broken Build / Schema State & Failed Tests

- **B-5.0 [BLOCKER — verified by running `.\mvnw.cmd test`]**: `SchemaManagementException: Schema-validation: missing table [candidate_profile_views]`.
  - Entity `src/main/java/com/thefale/entity/CandidateProfileView.java:11` maps `@Table(name = "candidate_profile_views")`, but **no Flyway migration (`V1__`–`V17__`) creates `candidate_profile_views`** (also `V14` is skipped: migrations jump from `V13` to `V15`, and root `clean_db.sql` omits it too). Because `application-dev.yml` and `application-prod.yml` use `ddl-auto: validate`, **both integration tests (9 errors / 19 tests) and fresh production deployments fail to boot**.
  - **Fix**: Add `V18__candidate_profile_views.sql` creating `candidate_profile_views` and index `idx_candidate_views_cid_date` (and reconcile the `V14` gap before prod).
- **B-5.0b [HIGH — from reading the code only]**: `ShiftLifecycleIntegrationTest.java:270-291` asserts `403 Forbidden` with `"Healthcare students are not legally permitted to practice as independent locum staff under NMC/INC regulations."` when a student claims a locum shift. A full search across `src/main/**/*.java` found **0 occurrences** of that guard; `ShiftClaimService.claimShift()` (`L42-56`) does NOT check `candidateLevel == "STUDENT"` or `kycStatus == "VERIFIED"`.

## 5.2 Money & Transaction Vulnerabilities

1. **B-5.1 [BLOCKER] — Free Sourcing Credits + Client-Controlled Quantity (`FacilityWalletService.java:172-186`)**:
   - Line 172–174: `if (request.getCredits() != null && request.getCredits() > 0) { creditsToAdd = request.getCredits(); }` lets the client specify arbitrary `credits` for the ₹1,999 `pkg-1` package.
   - Line 178–185: If `wallet.getAvailableBalance().compareTo(costRupees) < 0` (including `₹0.00`), the balance deduction `if` block is skipped, `paidFromEscrow` stays `false`, yet line 184 still executes `wallet.setCandidateCredits(wallet.getCandidateCredits() + creditsToAdd)`.
   - **Impact**: Any hospital with ₹0 balance can mint unlimited candidate unlock credits for free.
   - **Fix**: Remove client override on `credits`; require verified gateway payment OR throw `BadRequestException` if `availableBalance < costRupees`.

2. **B-5.2 [BLOCKER] — Escrow Double-Refund on Claim Cancel + Shift Cancel (`ShiftClaimService.java:366-393` vs `ShiftManagementService.java:190-200`)**:
   - When a shift is posted, escrow for all openings (`totalPerWorker * openings`) is locked once. When a candidate cancels a claim, `ShiftClaimService.java:374-378` reopens the slot AND refunds `slotPay` back to `availableBalance`. If the hospital subsequently cancels the open shift, `ShiftManagementService.java:191-199` refunds `totalPerWorker * openings` again — double-refunding the slot escrow and driving `lockedEscrowBalance` negative.
   - **Fix**: Candidate claim cancellation must only reopen the shift slot (`slotsClaimed - 1`, `status = "OPEN"`) and keep the slot escrow locked in `lockedEscrowBalance` until either worked or shift-cancelled by the facility.

3. **B-5.3 [BLOCKER] — Unverified Self-Reported UTR Credits Real Money Immediately (`FacilityWalletService.java:89-127`)**:
   - Submitting any unused UTR string instantly increments `wallet.availableBalance` and marks the transaction `"Completed"` without bank reconciliation or payment gateway signature verification.
   - **Fix**: Record manual UTR submissions as `PENDING_VERIFICATION` without crediting `availableBalance` until approved by finance/admin or verified via Razorpay/Cashfree webhook HMAC.

4. **B-5.4 [HIGH] — Idempotency Filter Route Mismatch & Concurrent Race Window (`IdempotencyFilter.java:34-48, 77-101` vs `ShiftController.java:38`)**:
   - Filter watches `"/api/v1/employer/shifts"`, whereas `ShiftController` binds to `"/api/v1/shifts"` and `"/api/v1/facility/shifts"` — so idempotency is **completely bypassed on shift creation**.
   - Furthermore, two simultaneous requests with the same `Idempotency-Key` both pass `getCachedResponse()` before either reaches line 98 `saveResponse()`, and `IdempotencyKey` uses only `key` as `@Id` (cross-user PK collision risk) with no request payload hash comparison.
   - **Fix**: Align routes, insert an `IN_PROGRESS` composite `(key, user_id)` lock row with SHA-256 request hash before `filterChain.doFilter`, and clean up expired keys via scheduled job.

5. **B-5.5 [MEDIUM] — Missing Ledger Row on Resdex Credit Spend (`FacilityResdexService.java:84-93`)**:
   - Decrements `candidateCredits` without inserting a `WalletTransaction` audit record, breaking accounting reconciliation.

## 5.3 Exception Handling, Performance & Resilience

- **GlobalExceptionHandler (`GlobalExceptionHandler.java:25-120`) [MEDIUM]**:
  - `HttpMessageNotReadableException` (`L54`) leaks `ex.getMostSpecificCause().getMessage()` (exposing internal Java class/package names when Jackson `fail-on-unknown-properties: true` triggers).
  - Missing handlers for `MaxUploadSizeExceededException` (returns 500 instead of 413), `MethodArgumentTypeMismatchException` (invalid UUID returns 500 instead of 400), `HttpRequestMethodNotSupportedException` (returns 500 instead of 405), and `MissingServletRequestParameterException` (500 instead of 400).
  - Generates local `UUID.randomUUID()` per exception with no `MdcCorrelationFilter` across normal requests.
- **Unbounded `findAll()` & N+1 Traps [HIGH]**:
  - `FacilityResdexService.java:49` & `AdminService.java:82,97,116`: calls `findAll()` on entire tables in JVM memory with zero pagination.
  - `ShiftClaimService.java:446-449`: fires **4 separate `candidateProfileRepository.findById()` queries per row** in `mapClaimToDto`! Plus N+1 loops in `JobService.getMyApplications`.
  - Nearly all list controllers (`AdminController`, `JobController`, `ShiftController`, `FacilityWalletController`, `HuddleController`, `NotificationController`) return unpaginated `List<T>`.
- **Missing Validation `@Valid` & Auth Guards [HIGH/MEDIUM]**:
  - Missing `@Valid` on `@RequestBody` in `AdminController:385,403`, `AiController:26`, `CandidateController:119`, `CandidateEarningsController:52`, `FacilityController:56,85`, `HuddleController:50`.
  - `HuddleController:75` (`/posts/{id}/pin`) lacks `@PreAuthorize("hasRole('ADMIN')")` (any logged-in user can pin posts).
  - `SecurityConfig.java:84` puts `/api/v1/ai/**` in `permitAll()` (public unauthenticated LLM drain).
  - `ShiftClaimService.java:324-337` (`disputeShift`) has zero ownership check — any candidate/facility can dispute any `claimId` (IDOR).
  - `CandidateController.java:163` (`POST /{id}/view`) lacks facility ownership check.
- **External AI Thread Starvation & Missing Schedulers [MEDIUM]**:
  - `GeminiAiClient.java:116-138`: loops over 4 models × 60s timeout = up to **240 seconds blocking a servlet thread** on AI outage! (`3` of those `4` models — `2.5-flash`, `2.0-flash`, `1.5-flash` — return HTTP `404` per runtime logs.)
  - `FaleApplication.java` has **no `@EnableScheduling`** — unattended `CLAIMED` shifts, expired `otp_verifications`, and revoked `refresh_tokens` never expire/clean up.

---

# AUDIT 6: FRONTEND RELIABILITY, ACCESSIBILITY AND UX

| ID | File:Line | Issue | Severity | Production Impact | Exact Fix | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **F-6.1** | `src/routes/admin.candidates.tsx:296`, `admin.facilities.tsx:262` | `win.document.body.innerHTML = \`<img src="${doc.fileUrl}" ... alt="${doc.originalFileName}" />\`` | **Blocker** | **DOM XSS**: unescaped `doc.fileUrl` / `doc.originalFileName` injected into `innerHTML` in new browser window with origin access. | Use `document.createElement("img")`, set `img.src` (after `https:`/`data:image/` scheme allowlist) & `img.alt` via DOM properties; avoid `innerHTML`. | `from reading the code only` |
| **F-6.2** | `src/lib/api.ts:145-260` | Zero `AbortController` / request timeout in `apiFetch` | **High** | Requests (especially hung `/ai/execute` calls lasting up to 240s) hang browser tabs indefinitely with stuck spinners. | Wrap `fetch` in `AbortController` with sensible timeouts (e.g. 15s default, 25s AI) and clean up timer. | `from reading the code only` |
| **F-6.3** | `src/lib/api.ts:171-250` | Ignores `429 Too Many Requests` & `Retry-After` header | **High** | Does not surface rate-limit cooldown countdown to user when OTP or AI endpoints return 429 (`RateLimitFilter` sets `Retry-After`). | Read `res.headers.get("Retry-After")` on 429 and throw structured error with remaining wait time. | `from reading the code only` |
| **F-6.4** | `onboarding.tsx`, `employer.post.tsx`, `jobs.$id.tsx`, `applications.tsx`, `learning.*`, `admin.huddle.tsx` | Fire-and-forget async API calls (not `await`ed) + immediate `toast.success(...)` | **High** | Backend `400/403/500` errors are logged to console and hidden; UI redirects and tells user `"Submitted"` / `"Applied"` when zero data was saved. | `await` every mutation inside `try/catch/finally`, disable buttons while pending, and show `toast.error` on failure before modifying local state or navigating. | `from reading the code only` |
| **F-6.5** | `home.tsx`, `shifts.tsx`, `applications.tsx`, `earnings.tsx`, `employer.*.tsx`, `huddle.tsx` | `Promise.all` loaders use `.catch(() => [])` or `.catch(() => null)` | **High** | API outages or 500 errors render indistinguishable empty lists or `₹0` wallets instead of error/retry banners. | Track explicit error state on every page and render a retry UI rather than silently coercing failures to `[]`. | `from reading the code only` |
| **F-6.6** | `facilityAdapter.ts:200,251,268,282` & `shiftAdapter.ts` | Generates fresh `generateIdempotencyKey()` inside each click/call | **Medium** | Re-clicking a failed/timed-out submit generates a **new** UUID each time, defeating backend idempotency against double-submissions. | Create an idempotency key per form-session / action intent and reuse it across retries of the same logical submission. | `from reading the code only` |
| **F-6.7** | `huddleAdapter.ts` (`HuddlePostDto`) | Duplicate `audioUrl`/`audioDuration` fields & `updateHuddlePost` reads field missing from param type | **Medium** | TypeScript contract confusion & unreliable voice note updates. | Deduplicate DTO fields and align `updateHuddlePost` parameter interface. | `from reading the code only` |
| **F-6.8** | `vercel.json:1-8` | Contains only SPA rewrite rule `{ "source": "/(.*)", "destination": "/index.html" }` | **High** | Deployed frontend serves **zero** CSP, HSTS, `X-Frame-Options`, `X-Content-Type-Options`, or `Referrer-Policy` headers on HTML/static assets. | Add `headers` array to `vercel.json` with strict CSP, `frame-ancestors 'none'`, `nosniff`, and HSTS. | `from reading the code only` |
| **F-6.9** | `vite build` (`onboarding-Ben2wsYM.js`) & `package.json` | 907.02 kB JS chunk (`onboarding`); `npm audit --omit=dev` shows 2 High vulns (`vite`, `zod`) | **High** | Heavy initial route download for onboarding on mobile networks + unpatched High CVE exposure in `zod` & `vite`. | Lazy-load heavy PDF/Lottie/resume-parser libs dynamically; upgrade `zod` and `vite`. | `verified by running it` |
| **F-6.10** | `src/test/setup.ts` & `src/test/app-routing.test.tsx` | Missing canvas context mock crashes `lottie_light.js` in Vitest (`TypeError: Cannot set properties of null`) | **High** | Automated route regression suite is currently broken (`1 failed | 1 passed`). | Mock `HTMLCanvasElement.prototype.getContext` in `src/test/setup.ts` or mock Lottie in unit tests, and expand adapter/UI tests. | `verified by running it` |
| **F-6.11** | User screenshots + `AdminLayout.tsx` / `FlowAIFloatingButton.tsx` | Mobile header/button spacing, dummy admin search input, `<button>`/pill overlap at bottom-right (`Ally` floating pill overlaps action buttons on lower rows) | **Medium** | Floating `"Ally"` trigger at `bottom-20 right-4` covers rightmost action buttons (`View`/scrollbars) when scrolled near bottom rows (observed in submitted Admin Action Center screenshots). | Add safe bottom padding (`pb-24`) on tables/cards or dock Ally above action rails without occluding interactive buttons; wire Admin header search input or remove it. | `verified by running it` (visual screenshots) / `from reading the code only` |

---

# AUDIT 7: PRODUCTION STANDARDS CHECKLIST

| Area | Control | Status | Observed Evidence |
| :--- | :--- | :--- | :--- |
| **1. Security** | RBAC / Auth on all endpoints | **FAIL** | `/api/v1/ai/**` is `permitAll()`, `togglePinPost` has no `@PreAuthorize`, `claimShift` auto-verifies KYC & misses student guard, `FALE907010` admin backdoor. |
| | IDOR checks | **FAIL** | `FacilityResdexService:149-150` leaks raw `unlockedPhone` & `unlockedEmail` for locked profiles; `ShiftClaimService.disputeShift` has 0 ownership check; `PayoutController.getPayoutSlip` has 0 ownership check. |
| | Rate limiting | **FAIL** | In-memory `ConcurrentHashMap` in `RateLimitService` (fails across replicas & leaks memory), trusts spoofable `X-Forwarded-For`, no dedicated limit on `/auth/admin/login`. |
| | CORS & Security headers (Backend / Frontend) | **PARTIAL** | Backend `SecurityConfig` sets HSTS/DENY/nosniff/CSP/Referrer-Policy + configured CORS (**PASS**); Frontend `vercel.json` sets zero headers (**FAIL**). |
| | Refresh cookies | **PASS** | `HttpOnly`, `Secure`, `SameSite=Strict`, scoped to `/api/v1/auth`. |
| | JWT secret & revocation | **FAIL** | Fallback public secret in `application.yml:23` (unoverridden in `prod`), PII (`phone`/`email`) in claims, no access-token revocation on logout. |
| | Admin MFA | **FAIL** | `mfaEnabled(false)`, `/admin/login` never checks MFA, `/admin/mfa/verify` controller absent despite `permitAll()` entry. |
| | File uploads & Tika | **FAIL** | Raw URL strings instead of `MultipartFile`; `tika-core` imported in `pom.xml` with 0 usages; fake file sizes; no AV scan. |
| **2. Data** | Flyway migrations | **FAIL** | `candidate_profile_views` entity lacks migration (breaks `ddl-auto: validate` & tests); `V14` missing; destructive `clean_db.sql` & `Hash.java` in root. |
| | Soft deletes | **FAIL** | `deletedAt` column present on `User`/`JobPosting`, but `@SQLDelete` & `@SQLRestriction` missing. |
| | PII encryption & DPDP Act 2023 | **FAIL** | Candidate bank account stored plaintext; Hospital bank account uses `"ENC_" + rawAcc`; no consent records or erasure endpoint despite Privacy Policy promises. |
| **3. Money** | Escrow & Credit Ledger integrity | **FAIL** | Free sourcing credits (`₹0` balance bypass + `request.getCredits()` override); escrow double-refund on claim cancel + shift cancel; self-typed UTR instant wallet credit; missing credit-spend ledger row. |
| | Rounding & Dynamic Settings | **PARTIAL** | `BigDecimal` + `RoundingMode.HALF_UP` (**PASS**), but `getSettingAsDecimal` falls back silently & no Admin UI/API edits `platform_settings`. |
| | Idempotency | **FAIL** | `/api/v1/shifts` missed by filter (`employer/shifts` mismatch), concurrent race before save, single-column PK `(key)` risk. |
| **4. Reliability** | Graceful shutdown, Hikari, Timeouts | **FAIL** | Missing `server.shutdown: graceful`, unconfigured HikariCP pool sizing, missing `apiFetch` timeout, 4×60s blocking Gemini loop, no `@EnableScheduling`. |
| | Queries, Pagination, Observability | **FAIL** | `findAll()` in memory (`AdminService`, `FacilityResdexService`), 4× duplicate `findById` per claim in `ShiftClaimService`, unpaginated lists, no Micrometer/Prometheus, no MDC filter. |
| | CI/CD & Containers | **FAIL** | No `Dockerfile`, `docker-compose.yml`, or CI workflows; `npm audit` fails with 2 High vulns. |
| **5. Testing** | Automated suites | **FAIL** | Backend: `.\mvnw.cmd test` fails (`9 errors / 19 tests`). Frontend: `npm test -- --run` fails (`app-routing.test.tsx`). OWASP check blocked offline (`not verified`). |

---

# FINAL OUTPUT

## 1. Master Sorted Findings Table

| ID | Severity | Area | File:Line | Problem | Production Impact | Exact Fix | Effort | Verification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **BLK-01** | **Blocker** | Auth / Secrets | `AuthService.java:52,240,267` & `authAdapter.ts:165` | Hardcoded `ADMIN_SECURE_PASSCODE="FALE907010"` exposed in Java & browser JS bundle on `permitAll()` admin signup/reset endpoints. | Total admin takeover by anyone reading JS devtools. | Remove passcode & public admin signup/forgot-password; use invite tokens. | **M** | `from reading the code only` |
| **BLK-02** | **Blocker** | Auth / Frontend | `src/routes/index.tsx:338-359` | Email login & Social buttons call `completeSignIn()` locally with zero backend call or JWT. | Unauthenticated users bypass sign-in and hit 401 across app. | Wire email OTP/password + OAuth callbacks to backend or disable unbacked buttons. | **M** | `from reading the code only` |
| **BLK-03** | **Blocker** | Auth / Frontend | `authAdapter.ts:131-147,192-212,236-241` | `adminLogin`/`Signup`/`ForgotPassword` fall back to `mock-admin-token` on network error even in API mode. | Network error grants fake admin cockpit access. | Gate mock fallbacks strictly to `!isApiEnabled && import.meta.env.DEV`. | **S** | `from reading the code only` |
| **BLK-04** | **Blocker** | Secrets / JWT | `application.yml:23` & `application-prod.yml` | Default public `JWT_SECRET` fallback not cleared in `prod`. | Missing env var lets attackers forge `ROLE_ADMIN` tokens. | Require `${JWT_SECRET}` with no default in `prod`; reject known dev key on boot. | **S** | `from reading the code only` |
| **BLK-05** | **Blocker** | Money / Wallet | `FacilityWalletService.java:172-186` | Buying credits succeeds with ₹0 balance (`paidFromEscrow=false`) AND trusts `request.getCredits()`. | Infinite free sourcing credits exploit. | Remove `request.getCredits()` override; fail if `availableBalance < costRupees`. | **S** | `from reading the code only` |
| **BLK-06** | **Blocker** | Money / Escrow | `ShiftClaimService.java:374-378` vs `ShiftManagementService.java:191-199` | Candidate cancel refunds slot escrow, then facility shift cancel refunds full shift escrow again. | Double refund drains escrow & creates negative balances. | Remove escrow refund from `cancelClaim`; keep locked until shift completion or shift cancel. | **S** | `from reading the code only` |
| **BLK-07** | **Blocker** | Money / Wallet | `FacilityWalletService.java:89-127` | Arbitrary client-typed UTR instantly credits real rupees to wallet `availableBalance`. | Unlimited fake wallet money to post shifts. | Set UTR top-ups to `PENDING_VERIFICATION` until bank webhook / admin approval. | **M** | `from reading the code only` |
| **BLK-08** | **Blocker** | Security / IDOR | `FacilityResdexService.java:149-150` | `unlockedPhone` & `unlockedEmail` always return unmasked `rawPhone` & `rawEmail` even when `isUnlocked=false`. | Bypass paid credits: inspect JSON to scrape all doctors' contacts. | Set `unlockedPhone` / `unlockedEmail` to `null` unless `isUnlocked == true`. | **S** | `from reading the code only` |
| **BLK-09** | **Blocker** | Security / Compliance | `ShiftClaimService.java:46-56` + missing student guard | `claimShift` auto-creates missing profile as `kycStatus="VERIFIED"` and lacks student 403 block tested in `ShiftLifecycleIntegrationTest`. | Unverified candidates & healthcare students can claim live locum ICU shifts. | Enforce existing profile + `kycStatus=="VERIFIED"` + block `STUDENT` level with 403. | **S** | `from reading the code only` |
| **BLK-10** | **Blocker** | DB / Startup | `CandidateProfileView.java:11` & `db/migration/` | Missing Flyway table `candidate_profile_views`; `ddl-auto: validate` crashes. | Integration tests fail (`9 errors/19`) and clean DB prod fails to start. | Add `V18__candidate_profile_views.sql` and fix `V14` gap. | **S** | `verified by running it` |
| **BLK-11** | **Blocker** | KYC / Integrity | `DigiLockerModal.tsx:73-85`, `admin.candidates.tsx:279`, `admin.facilities.tsx:247` | Fake DigiLocker OTP (`123456`) + Admin viewer generates synthetic PDF certificates on load failure. | Fake identity verification & admins reviewing auto-generated fake PDFs. | Show real error if doc fails to load; disable fake DigiLocker until real API integrated. | **M** | `from reading the code only` |
| **BLK-12** | **Blocker** | Security / XSS | `admin.candidates.tsx:296`, `admin.facilities.tsx:262` | Unescaped `doc.fileUrl` / `originalFileName` passed to `win.document.body.innerHTML`. | Stored XSS inside Admin session when previewing uploaded docs. | Render via safe DOM `createElement("img")` + URL scheme validation. | **S** | `from reading the code only` |
| **BLK-13** | **Blocker** | Security / AI Cost | `SecurityConfig.java:84` & `AiService.java:79-85` | `/api/v1/ai/**` is `permitAll()`, and `AiService` masks 404/503 outages with `success: true` fake metrics (`4/2/3`, `South Bengaluru`). | Public LLM key abuse + false operational briefs on AI outage. | Authenticate `/api/v1/ai/**`; surface real errors instead of synthetic numbers. | **S** | `from reading the code only` + log |
| **BLK-14** | **Blocker** | Comms / OTP | `AuthService.java:98-101` | Production OTP SMS is a log-only stub (`dev-otp-enabled: false` sends nothing). | Zero candidates or hospitals can log in in production. | Integrate real SMS/email OTP provider (MSG91/Twilio/SES). | **M** | `from reading the code only` |
| **BLK-15** | **Blocker** | Security / PII | `CandidateService.java:356`, `FacilityService.java:288` | Candidate bank account stored plaintext; facility bank account prefixed `"ENC_"` only. | Breach exposes raw bank account numbers (DPDP/RBI violation). | Implement AES-256-GCM JPA `AttributeConverter` with KMS key. | **M** | `from reading the code only` |
| **BLK-16** | **Blocker** | Money / Data | `PayoutController.java:66`, `CandidateService.java:469` | Hardcoded non-existent `https://api.thefale.com/.../*.pdf` URLs with no PDF generation & no IDOR check. | 404 downloads + Candidate A can inspect Candidate B payout reference. | Verify claim ownership + stream real PDF bytes / signed S3 URL. | **M** | `from reading the code only` |
| **BLK-17** | **Blocker** | Fake Data / UX | `FacilityResdexService.java:119-143`, `FacilityJobManagementService:530`, `employer.*.tsx` | Hardcoded match scores (`94`/`96`), fake contacts (`+91 98401 23456`, `candidate@fale.care`), & fabricated `BLS/ACLS` badges. | Hospitals hire based on fabricated clinical credentials & match scores. | Compute real match scores; drop all fabricated contacts/certificates. | **M** | `from reading the code only` |
| **BLK-18** | **Blocker** | Secrets | `F:\theFaleOne-Backend\.env:2-3` | Active Gemini key on disk + `.env` filesystem lookup in `GeminiAiClient.java:60`. | Local secret risk + breaks containerized prod config. | Rotate key; remove `resolveKeyFromDotEnv()`; inject via secret manager. | **S** | `from reading the code only` |
| **HIG-01** | **High** | Security / IDOR | `ShiftClaimService.java:324-337` | `disputeShift` has zero caller ownership validation. | Any candidate/hospital can freeze any shift claim's escrow. | Verify `principal` owns either the claim or the parent shift. | **S** | `from reading the code only` |
| **HIG-02** | **High** | Money / Idempotency | `IdempotencyFilter.java:34-48,77-101` | Watches `/employer/shifts` (missing `/shifts`); no pre-execution lock; single-column PK. | Duplicate shifts/debits on retries or concurrent clicks. | Fix route set + atomic `(key, userId)` `IN_PROGRESS` lock + payload hash. | **M** | `from reading the code only` |
| **HIG-03** | **High** | Security / Auth | `AuthService.java:231`, `SecurityConfig:82`, `AdminBootstrapService:31-42` | Admin MFA unimplemented; defaults `dev` profile & `admin@thefale.com/admin123`; zero prod admin failsafe. | Admin accounts unprotected by 2FA; unsafe default profile. | Default `prod` profile, add TOTP MFA flow, fail if no prod admin exists. | **L** | `from reading the code only` |
| **HIG-04** | **High** | Security / Rate Limit | `RateLimitService.java`, `RateLimitFilter.java:64-69` | In-memory buckets only, trusts spoofable `X-Forwarded-For`, no limit on `/auth/admin/login`. | Attacker rotates header IP to brute-force OTPs/admin logins. | Distributed Redis Bucket4j + trusted proxy config + strict admin login throttle. | **M** | `from reading the code only` |
| **HIG-05** | **High** | Security / Files | `CandidateController:119`, `CandidateService:444-445`, `FacilityService:384` | URL-string uploads, unused `tika-core`, hardcoded 512KB/256KB sizes, no malware scan. | SSRF/broken URLs, unverified file types/sizes, container storage loss. | Multipart upload + Apache Tika magic bytes + S3 + AV scan. | **L** | `from reading the code only` |
| **HIG-06** | **High** | Reliability / Perf | `AdminService:82,97`, `FacilityResdexService:49`, `ShiftClaimService:446-449` | `findAll().stream()` across whole tables + 4 duplicate `findById` queries per claim + no pagination. | Heap OOM & severe latency under real hospital/candidate load. | SQL `WHERE`/aggregations + `JOIN FETCH` + `Pageable` endpoints. | **M** | `from reading the code only` |
| **HIG-07** | **High** | Frontend / Mutations | `onboarding.tsx`, `employer.post.tsx`, `jobs.$id.tsx`, `applications.tsx`, `learning.*`, `admin.huddle.tsx` | Fire-and-forget un-awaited API mutations show optimistic `toast.success` even on 400/500. | Users believe jobs/applications/KYC succeeded when backend rejected them. | `await` mutations + rollback/show `toast.error` on catch. | **M** | `from reading the code only` |
| **HIG-08** | **High** | Frontend / Fallbacks | `employer.wallet.tsx:282`, `applications.tsx`, `learning.index.tsx`, `admin.huddle.tsx` | Mock invoices, mock courses, mock jobs, and store huddle posts leak into `VITE_USE_API=true` mode. | Fake financial/course data displayed in production. | Strictly guard all mock fixtures with `!isApiEnabled && import.meta.env.DEV`. | **M** | `from reading the code only` |
| **HIG-09** | **High** | Frontend / UI Bugs | `verify.tsx`, `huddle.tsx:451`, `notifications.tsx`, `learning.$id.tsx` | Fake resend timer, case bug marks unverified KYC as verified in Huddle, unenroll calls enroll, non-persistent notification toggles. | Unverified users show verified seal; users stuck enrolled/un-notified. | Fix case check, wire real resend & unenroll/prefs endpoints or remove dead toggles. | **S** | `from reading the code only` |
| **HIG-10** | **High** | Reliability / Frontend | `api.ts`, `home.tsx`, `shifts.tsx`, `earnings.tsx`, `vercel.json` | No `AbortController` timeout, ignores `429 Retry-After`, silent `.catch(()=>[])`, zero headers in `vercel.json`. | Hung UI tabs, hidden outages, weak browser hardening. | Add request timeout, 429 retry-after toast, error banners, `vercel.json` headers. | **M** | `from reading the code only` |
| **HIG-11** | **High** | Supply Chain / Tests | `package.json`, `vitest.config.ts`, `src/test/setup.ts` | `npm audit` fails (2 High: `vite`, `zod`); Vitest fails (`lottie` null canvas context); 907 kB chunk. | Vulnerable dependencies + broken CI test gate + slow mobile load. | Upgrade `vite`/`zod`, fix canvas test setup, code-split `onboarding`. | **M** | `verified by running it` |
| **HIG-12** | **High** | Ops / Reliability | `application-prod.yml`, `application.yml`, `clean_db.sql` | No `server.shutdown: graceful`, default Hikari 10 pool, destructive `clean_db.sql` in repo root, secrets in git log. | Dropped transactions on deploy, pool exhaustion, accidental DB wipe risk. | Add graceful shutdown & pool sizing; move `clean_db.sql`; rotate secrets. | **S** | `from reading the code only` |
| **MED-01** | **Medium** | Security / Auth | `HuddleController:75`, `CandidateController:163` | `/posts/{id}/pin` has no `@PreAuthorize`; `POST /{id}/view` allows anyone to spoof `facilityName`. | Non-admins pin posts; view counts can be faked. | Add `@PreAuthorize("hasRole('ADMIN')")` / `FACILITY` checks. | **S** | `from reading the code only` |
| **MED-02** | **Medium** | Data / Money | `FacilityResdexService:84-93`, `CourseService:137-142` | Resdex credit spend lacks `WalletTransaction` ledger row; `progressPercent=100` mints CME certificate without assessment check. | Unreconciled credit balance & spoofable CME credentials. | Persist `credit_usage` ledger row; validate module completions server-side. | **S** | `from reading the code only` |
| **MED-03** | **Medium** | Validation / Errors | `GlobalExceptionHandler:51-112`, multiple controllers | Missing `@Valid` on 8 endpoints; Jackson error leaks class names; 413/400/405 return 500; `ShiftClaimService` ignores city comparison. | Dirty DB payloads, 500s on invalid UUID/large files, broken shift match city score. | Add `@Valid`, sanitize JSON error, add handlers, compare `facility.city`. | **M** | `from reading the code only` |
| **MED-04** | **Medium** | Reliability / AI | `GeminiAiClient:24-28,116-138`, `FaleApplication.java` | 3 dead 404 Gemini models + 4×60s (240s) blocking timeout; `?key=` in URL; no `@EnableScheduling` for stale claims/OTPs. | Thread pool starvation on AI outage + locked claims never expire. | Remove dead 404 models, header API key, 8s timeout + ShedLock reaper job. | **M** | `from reading the code only` + log |
| **MED-05** | **Medium** | Data / Soft Delete | `User.java`, `JobPosting.java`, `db/migration/` | `deletedAt` ignored (no `@SQLRestriction`); Flyway `V14` gap; `AdminService:925` vs DB `platform_settings` unedited. | Deleted users can still log in; deleted jobs remain queryable. | Add `@SQLDelete` & `@SQLRestriction("deleted_at IS NULL")`. | **S** | `from reading the code only` |
| **MED-06** | **Medium** | Form Sync / UX | `employer.post.tsx`, `employer.post-shift.tsx`, `support.tsx`, `AdminLayout:193` | Collected fields (`urgency`, `shifts`, `unitFloor`, `perks`, `supervisor`) discarded; Drafts lost on reload; Admin header search & support urgency non-functional. | Hospital/support metadata silently dropped. | Extend DTOs/DB or remove unbacked inputs; wire/remove Admin search bar. | **M** | `from reading the code only` |
| **MED-07** | **Medium** | Ops / Privacy | `PhiProtectionService.java`, `AuthService:96`, `pom.xml`, `Dockerfile` | Naive PHI regex blocks clinical words; dev OTP/emails logged; no Prometheus metrics/MDC/Dockerfile/CI; DPDP consent/erasure missing. | False-positive post rejections + poor prod observability & DPDP gap. | Refine PHI regex, add MDC + Prometheus + Dockerfile + DPDP endpoints. | **L** | `from reading the code only` |
| **LOW-01** | **Low** | API / UI Consistency | `AiController.java:28,34`, `RateLimitFilter:47`, `AuthService:369`, `GlobalExceptionHandler:27`, `AdminController`, `LandingPage` | Unwrapped `AiController` responses; static `Retry-After: 60`; hardcoded `+91`; `contact@thefale.com` vs `support@thefale.com`; `Ally` pill overlaps corner buttons on narrow/short scroll viewports. | Minor UX inconsistencies, trace friction, button occlusion on mobile/table edges. | Wrap in `ApiResponse<T>`, dynamic `Retry-After`, unify email, add `pb-24` clearance. | **S** | `from reading the code only` / screenshots |

---

## 2. Immediate Launch BLOCKERS (Do Not Ship Until Resolved)

1. **Test & Startup Crash (`BLK-10`)**: Missing Flyway migration for `candidate_profile_views` crashes Hibernate `ddl-auto: validate` (verified: `mvnw test` fails `9/19`).
2. **Broken & Bypassable Authentication (`BLK-01`, `BLK-02`, `BLK-03`, `BLK-04`, `BLK-14`)**:
   - Hardcoded `FALE907010` admin passcode in Java + browser JS + `permitAll()` signup/reset.
   - Public fallback `JWT_SECRET` in `application.yml` (unoverridden in `prod`).
   - Frontend email/social logins work with zero backend/JWT; `adminLogin` falls back to mock admin on network error; **production SMS OTP is a log-only stub** (real users cannot receive OTPs).
3. **Money & Escrow Exploits (`BLK-05`, `BLK-06`, `BLK-07`, `BLK-16`)**:
   - Sourcing credits granted for free at `₹0` balance + client-controlled `request.getCredits()` override.
   - Double escrow refund across `ShiftClaimService.cancelClaim` + `ShiftManagementService.cancelShift`.
   - Self-typed UTR instantly deposits fake money into wallet `availableBalance`.
   - Payout slips & CV exports return hardcoded 404 URLs with missing ownership checks.
4. **Clinical Safety, KYC & Data Privacy Breaches (`BLK-08`, `BLK-09`, `BLK-11`, `BLK-12`, `BLK-15`, `BLK-17`)**:
   - `FacilityResdexService` returns unmasked `unlockedPhone` & `unlockedEmail` on locked candidates.
   - `ShiftClaimService.claimShift` auto-creates `kycStatus="VERIFIED"` profile and lacks student shift block.
   - Fake DigiLocker modal + Admin KYC viewer generates fake PDFs + stored XSS via `innerHTML`.
   - Bank accounts stored plaintext (`candidate_payout_methods`) or `"ENC_" + raw` (`hospital_bank_accounts`).
   - Hardcoded match scores (`94`/`96`) and fabricated `BLS/ACLS` certs shown to hospitals.
5. **Unauthenticated LLM Drain & Dishonest AI Fallback (`BLK-13`, `BLK-18`)**: `/api/v1/ai/**` is `permitAll()`, dead 404 Gemini models + 503 silently return `success: true` with fabricated metrics, and active API key sits in `.env` / filesystem loader.

---

## 3. Pilot-Acceptable Items (Require Tracked Post-Pilot Tickets)

*(Only after all 18 Blockers and High money/auth/data items above are fixed — suitable for a closed, manual-ops pilot with vetted hospitals):*
1. **Manual Admin Council Verification** (instead of automated DigiLocker/NMC APIs) — acceptable for pilot **only once** `DigiLockerModal` is replaced/hidden so users aren't misled, real `MultipartFile` upload + Tika check is active, and fake PDF fallback in Admin is removed.
2. **Manual Admin Reconciliation for Bank Top-Ups / Payouts** — acceptable for pilot **only if** UTR top-ups enter `PENDING_VERIFICATION` without crediting balance until finance verifies actual bank statement credit (`BLK-07`), and manual payout UTR entry stays admin-only (`AUDIT 1.4`).
3. **Simple Keyword/Rule Match Score (`JobService` / `ShiftClaimService`)** — acceptable for pilot once hardcoded `85`/`94`/`96` and fabricated certificates are removed (`BLK-17`) and `ShiftClaimService` city comparison is fixed.
4. **Huddle PHI Regex Filter (`PhiProtectionService`)** — acceptable for pilot once clinical-verb false positives and missing PAN/ABHA patterns are tuned (`MED-07`), alongside manual admin case review.
5. **Missing Prometheus Metrics & Container CI/CD (`MED-07`, `LOW-01`)** — track for scaling/GA after pilot launch, provided graceful shutdown, HikariCP sizing, and real SMS/backups are enabled.

---

## 4. Honest 5-Line Verdict

The platform has solid domain modeling and many intended OWASP controls on paper, **but it is not production-ready and cannot safely launch even a closed pilot today**. A missing Flyway migration breaks startup validation and backend tests (`9/19` errors), while the frontend test suite and dependency audit both fail. Critical financial safeguards are broken: hospital wallets can mint free sourcing credits, self-credit arbitrary UTRs, and trigger double escrow refunds, while locked candidate contacts leak in plaintext. Authentication has fatal holes in both layers — a hardcoded `FALE907010` admin passcode in JS/Java, fallback JWT secret, unbacked email/social login, offline mock-admin fallback, and no real production SMS OTP delivery. Combined with fake DigiLocker/PDF-generation stubs, unauthenticated AI endpoints masking 404/503 errors with fabricated numbers, and DOM XSS in admin preview, launch must be blocked until P0 fixes land.

---

## 5. Proposed Batched Fix Order (Before Any Code Changes Begin)

- **Batch 1 — Boot, Tests & Critical Secret/Auth Lockdown (P0)**:
  - Add `V18__candidate_profile_views.sql` (fix `mvnw test` + `ddl-auto: validate`); resolve `V14` migration gap; remove `Hash.java` & root `clean_db.sql`.
  - Remove `FALE907010` passcode, public admin signup/forgot-password, `handleQuickFill`, fallback `JWT_SECRET`, and offline mock-admin fallback in `authAdapter.ts`.
  - Disable/remove fake email & social sign-in buttons until backend exists; integrate real OTP SMS provider (MSG91/Twilio); rotate Gemini key & remove `.env` disk loader.
  - Lock `/api/v1/ai/**`, `/posts/{id}/pin`, `claimShift` (enforce verified KYC + student 403 block), and `disputeShift` (IDOR check).
- **Batch 2 — Money, Escrow, Credits & Idempotency Integrity (P0)**:
  - Fix `FacilityWalletService.buyCredits` (remove `request.getCredits()` override, reject `₹0` balance) and `topUp` (`PENDING_VERIFICATION` until verified webhook/admin check).
  - Fix escrow double-refund in `ShiftClaimService.cancelClaim`; align `IdempotencyFilter` routes + pre-execution lock; add Resdex credit ledger entry; implement AES-256-GCM converter for bank accounts & real PDF generation + IDOR check for payout slips.
- **Batch 3 — Honest Data & Real KYC/Document Pipeline (P0/P1)**:
  - Null out `unlockedPhone`/`unlockedEmail` when locked in `FacilityResdexService`; remove all hardcoded `85`/`94`/`96` match scores, fake phone/email defaults, and fabricated `BLS/ACLS` badges.
  - Fix `GeminiAiClient` model IDs + timeouts + header API key, and make `AiService` / `aiAdapter` return honest errors instead of fake operational briefs.
  - Replace fake `DigiLockerModal`, fake admin jsPDF fallback (`BLK-11`), and `innerHTML` XSS (`BLK-12`) with real `MultipartFile` upload + Apache Tika verification + honest UI error states.
- **Batch 4 — Frontend Mutation Reliability, Real API Wiring & Security Hardening (P1)**:
  - Await all async mutations across onboarding, jobs, applications, learning, employer, and admin screens; remove remaining `mockTaxInvoices`/course/store leaks in API mode; fix Huddle `"PENDING"` check & unenroll bug.
  - Add `AbortController` timeouts, `429 Retry-After` handling, explicit error banners, `vercel.json` security headers, canvas test mock, and upgrade `vite`/`zod`.
- **Batch 5 — Performance, Pagination, Scheduling & Ops Polish (P2)**:
  - Replace `findAll().stream()` and 4× N+1 queries with SQL queries + `Pageable`; add `@Valid`, global exception handlers (413/400/405), `@SQLRestriction`, `@EnableScheduling` + ShedLock, Hikari/graceful shutdown, MDC/Prometheus, and `Dockerfile`/CI.

