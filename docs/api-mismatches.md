# API Contract & Frontend Model Mismatches

This document tracks all differences between the Spring Boot backend REST API shapes and the frontend component data representations, resolved via the adapter layer in `src/lib/adapters/`.

---

## Slice A: Auth, Onboarding & Candidate Profile

| Frontend Screen / Field | Frontend Mock Shape | Backend Contract / DTO | Resolution in Adapter Layer (`src/lib/adapters/`) |
| :--- | :--- | :--- | :--- |
| **Candidate Name** | `profile.name` (`string`) | Request: `fullName`, Response: `name` | `candidateAdapter` maps `fullName` to API and extracts `name` into UI state. |
| **Candidate Level** | `candidateLevel` (`"student" \| "fresher" \| "professional"`) | `candidateLevel` (`"STUDENT" \| "FRESHER" \| "PROFESSIONAL"`) | Adapter normalizes to uppercase when sending to API and lowercase for TanStack UI route states. |
| **College Name** | `profile.college` (`string`) | Request: `collegeName`, Response: `college` | Adapter maps `collegeName: data.college` on onboarding/update requests. |
| **Council Registration Number** | `profile.regNo` (`string`) | Request: `councilRegistrationNumber`, Response: `regNumber` | Adapter maps `councilRegistrationNumber` in request payloads and sets `regNumber` on response. |
| **Expected Salary** | `profile.salary` (`"₹45,000–₹75,000"`) | `expectedSalaryMinPaise`, `expectedSalaryMaxPaise` (`Long` integer paise) | `moneyAdapter` (`paiseToRupees` and `rupeesToPaise` in `src/lib/api.ts`) converts rupees $\leftrightarrow$ paise. |
| **Candidate Documents** | `docs` dictionary: `{ [docLabel: string]: string }` | `documents: List<CandidateDocumentDto>` (`id, documentType, originalFileName, fileUrl`) | `candidateAdapter` provides dedicated methods for document metadata creation and deletion (`POST /candidates/me/documents` and `DELETE /candidates/me/documents/{id}`). |
| **Refresh Token** | Previously stored in `localStorage` (`fale-pro-v2`) | `Set-Cookie: fale_refresh_token; HttpOnly; SameSite=Strict` | Refresh token is never accessed in JavaScript; browser automatically sends cookie with `credentials: "include"`. |
| **Personal Identifiable Info (PII)** | Stored in `localStorage` under `fale-pro-v2` | Never stored in `localStorage` when `VITE_USE_API=true` | `src/lib/store.ts` scrubs `profile`, `docs`, `contact`, `hospitalBankAccount`, and `candidatePayoutMethod` from local storage when API mode is enabled. |

> [!NOTE]
> **Slice A Status**: 100% Resolved. Zero unresolved items. Auth (OTP send, OTP verify, logout, cookie refresh), onboarding, and profiles for both Student and Graduate candidate personas are fully wired with mock-mode compatibility preserved.

---

## Slice B: Jobs, Applications, Notifications, Support & Learning

| Frontend Screen / Field | Frontend Mock Shape | Backend Contract / DTO | Resolution in Adapter Layer (`src/lib/adapters/`) |
| :--- | :--- | :--- | :--- |
| **Job Organization** | `job.org` (`string`) | `JobPostingDto.facilityName` | `mapJobPostingToUi` in `jobAdapter.ts` maps `facilityName` to `org`. |
| **Job Salary** | `job.salary` (`"₹35,000 – ₹50,000"`) | `salaryMinPaise`, `salaryMaxPaise` (integers) or `salaryDisplay` | `jobAdapter.ts` converts paise $\to$ rupees and formats currency string. |
| **Internship vs Job Type** | `job.kind === "internship"` & `forStudents: boolean` | `JobPostingDto.isInternship` | `jobAdapter.ts` maps `isInternship` to `kind: "internship"` and `forStudents: true`. Backend `JobService` automatically restricts student queries to internships. |
| **Application Status** | UI strings: `"Applied"`, `"Under review"`, `"Interview"`, `"Offer"`, `"Hired"`, `"Withdrawn"`, `"Not selected"` | Backend enum strings: `PENDING_ADMIN`, `SHORTLISTED`, `INTERVIEW`, `CONFIRMED`, `HIRED`, `WITHDRAWN`, `REJECTED` | `mapApplicationStatus` in `applicationAdapter.ts` translates backend statuses to UI tabs and timeline badges. |
| **Application Withdrawal** | In-memory `status: "Withdrawn"` | `POST /api/v1/applications/{id}/withdraw?reason=...` | `applicationAdapter.withdrawApplication` calls the endpoint and updates local store. |
| **Notification Categories** | UI tabs: `"shifts"`, `"jobs"`, `"cme"`, `"credentials"` | `NotificationDto.type`: `"SHIFT"`, `"JOB"`, `"CME"`, `"CREDENTIAL"`, `"SYSTEM"` | `notificationAdapter.mapNotificationToUi` maps backend notification types to UI categories and unread flags. |
| **Notification Read State** | Local boolean state `unread` | `PATCH /api/v1/notifications/{id}/read` & `PATCH /api/v1/notifications/mark-all-read` | `notificationAdapter` calls PATCH endpoints and updates local view. |
| **Support Ticket Message** | Form field `message` | `CreateSupportTicketRequest.description` | `supportAdapter.createSupportTicket` maps `message` $\to$ `description`. |
| **CME Course Enrollment & Progress** | Store array `courses: string[]` & lesson check array | `POST /api/v1/courses/{id}/enroll` & `POST /api/v1/courses/{id}/progress` with `{ progressPercent }` | `learningAdapter.enrollCourse` and `learningAdapter.updateCourseProgress` synchronize enrollment and completion percentages. |

> [!NOTE]
> **Slice B Status**: 100% Resolved. Zero unresolved items. Jobs feed, job details, applications tracking & withdrawal, real-time alerts, support concierge, and CME learning are fully wired with mock-mode compatibility preserved.


