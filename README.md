# FALE Healthcare Platform — Backend & Local Environment Guide

Production-grade, OWASP-aligned Spring Boot 3.3.5 REST backend for the FALE Healthcare Platform, supporting four distinct personas: **Healthcare Students**, **Graduate Clinicians**, **Healthcare Facilities / Employers**, and **Compliance Administrators**.

---

## 1. Prerequisites (Windows)
- **Java 21 JDK** (e.g. Eclipse Temurin 21 or Microsoft OpenJDK 21)
- **Node.js** v18+ & **npm**
- **Docker Desktop** (or PostgreSQL 16+ installed locally)
- **PowerShell** 5.1+ or PowerShell 7+

---

## 2. Wiping & Resetting the Development Database

If you wish to wipe the local database clean to test dynamic flows from scratch:

1. Connect to PostgreSQL using `psql` or pgAdmin / DBeaver:
   ```powershell
   # Using fale_admin (or postgres):
   psql -h localhost -U fale_admin -d fale_db -f docs/wipe-dev-db.sql
   ```
2. The script drops all tables, foreign keys, views, sequences, and history in the schema with CASCADE.
3. Starting/restarting the backend will re-run Flyway migrations (`V1`, `V2`, `V3`), creating the fresh schema, platform settings, statutory rate floors, and the course catalog — with **zero seed users, facilities, jobs, shifts, or wallets**.

---

## 3. Configuring Environment Variables

Set the environment variables in PowerShell before launching the application:

```powershell
# Database Connection (Spring Data JPA / Flyway)
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/fale_db"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"

# JWT Secret (Min 256-bit hex or HMAC key)
$env:JWT_SECRET="4f7d9a3b6e8c2a1f0d5b7e9a3c6f8b2d1e0a5c7b9d3f6a8b2c4e1f0d7b9a3c6e"

# CORS Origin (Points to your Vite frontend dev server)
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"

# Root Admin Bootstrap (Required to create the first admin account)
# If these variables are omitted in dev, the backend starts cleanly without an admin.
$env:ADMIN_INITIAL_EMAIL="admin@thefale.com"
$env:ADMIN_INITIAL_PASSWORD="YourSecureAdminPassword123!"

# Optional: Shorten Access Token lifetime for testing refresh interceptor (Default: 900000 = 15m)
# $env:JWT_ACCESS_TOKEN_EXPIRATION_MS="10000"
```

---

## 4. Running the Spring Boot Backend

From the root of `theFaleOne-Backend`, run:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

When started:
- **Flyway** automatically applies migrations (`V1`, `V2`, `V3`) to set up configuration and catalog data.
- **AdminBootstrapService** creates the first admin account ONLY if `ADMIN_INITIAL_EMAIL` and `ADMIN_INITIAL_PASSWORD` are configured.
- **Dynamic OTP Verification**: No fixed `123456` bypass. Each OTP request generates a cryptographically random 6-digit code (valid for 5 minutes, single-use, hashed). In `dev` profile, the code is printed to the backend console logs:
  ```
  =================================================
  [DEV OTP CONSOLE] Contact: +919876543210 | Code: 492018
  =================================================
  ```
- **Swagger UI** is available at: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON** is available at: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 5. Running the Vite Frontend (Dual Mode)

Navigate to the frontend directory `theFaleOne-Frontend\FALE-healthcare-ui`:

### A. Dynamic API Mode (`VITE_USE_API=true`)
In `FALE-healthcare-ui\.env`:
```env
VITE_USE_API=true
VITE_API_URL=http://localhost:8080/api/v1
```
Or in PowerShell:
```powershell
$env:VITE_USE_API="true"
npm run dev
```
In API mode:
- No static or mock data from `data.ts` or `store.ts` is shown on any screen.
- All persisted mock data in `localStorage` (`fale-pro-v2`) is automatically cleared.
- All data is fetched live from the backend REST API with loading spinners, empty states, and error handling.

### B. Static Mock Mode (`VITE_USE_API=false`)
```env
VITE_USE_API=false
```
In mock mode:
- The app operates offline with rich realistic mock data stored in `localStorage` and `src/lib/data.ts`.

---

## 6. End-to-End Walkthrough: Creating Facility & Candidate from Scratch (UI)

### Step 1: Register and Onboard a Healthcare Facility
1. Open [http://localhost:5173](http://localhost:5173).
2. Toggle portal gateway switch to **"Healthcare Facility & Hiring"**.
3. Enter your facility mobile number (e.g. `9876543210`) and click **Send verification code**.
4. Check the backend terminal for the random 6-digit OTP in `[DEV OTP CONSOLE]` and enter it.
5. You are redirected to `/onboarding?role=employer`:
   - Enter Hospital Name, Facility Type, Address, Bed Counts, License Number, and NABH Accreditation.
   - Submit onboarding.
6. Navigate to **Wallet & Escrow** (`/employer/wallet`) &rarr; Top-up wallet escrow balance and buy candidate contact unlock credits.
7. Post a new Job requirement (`/employer/post`) or On-demand Locum Shift (`/employer/post-shift`).

### Step 2: Approve Postings via Compliance Admin
1. If initial admin environment variables were set, log in as Admin.
2. In the Admin Console (`/admin`):
   - Review and verify the pending Hospital Facility registration under **Accreditation**.
   - Review and approve the pending Job Postings and Locum Shifts under **Postings**.
   - Once approved, the requirements become active and visible across the network.

### Step 3: Register and Onboard a Candidate (Student or Graduate)
1. Open an incognito browser window or sign out.
2. Toggle to **"Healthcare Professional & Student"**.
3. Enter candidate mobile number (e.g. `9876543211`) and verify via console OTP.
4. Complete candidate onboarding (`/onboarding?role=candidate`):
   - **Student Candidate**: Select course, college, year of study, expected graduation year, and bonafide document. Student is granted access to internships, CME learning, and Huddle mentorship.
   - **Graduate Doctor / Nurse**: Select profession, council, registration number, qualifications, and experience.
5. Browse live jobs (`/jobs`) and locum shifts (`/shifts`), apply or claim, and track status in real time (`/applications`, `/earnings`).

---

## 7. Production Security Guardrails
1. **Refusal of Dev OTP in Production**: The backend strictly refuses to start if `fale.auth.dev-otp-enabled=true` under `@Profile("prod")`.
2. **Refusal of Non-Standard JWT Lifetime in Production**: If `fale.security.jwt.access-token-expiration-ms != 900000` (15 minutes), startup aborts with a fatal security error under `@Profile("prod")`.
3. **No Default Admin Passwords**: Default passwords are eradicated from code. Admin bootstrap is strictly environment-driven with mandatory password hashing.
4. **Rate Limiting & PHI Filtering**: Strict token bucket rate limiting on sensitive endpoints and automated PHI sanitization on clinical case discussions.
