# The Fale V53: screen, field and flow analysis

Source: `C:\Users\Dell\Downloads\TheFale_Website_v53 (1).html`. References below are original source line numbers, not backend source. This is a static code trace of a single-file interactive prototype, not a browser walkthrough or a guarantee that every control executes successfully. The companion [control index](v53-control-index.md) captures literal controls and modal headings, including old and conditional implementations; use it with the source to inspect individual fields. Dynamic controls and labels assembled from arrays cannot be fully enumerated by this lexical index.

## Architecture and paths

The document contains the public page, a sign-in document (`signinSrc`, L1702), an app document (`appSrc`, L2173), and a router embedding these with `srcdoc` (L12136). Hashes include sign-in, hiring, app, team, explore, jobs and Huddle. Application boot loads a sample database from browser localStorage, applies migrations, consumes sign-in handoff, restores session, or opens guest view (L12104). There are public, guest, professional, hospital and team experiences; the source contains successive overrides through V53. A definition appearing earlier is not necessarily the one used at runtime.

## Public website

- Film/introduction and navigation (L803); professional and hiring entry selectors (L964); job search/results (L933); role coverage and how it works (L959, L998, some sections initially hidden).
- Hospital hiring proposition and comparison (L1025, L1047), Huddle (L1070), founding mentor information and application (L1096), safety and verification tags (L1152), about (L1182), FAQ (L1203), contact/footer (L1249).
- Mentor application collects fields shown in its form; consult control index at L1128–1145. Ally widget answers product questions and links into site destinations (L1309 onwards). Public claims and response times are copy, not proof of service operation.

## Sign-in and guest join

### Main sign-in (L1924–2148)

Select professional or hiring. Phone option: +91 mobile, 10 digits beginning 6–9; send → six individual OTP boxes, paste, resend → success/handoff. Email: email format → six-digit code; hiring users on common personal email domains get a hint to use work email. Google/Apple buttons enter directly. Back returns to other sign-in choices. On success, role and contact are passed to the app via localStorage/parent messaging.

### Guest portal and intent join (L8088–8269)

Guest screens: Explore, Jobs and Huddle; search, filters and limited reading are available. Apply, save, reply, post, pay contribution or mentor intent can open a contextual join sheet preserving the pending intention. Join: phone or email → age 18+ and terms/privacy checkbox for new join → six-digit code, resend, WhatsApp/SMS choice on phone → name → profile. Existing local user gets a welcome-back card. Main sign-in enters directly at name/profile using `quickSetup`.

### Quick professional onboarding (L8135, modified L9292 and L10767)

Name, then choose resume upload or manual setup. Resume supports PDF/Word/text/image; reading and unreadable/retry states, sample resume, review detected profession, years of experience, area, skills and registration number. Manual path: student/fresher/working; course for student or profession for others; area. Later wrapper adds date of birth and gender on the detected-details and manual screens; source says date of birth is not shown to hospitals. On completion user profile is created and the original guest action can resume. Resume parsing/OCR may require externally loaded libraries and recognition is not guaranteed.

A separate older `candChoose`, `studentSetup` and five-step `proSetup` remain in source (L5804, L6961): the active `bootCandidate` calls `quickSetup`, so do not assume those older long forms are the default. The older pro flow includes name/DOB/gender/contact, profession/department/skills, council/registration/qualification/year/college, experience with dates or fresher, job types and shift preferences. Older student flow includes course, college, year, expected graduation, proof of study and age confirmation. They remain useful for understanding historical intended data, not proof of current initial entry.

## Professional portal

Base portal and its later overrides: L7048 onward, L7683 onward, L10500 onward, L11696 onward. Navigation: Home, Jobs, Shifts, Applications, Learning, Huddle, Profile; extra views are added by later scripts.

### Home

Greeting, verification/photo, professional level and area; profile views, role matches, nearby shifts, active applications and interviews. Conditional sections include profile completion, pending document check, payout account prompt, Open to Work/Priority pool, shift earnings, pay near you, learning, nearby shifts, schedule, recommended roles, introductions, activity and Huddle. Cards and dismiss/return rules change in V51–V53 (L11990–12085). Profile views can hide for the week until count increases. Cards can reappear when their content changes.

### Jobs and applications

Search role/hospital/skill; area/type filters, matching score, reasons and warnings; salary, experience, joining time, location, registration, specialty and openings. Save/bookmark, view details, apply. Later map/list mode is L11332. Before apply, show profile/contact and resume-sharing disclosure. Some applications are held pending verification. Hospital introductions can be accepted or declined; contact disclosure differs by path. Pipeline: applied, shortlisted, interview, offered, hired; also rejection/withdrawal. Interviews have candidate cards, calendar options, changes and cancellation; offers can be opened, accepted, declined or questioned and carry joining instructions.

Students are shown internships and the prototype gates professional jobs/shifts until appropriate graduation/verification. Conditions vary by role and job; inspect later hard-match checks at L10761 rather than assuming every listing is actionable.

### Profile and verification

Profile contains personal details/photo, profession, specialty, skills, qualifications, registration, work history, experience, resume, verification documents, work preferences and visibility. Profile photo/crop/avatar; name changes can require review; account/data-copy/deletion controls are in later code (L10775). A Fale-generated resume uses profile data (L9434). Document prompts include student proof of study, degree, council registration, experience, ID and certificates; there are pending/verified/rejected and resubmit states. Student graduation submits a degree and team approval changes status to fresher. Certificate expiry reminders and management appear L10978.

Work preferences modal added V53 (L12063) reuses L11735: up to five preferred areas, travel distance, job types, shift pattern and relocation cities; save updates profile and hospital candidate view. Open-to-Work status and joining availability determine visibility. Priority logic at L5954 requires non-student, active status, availability within 15 days, >=80% completion and confirmation less than 30 days old; reminder threshold is 27 days. Matching rules and exclusions are L5969–6012: weighted specialty 25, experience 15, distance 15, join time 15, salary 10, reputation 12, response 5, verified 3.

NCAHP registration and rules notices: registration type/state, number or acknowledgement, expiry where relevant; instructions, reminders and possible blocking are in L9328–9412. These are prototype rules and must not be relied on as current law.

### Shifts and payout

Shifts list, search/filters and map/list, availability and alerts; request, eligibility, review/confirmation and shift change response. Confirmed shift includes report-to, reporting place, dress/bring items and notes. On-my-way/late, check-in via QR/code, checkout, timesheet approval, cancellation, no-show/standing, dispute and mutual rating appear across L8270–8932 and L10930–11220. Availability editor includes days and time ranges, 30-minute choices and preset patterns (L11716). Shift earnings separate completed/unapproved, approved/due, paid and payout history; instant and Friday payout paths are modelled.

Payout account modal (L7509): holder name; UPI ID OR bank account number and IFSC; PAN. Name min 3, UPI regex, 9–18 account digits, IFSC format and PAN format validated. The displayed ₹1 verification is a timer simulation; only a masked account label and PAN suffix are retained in the demo object.

### Learning and Huddle

Learning: modules, content, quiz, results/pass and badges, CME, interview preparation and certification information (L6710). Huddle (L7929, L10850): feed/For you/following, rooms, posts, post types Thought/Experience/Case/Question for professionals and Update/Event for hospitals, comments/replies, report/moderation and rules. Media flows include crop, blur/privacy checklist and audio question/transcription screens. Mentor section includes questions, answers, follow-ups and solved state. Pay contribution and aggregated ranges have privacy thresholds in code. Verified status governs some posting actions; guest view is limited. See index for individual composer, report, profile and pay fields.

## Hospital onboarding and portal

### Onboarding (active later definition L9246; group fields replaced L11427)

1. Facility name (>=3 characters), multiple branches yes/no; if yes group/chain name and branch; facility type, area, optional bed count, hiring needs (full-time/internships/shifts).
2. Contact name (>=2), role, work email, mobile, optional GSTIN. Email and Indian mobile formats checked; optional GSTIN checked against a 15-character pattern.
3. Read service agreement/full agreement, check authority to accept and sign via six-digit code; live preview, completion and conditional welcome/founding offers. Group adds message about separate MOU. Form requires agreement and a signature object. The sign-code UI is demo logic; it is not evidence of a binding electronic signature system.

Then creates facility in pending verification and opens dashboard. Earlier two-step `hospSetup` L5923 is overridden; the three-step implementation is the relevant later one. Branch fields are replaced again at V47.

### Dashboard and organisation profile

Dashboard has facility details and verification, active roles/openings, applicants/interviews, shifts/coverage, candidate pool, weekly schedule, metrics, pending tasks, wallet and shortcuts. Hospital profile (L11453) includes name/type/location, address/beds, recruiter and contact, logo, cover, workplace images, about, specialties and perks; completion criteria appear L11385. Group/branch screens support branch information and HR invite fields (L11427–11479). Verification may gate candidate visibility and posting/community actions. Hospital name/group/accreditation/GST changes have review flows.

### Post a job and manage candidates

Requirement flow supports free-text need and structured job fields: title, role/profession, specialty, vacancies, minimum experience, area/radius, minimum/maximum budget and joining window, registration, urgency; later job form L11897 adds employment type, shift pattern, optional timings, working days/off, minimum qualification, in-hand versus CTC, benefits and service bond. It describes 30-day listing and one 15-day extension; expiration code L11915.

Select self-hire/credits or assisted route; terms, wallet/credits and agreement checks apply depending on route. Matching, results, candidate details, intro, consent/contact unlock, applicant stage management, close/reopen and job editing are modelled. Assisted route operations and candidate identity restrictions appear L10592–10675. Hiring records joining date and annual CTC and calculates invoices according to route; replacement request includes an optional explanation. These are prototype business rules, not verified contracts.

Interview form L11771: mode in person/video/phone; date/time; in-person address and optional ask-for; video generated or own URL; optional suggested questions; schedule, change or cancel. Interview outcome records completion/no-show/reschedule/cancel and feedback. Offer form L11857: role, monthly pay, joining date, shift pattern, probation, notice, respond-by date, day-one time and address, optional person to meet, selected/additional documents, benefits and notes. Validates salary >=5000 and joining/respond dates not in the past. Candidate can accept, decline or ask.

### Shifts, finance and analytics

Create shift: role, schedule/date and start/end, rate, slots and urgency plus profession-specific requirements; later form L8769 has must-have chips, report-to, where, dress, optional note and bring checklist. Booking requests, candidate checks, approve/decline, change and cancellation, check-in, timesheets, approval/dispute and ratings are modelled. Candidate response is requested when an affected shift changes. Hospital has wallet/prepaid, credits, transactions, invoices, checkout, bank-transfer option and analytics/date filters (L5336, L8658–8731). A demo checkout explicitly says no money moves.

## Fale team portal

Team sign-in: work email, then six-digit code; demo text accepts any six digits (L7548). Overview, verification, jobs/assisted hiring, shifts desk, people, Huddle review, handoffs/referrals, invoices/finance, rules and AI log are defined or added in later sidebar wrappers (L7562, L11230).

- Verification queues: hospital, student, degree, registration, experience, ID, certificate, mentor, name/group and other cases. Approve or reject with reason, notify locally, release held applications and update associated status.
- Hiring: requirement queue, matching and selecting profiles for introductions; candidate/application and assisted-hiring oversight.
- Shifts: bookings, gaps, approval/disputes, ratings, payout and finance oversight.
- Community: report/moderation and publish/remove, handoff resolution, referrals and mentor support.
- System/analytics: operational counts, revenue, funnel/targets, configurable registration rules and AI log. Actual visible navigation depends on later wrappers and state.

## Cross-cutting prototype behaviour and limits

- `loadDB/saveDB` persist a seeded in-browser DB in localStorage (L6121). The role views share that browser-local data; it is not evidence of a real shared backend.
- Main and guest OTP accept arbitrary six digits; Google and Apple are simulated; signing and account verification are simulated. Notifications, sample actors, gateway checkout, invoices and payouts must not be assumed to be external integrations.
- Some images are embedded; PDF/OCR libraries and fonts may require network access. The system uses a mixture of local rules, hardcoded samples and UI labels such as AI. A visible AI label alone does not establish a live model call.
- The code includes multiple generations and overrides, hidden and conditionally rendered UI, arrays and string-generated HTML. This inventory establishes source coverage; it does not prove accessibility or runtime behaviour of every branch. The companion lexical control index includes superseded definitions and is not a list of active unique fields.

## Additional field details

- Public founding mentor application (L1128): full name, mobile, optional email, profession, qualification route (8+ years, faculty/HOD, author, association office-bearer), workplace/teaching/book/association, optional profile link, non-agency declaration and consent to contact.
- Work preferences (L11733): 3, 5, 10, 15 or 25 km; full-time/part-time/contract; day/night/rotational/any; relocation choices Mysuru, Chennai, Hyderabad, Kochi, Mumbai, Pune and Delhi NCR. The V53 save handler limits preferred areas to five.
- Checkout (L5338): UPI QR or ID; a list of net-banking choices; a read-only demo card number, expiry and CVV; or sample bank-transfer details. Payment confirmation is simulated.
