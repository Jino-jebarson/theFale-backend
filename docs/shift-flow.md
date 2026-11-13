# Shift posting, attendance and fees

Hospitals must have verified KYC and a verified bank account to post. The first post awaits admin approval. After the first approval, later eligible posts publish automatically. Rejection does not unlock automatic publishing. Existing approved hospitals retain that eligibility through migration V20.

Applications do not consume openings. Hospital confirmation rechecks eligibility, overlapping confirmed bookings and available capacity, then reserves an opening. Public responses contain profession, city and qualification requirements. Reporting person, floor/ward, dress, bring checklist and private notes become visible to a candidate after confirmation. Admins and the owning hospital can inspect them.

Check-in requires a location and camera selfie captured within two minutes, plus the booking's six-digit PIN or QR token. Credentials are available to hospital staff, expire on successful use, and are withheld from candidate API responses. Five incorrect attempts block further attempts for five minutes. The existing arrival window is two hours before the shift through two hours after its end. QR scanning uses browser BarcodeDetector support; PIN entry is available where scanning is unsupported. Camera and location require browser permissions and a secure origin (localhost works for development).

Attendance evidence is served separately to the candidate, owning hospital or admin with Cache-Control: no-store. It is excluded from bulk lists. GPS capture does not establish a geofence; camera capture does not provide biometric liveness verification. Evidence currently resides in the database; production retention and access policies should account for this sensitive data.

Entering the sixth PIN digit or scanning the matching booking QR automatically starts GPS capture, opens the front camera, displays a three-second selfie countdown and submits the complete check-in request. Browser permission approval is still required. Permission failures block submission; retry captures fresh evidence. Closing/cancelling before submission stops camera tracks and prevents a later capture from submitting attendance.

Hospital posting reserves gross wages plus 8%. Clinicians pay no platform fee; existing statutory deductions remain separate. The first completed shift for each hospital is free: completion atomically records its shift ID while locking the wallet, refunds the reserved fee and saves the actual fee on the claim. All bookings on that same first shift receive the waiver. Competing completions on different shifts cannot both receive the waiver. Cancellation and rejection do not consume it. Later shifts cost the hospital 8%. Existing completed financial snapshots are retained. For example, a new Rs 4,000 booking reserves Rs 4,320; its first-shift completion refunds Rs 320.

The candidate submits a timesheet after check-in; hospital approval completes the booking and queues payout. Existing payout processing/manual disbursement remains in place; this change does not add a payment provider.

Restart the backend to apply V20 and V21 through Flyway before using the updated frontend. V21 supplies two tables already expected by existing entities and absent from the previous fresh-database migration chain. Do not apply these changes by enabling Hibernate schema updates.

Verification includes service rules, real authenticated API requests against isolated PostgreSQL, a concurrent completion test, migration validation, frontend component/adapter tests and isolated browser camera/location checks using synthetic devices. Browser audit requests use fixtures and do not submit real attendance.

The final backend shift run passed 20 tests (15 service rules, 3 attendance validator tests and 2 database/API tests). Desktop browser checks passed 150 success/empty/failure cases with 40 retry recoveries; mobile checks passed 50 layouts. These are targeted shift validations and browser fixture checks, not a claim that every legacy backend integration test has been updated.

The frontend production build passed and all 73 tests across 16 test files passed. The final frontend run used one worker and a 20-second test timeout to accommodate resource contention during local verification.
