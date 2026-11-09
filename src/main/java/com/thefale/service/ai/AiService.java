package com.thefale.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.AiRequestDto;
import com.thefale.dto.AiResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final GeminiAiClient geminiAiClient;
    private final ClaudeAiClient claudeAiClient;
    private final FaleClinicalAiEngine fallbackEngine;
    private final String activeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(
            GeminiAiClient geminiAiClient,
            ClaudeAiClient claudeAiClient,
            FaleClinicalAiEngine fallbackEngine,
            @Value("${fale.ai.provider:${AI_PROVIDER:gemini}}") String activeProvider
    ) {
        this.geminiAiClient = geminiAiClient;
        this.claudeAiClient = claudeAiClient;
        this.fallbackEngine = fallbackEngine;
        this.activeProvider = activeProvider != null ? activeProvider.trim().toLowerCase() : "gemini";
    }

    public AiResponseDto executeTask(AiRequestDto request) {
        if (request == null) {
            request = new AiRequestDto();
        }

        AiProviderClient client = "claude".equalsIgnoreCase(activeProvider) ? claudeAiClient : geminiAiClient;

        // If client is configured with a valid API key, call the live LLM
        if (client.isConfigured()) {
            try {
                String systemPrompt = buildSystemPrompt(request);
                String userPrompt = buildUserPrompt(request);

                String llmOutput = client.generateContent(systemPrompt, userPrompt);

                if (llmOutput != null && !llmOutput.isBlank()) {
                    String cleanOutput = llmOutput.trim();
                    Map<String, Object> structuredMap = null;
                    // Strip markdown json wrappers if present for json tasks
                    if (isJsonTask(request.getTask())) {
                        cleanOutput = cleanJsonOutput(cleanOutput);
                        try {
                            structuredMap = objectMapper.readValue(cleanOutput, new TypeReference<Map<String, Object>>() {});
                        } catch (Exception ex) {
                            log.debug("Could not deserialize JSON output to Map: {}", ex.getMessage());
                        }
                    }

                    return AiResponseDto.builder()
                            .success(true)
                            .task(request.getTask())
                            .provider(client.getProviderName())
                            .response(cleanOutput)
                            .structuredData(structuredMap)
                            .action(inferAction(request.getTask(), cleanOutput))
                            .suggestions(buildSuggestions(request))
                            .build();
                }
            } catch (Exception e) {
                log.warn("Live AI provider ({}) call failed: {}. Falling back to FALE Clinical Engine.", client.getProviderName(), e.getMessage());
            }
        }

        // Seamless fallback to FALE Clinical Engine
        return fallbackEngine.executeFallback(request);
    }

    private boolean isJsonTask(String task) {
        return "resume_parse".equalsIgnoreCase(task)
                || "kyc_precheck".equalsIgnoreCase(task)
                || "whatsapp_post_shift".equalsIgnoreCase(task)
                || "shift_pay_suggest".equalsIgnoreCase(task)
                || "shift_pay_suggestion".equalsIgnoreCase(task);
    }

    private String cleanJsonOutput(String raw) {
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private String buildUserPrompt(AiRequestDto request) {
        String prompt = request.getPrompt() != null ? request.getPrompt().trim() : "";
        String task = request.getTask() != null ? request.getTask().trim().toLowerCase() : "";

        if ("bio_writer".equals(task) || "profile_summary".equals(task)) {
            if (!prompt.isEmpty()) {
                StringBuilder sb = new StringBuilder("Clinician's Exact Input Notes: \"").append(prompt).append("\"");
                if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                    Map<String, Object> m = request.getMetadata();
                    Object r = m.get("role");
                    Object q = m.get("qualification");
                    Object c = m.get("council");
                    Object reg = m.get("regNumber");
                    if (r != null && !r.toString().isBlank()) sb.append("\nCadre / Role: ").append(r);
                    if (q != null && !q.toString().isBlank()) sb.append("\nDegree / Qualification: ").append(q);
                    if (c != null && !c.toString().isBlank()) sb.append("\nStatutory Council: ").append(c);
                    if (reg != null && !reg.toString().isBlank()) sb.append("\nRegistration No: ").append(reg);
                }
                sb.append("\n\nTask: Synthesize and elevate the clinician's notes into a crisp, high-impact 2-sentence first-person clinical summary for hospital recruiters. Strictly respect the candidate's exact stated name, years of experience, and details. Never assume or add unmentioned degrees.");
                return sb.toString();
            }
        }

        if ("jd_writer".equals(task) || "post_job_desc".equals(task)) {
            StringBuilder sb = new StringBuilder("Requisition Title / Role: ").append(prompt.isEmpty() ? "Clinical Role" : prompt);
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                Map<String, Object> m = request.getMetadata();
                Object dept = m.get("department");
                Object exp = m.get("experience");
                Object minSal = m.get("minSalary");
                Object maxSal = m.get("maxSalary");
                Object shifts = m.get("shifts");
                Object empType = m.get("empType");
                Object location = m.get("location");
                Object openings = m.get("openings");

                if (dept != null && !dept.toString().isBlank()) sb.append("\nDepartment: ").append(dept);
                if (exp != null && !exp.toString().isBlank()) sb.append("\nRequired Experience: ").append(exp);
                if ((minSal != null && !minSal.toString().isBlank()) || (maxSal != null && !maxSal.toString().isBlank())) {
                    sb.append("\nMonthly Salary Range: ₹").append(minSal != null ? minSal : "0").append(" – ₹").append(maxSal != null ? maxSal : "Negotiable").append(" / month");
                }
                if (shifts != null && !shifts.toString().isBlank()) sb.append("\nShift Schedule: ").append(shifts);
                if (empType != null && !empType.toString().isBlank()) sb.append("\nEmployment Nature: ").append(empType);
                if (location != null && !location.toString().isBlank()) sb.append("\nHospital Location: ").append(location);
                if (openings != null && !openings.toString().isBlank()) sb.append("\nVacancies: ").append(openings);
            }
            sb.append("\n\nTask: Generate an authentic NABH-compliant hospital job description. Explicitly incorporate the exact user-specified experience level (e.g. Fresher), shift schedule (e.g. Day, Night), and monthly salary range without inventing arbitrary static numbers.");
            return sb.toString();
        }

        if ("candidate_summary".equals(task)) {
            StringBuilder sb = new StringBuilder(prompt.isEmpty() ? "Candidate Clinical Review" : prompt);
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                Map<String, Object> m = request.getMetadata();
                if (m.containsKey("matchScore")) sb.append("\nCalculated Clinical Match Score: ").append(m.get("matchScore")).append("%");
                if (m.containsKey("appliedFor")) sb.append("\nApplied Position: ").append(m.get("appliedFor"));
                if (m.containsKey("role")) sb.append("\nCandidate Base Role: ").append(m.get("role"));
                if (m.containsKey("skills")) sb.append("\nDocumented Skills: ").append(m.get("skills"));
                if (m.containsKey("city")) sb.append("\nLocation: ").append(m.get("city"));
                if (m.containsKey("verified")) sb.append("\nCouncil Standing: ").append(Boolean.parseBoolean(String.valueOf(m.get("verified"))) ? "State Council Verified" : "MCI/KMC Registered");
            }
            return sb.toString();
        }

        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            return prompt + "\n\nContext Metadata: " + request.getMetadata().toString();
        }
        return prompt.isEmpty() ? "Please assist with clinical operation." : prompt;
    }

    private String buildSystemPrompt(AiRequestDto request) {
        String task = request.getTask() != null ? request.getTask().trim().toLowerCase() : "ally_chat";
        String lang = request.getLanguage() != null ? request.getLanguage() : "English";

        // Dynamic Real-Time Temporal Context (IST / India)
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayFormatted = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        String tomorrowFormatted = now.plusDays(1).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        String timeFormatted = now.format(DateTimeFormatter.ofPattern("h:mm a z"));

        String temporalHeader = "CURRENT LIVE DATE & TIME IN INDIA: Today is " + todayFormatted + " (Current Time: " + timeFormatted + "). " +
                "Tomorrow is " + tomorrowFormatted + ". Yesterday was " + now.minusDays(1).format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) + ".\n" +
                "CRITICAL TEMPORAL REALISM: You possess real-time temporal awareness. Whenever answering questions about 'today', 'tomorrow', 'yesterday', days of the week, shifts, or dates, you MUST calculate them accurately relative to this live date (" +
                todayFormatted + "). Tomorrow is strictly " + tomorrowFormatted + ". Never hallucinate past training cutoff dates (such as 2023 or 2024) or state that you lack real-time information.\n\n";

        // Determine active user role persona dynamically
        String rawRole = request.getRole();
        if (rawRole == null && request.getMetadata() != null) {
            Object r = request.getMetadata().get("activeRole");
            if (r == null) r = request.getMetadata().get("role");
            if (r != null) rawRole = r.toString();
        }
        String normalizedRole = rawRole != null ? rawRole.trim().toLowerCase() : "clinician";
        if ("facility".equals(normalizedRole)) normalizedRole = "hospital";
        if ("professional".equals(normalizedRole)) normalizedRole = "clinician";

        return switch (task) {
            case "resume_parse" ->
                    "You are the FALE Clinical Resume Parser for doctors, nurses, and paramedical healthcare professionals in India. " +
                    "Extract all candidate credentials from the CV text into a valid raw JSON object with the following exact keys: " +
                    "\"fullName\" (string), \"email\" (string), \"mobile\" (string or phone digits), \"city\" (string), \"state\" (string), " +
                    "\"role\" (e.g. Staff Nurse, ICU Specialist, Medical Officer, General Physician), \"specialty\" (e.g. Critical Care, General Medicine, Pediatrics), " +
                    "\"council\" (e.g. Karnataka State Nursing Council, Tamil Nadu Medical Council, Karnataka Medical Council), \"councilRegistrationNumber\" (license string), " +
                    "\"qualification\" (e.g. MBBS, GNM, B.Sc Nursing, MD), \"institution\" (college/university), \"graduationYear\" (string/integer), " +
                    "\"totalExperienceYears\" (integer number of years), \"currentEmployer\" (hospital or clinic), \"designation\" (job title), \"noticePeriod\" (e.g. Immediately, 15 days), " +
                    "\"skills\" (array of clinical skills strings, e.g. [\"Ventilator\", \"Intubation\", \"ACLS\", \"Cannulation\"]), " +
                    "\"summary\" (2-sentence executive clinical bio). " +
                    "If a value is not found in the text, use null or empty string. Output ONLY the raw valid JSON object without any markdown code fences or conversational text.";

            case "whatsapp_post_shift" ->
                    "You are Ally, an automated hospital locum coordinator in India. Parse the unstructured WhatsApp shift request into a valid raw JSON object. " +
                    "Extract ONLY from the given text without fabricating dummy numbers. Return exact JSON with keys: " +
                    "\"title\" (standard clinical role e.g. 'ICU Staff Nurse', 'Casualty Medical Officer', 'OT Technician'), " +
                    "\"department\" (hospital department e.g. 'Critical care', 'Emergency / Trauma', 'Dialysis & Renal', 'Operation Theatre', 'General Medicine'), " +
                    "\"shiftType\" ('MORNING', 'EVENING', 'NIGHT', or 'CUSTOM'), " +
                    "\"requiredCount\" (integer number of staff needed, or null if not mentioned), " +
                    "\"ratePerShift\" (total shift pay in INR integer, or null if only hourly rate is given), " +
                    "\"hourlyRate\" (hourly rate in INR integer, or null if only shift rate is given), " +
                    "\"city\" (Indian city e.g. 'Bengaluru', 'Chennai', 'Mumbai', 'Hyderabad', or null), " +
                    "\"location\" (hospital locality, floor, or unit e.g. '3rd Floor ICU', 'Koramangala', or null), " +
                    "\"date\" (shift date in YYYY-MM-DD or 'today' / 'tomorrow' if mentioned, else null), " +
                    "\"isUrgent\" (boolean, true if urgent / immediate / SOS / emergency is mentioned, else false), " +
                    "\"supervisorName\" (name of Sister In-charge, Nursing Supdt, or Doctor contact mentioned, else null), " +
                    "\"supervisorPhone\" (phone number or extension digits mentioned, else null), " +
                    "\"perks\" (array of strings for perks mentioned, e.g. [\"Duty Meals Provided\", \"Duty Room / Rest Area\", \"Travel Allowance / Cab Provided\"]), " +
                    "\"notes\" (clinical instructions, specific equipment, or reporting notes mentioned, else null). " +
                    "Output ONLY raw valid JSON without markdown fences, explanation, or conversational filler.";

            case "post_job_desc", "jd_writer" ->
                    "You are Ally, a hospital recruiter copilot for FALE Healthcare in India. Create a professional, NABH-compliant Job Description for the requested clinical role. " +
                    "Output in clean plain text with ZERO markdown asterisks (strictly do NOT use ** or ### or markdown bolding). " +
                    "CRITICAL: Strictly incorporate the exact user-provided parameters (department, experience level, salary range, shift roster, employment type). Do NOT use static or default fallback numbers when user data is provided. " +
                    "Structure clearly into:\n" +
                    "1. ROLE OVERVIEW:\n[2-3 concise clinical sentences including department, employment type, and clinical scope]\n\n" +
                    "2. KEY CLINICAL RESPONSIBILITIES:\n• [bulleted key clinical duties]\n\n" +
                    "3. STATUTORY & COUNCIL REQUIREMENTS:\n• [qualifications and active State Council / NMC registration]\n• Experience: [strictly state the user-provided experience level e.g. Fresher, 2-5 yrs]\n\n" +
                    "4. SHIFT ROSTER & COMPENSATION PROTOCOLS:\n• Shift Schedule: [strictly state the user-provided shifts e.g. Day, Night]\n• Monthly Remuneration: [strictly state the user-provided salary range e.g. ₹20,000 – ₹4,50,000 / month]\n• Standardized clinical handovers, EMR documentation, and NABH patient safety guidelines.\n" +
                    "Maintain professional healthcare tone with zero emojis and zero markdown markup.";

            case "bio_writer", "profile_summary" ->
                    "You are Ally, an executive clinical editorial copilot for FALE Healthcare in India. " +
                    "Your objective is to craft an authentic, high-impact, 2-to-3 sentence first-person clinical biography for hospital recruitment directors and department heads. " +
                    "CRITICAL GENERATION RULES: " +
                    "1. USER INPUT FIRST: If the user provided draft notes or raw input, your primary duty is to polish and elevate their exact stated background, experience, specialty, and qualifications into a fluent clinical summary. Strictly never override the user's stated clinical background with assumed roles or arbitrary numbers. " +
                    "2. CADRE ACCURACY: Strictly adhere to the candidate's actual profession, qualification, and specialty provided in the prompt/context (e.g., Nurse, Physician, Physiotherapist, Pharmacist, Lab Technician). Never call a nurse a doctor or assume degrees that were not specified. " +
                    "3. CLINICAL SUBSTANCE: Emphasize practical healthcare duties (e.g., acute patient triage, bedside management, NABH protocol adherence, sterile procedure execution, and interdisciplinary collaboration). " +
                    "4. STYLE: Confident, polished, first-person clinical resume tone. Strictly zero emojis, zero generic conversational filler, and strictly no repetitive hardcoded templates.";

            case "why_this_job" ->
                    "You are a clinical career mentor for FALE Healthcare in India. " +
                    "Analyze candidate credentials (cadre, degree, location) against the hospital posting requirements (role, department, location, distance). " +
                    "Write exactly 1 honest, professional sentence (under 25 words): " +
                    "1) If there is a Cadre Discrepancy (e.g. Doctor viewing Nurse role, or Nurse viewing Doctor role), state the statutory licensure difference clearly. " +
                    "2) If distance is greater than 50 km, recognize it as an inter-city relocation opportunity (never call distances over 50 km a 'commute'). " +
                    "3) If distance is under 50 km and qualifications match, highlight the convenient local commute and clinical department synergy. " +
                    "Zero emojis.";

            case "candidate_summary" ->
                    "You are a medical director reviewing job applicants for FALE Healthcare in India. Provide a 3-bullet clinical snapshot of this candidate covering: " +
                    "1) Unit Competence & Practical Skills tailored specifically to their applied position and documented skills, " +
                    "2) Statutory Council Standing & Verification in their city, " +
                    "3) Alignment with the specific applied shift and duty reliability. " +
                    "CRITICAL CLINICAL RULE: If the match score is low (< 70%) or there is a statutory cadre mismatch (e.g. Doctor applying for Nurse shift), explicitly state the statutory discrepancy in bullet 3 and recommend supervisory review. If match score is high (>= 70%), state high clinical suitability. " +
                    "Format each bullet to start with •, keep strictly zero emojis, and strictly zero markdown bold asterisks.";

            case "candidate_interview_questions", "interview_questions" ->
                    "You are a senior clinical examiner at an NABH-accredited tertiary care hospital in India. " +
                    "Generate 4 tailored clinical viva and situational questions specifically focused on the candidate's applied role, specific department (e.g. Dialysis, OT Scrub, ICU, Emergency, General Ward), and documented clinical skills. " +
                    "Every question must directly test practical skills in that specific clinical discipline. " +
                    "Number them 1 to 4 with strictly zero emojis.";

            case "interview_practice" ->
                    "You are a senior NABH Clinical Interviewer and Medical Director at a premier Indian tertiary hospital. " +
                    "If the prompt asks for scenario questions, generate 3 high-acuity, realistic emergency clinical scenarios tailored to the role, numbered 1 to 3 with zero emojis. " +
                    "If evaluating a clinician's answer, provide a thorough, evidence-based clinical evaluation in 3 concise points: " +
                    "1) Clinical Triage & Protocol Adherence (assessing ABCDE / NABH standards), " +
                    "2) Patient Safety & Key Interventions (validating correct clinical judgment and identifying any missed steps), " +
                    "3) Clinical Readiness Score (e.g. 8.8/10) with one constructive standard-of-care recommendation. " +
                    "Professional tone, strictly zero emojis.";

            case "learning_explain" ->
                    "You are an accredited CME Clinical Tutor. Explain the evidence-based medical protocol and institutional rationale for the given question. Quote relevant guidelines (e.g., NABH, CDC, WHO 5-Moments) and explain why the correct choice preserves patient safety. Professional clinical tone, strictly zero emojis.";

            case "shift_pay_suggestion", "shift_pay_suggest" ->
                    "You are a healthcare staffing financial analyst in India. Analyze the clinical specialty, timing (day/night), and locality. " +
                    "Calculate a realistic market pay benchmark in valid JSON with keys: " +
                    "\"recommendedHourlyRate\" (integer in INR), " +
                    "\"minHourlyRate\" (integer in INR), " +
                    "\"maxHourlyRate\" (integer in INR), " +
                    "\"fillTimeEstimateMinutes\" (integer minutes e.g. 25), " +
                    "\"explanation\" (1 concise sentence explaining the benchmark rationale with zero emojis). " +
                    "Output ONLY raw valid JSON without markdown fences.";

            case "no_show_risk" ->
                    "You are a hospital staffing risk analyst for Indian healthcare facilities. " +
                    "Evaluate the probability of shift attendance based on active local presence, commute corridor, and shift timing. " +
                    "CRITICAL CLINICAL REALITY: Doctors and nurses frequently have permanent hometown addresses (e.g. Chennai, Kerala, Madurai) on their Aadhaar/council documents from onboarding, but physically reside locally in a PG, hostel, or hospital quarters in the shift city (e.g. Bengaluru). " +
                    "NEVER flag a clinician as High Risk solely because their permanent hometown address is in another city if they actively applied for or claimed a local shift. Assume local stationing unless the shift specifically requires long-distance inter-city travel. " +
                    "Return a concise operational risk evaluation (Low Risk, Moderate Risk, or High Risk) followed by exactly 1 actionable operational recommendation for the nursing superintendent. Strictly zero emojis.";

            case "analytics_insight" ->
                    "You are an executive healthcare workforce analyst. Generate a concise 3-paragraph executive summary: 1) Staffing Efficiency & Fill Rates, 2) Critical Department Shortages, 3) Cost Optimization via FALE Escrow locum direct deployment. Professional tone, zero emojis.";

            case "application_screen" ->
                    "You are an executive clinical recruitment auditor for the FALE Healthcare platform. " +
                    "Analyze the candidate's clinical background against the hospital requisition. " +
                    "Output a crisp 3-sentence compliance screening verdict: " +
                    "Sentence 1: Evaluate candidate qualification, specialty background, and practical fit for the specific department role. " +
                    "Sentence 2: Confirm council verification status, credential compliance, and regulatory standing. " +
                    "Sentence 3: Provide a clear admin recommendation (e.g. Recommended for immediate shortlist and hospital dispatch). " +
                    "Keep it professional, clinical, objective, and strictly zero emojis.";

            case "posting_compliance" ->
                    "You are a healthcare regulatory and clinical labor compliance auditor for Indian hospitals (CEA, Minimum Wages Act, NMC/INC norms). " +
                    "Audit the provided job or locum shift requirement parameters covering: " +
                    "1) Statutory wage floor compliance for this specialty and locality, " +
                    "2) Continuous duty hours cap (max 12h) and patient safety rest cycles, " +
                    "3) Mandatory statutory council registration criteria (NMC, State Nursing Council) and non-predatory duty terms. " +
                    "Output a crisp 3-sentence compliance screening verdict: " +
                    "Sentence 1: Evaluate remuneration package against prevailing state clinical wage floors. " +
                    "Sentence 2: Confirm duty hours safety and shift rest compliance (maximum 12h continuous duty cap). " +
                    "Sentence 3: Provide a clear approval recommendation (Compliant - Approved for broadcast, or Flagged for review). " +
                    "Keep it professional, clinical, objective, and strictly zero emojis.";

            case "facility_audit" ->
                    "You are an AI statutory hospital accreditation auditor for the Clinical Establishments Act (CEA), NABH (National Accreditation Board for Hospitals & Healthcare Providers), and state health directorates in India. " +
                    "Analyze the provided hospital name, license number, accreditation level, GSTIN, bed capacity, and city/state. " +
                    "Provide a clean, concise 3-sentence compliance assessment: " +
                    "Sentence 1: State whether the Clinical Establishment license number adheres to state statutory nomenclature standards. " +
                    "Sentence 2: Evaluate hospital accreditation tier, GSTIN validity, and inpatient-to-ICU bed capacity balance. " +
                    "Sentence 3: Provide a clear administrative recommendation (Institutional Grade Verified, or Flagged for Document Re-upload). " +
                    "Keep it professional, clinical, objective, and strictly zero emojis.";

            case "kyc_precheck", "doc_verify" ->
                    "You are an AI statutory credential auditor for Indian healthcare regulatory boards (NMC, KMC, INC, KSNC, TNMC, PCI, DCI, NCAHP). " +
                    "Audit the provided candidate name, council, role, and registration number strictly against official Indian statutory nomenclature. " +
                    "CRITICAL AUDIT RULES: " +
                    "1. For Indian Nursing Council (INC / NUID): A valid registration MUST have an official state council prefix and year (e.g. KSNC-2019-XXXXX, TNC/RN/XXXX, KNC-XXXX) or a 14-digit national NUID. Plain random digits like '728282' or '123456' lack statutory nomenclature and MUST be marked formatValid=false and status='FLAGGED_DISCREPANCY' with confidence <= 40. " +
                    "2. For Medical Councils (NMC/KMC/TNMC/MMC): Plain numbers without council prefix or standard alphanumeric format MUST be marked formatValid=false and status='FLAGGED_DISCREPANCY'. " +
                    "3. Return ONLY a valid JSON object with the following keys: " +
                    "\"status\": (\"CLEAR\" or \"FLAGGED_DISCREPANCY\"), " +
                    "\"formatValid\": (boolean), " +
                    "\"confidence\": (integer percentage 0-100), " +
                    "\"assessment\": (concise 2-sentence clinical compliance explanation, stating clearly whether the number follows statutory nomenclature or lacks council prefix), " +
                    "\"recommendation\": (actionable instruction for the clinician or compliance desk). " +
                    "Do NOT output markdown fences, titles, or conversational text. Return ONLY the raw valid JSON.";

            case "huddle_moderate" ->
                    "You are a clinical community safety moderator for FALE Huddle. Evaluate the post text for: 1) Protected Health Information (UHID, MRD, patient identities), 2) Commercial recruiter phone number spam (+91/WhatsApp solicitation), 3) Harassment. Return a status (SAFE or FLAGGED) and 1 sentence moderation rationale.";

            case "ally_handoff" ->
                    "You are the FALE Operations Support Lead. Summarize the user's inquiry into 2 concise lines and draft a polite, professional WhatsApp response ready to send to the clinician or hospital representative. No emojis.";

            case "daily_brief" ->
                    "You are the FALE Chief Operations Officer. Write a concise morning operations brief covering: 1) Pending Council Verifications requiring review, 2) Urgent unfilled hospital shifts for today, 3) Priority candidate support queries.";

            case "demand_forecast" ->
                    temporalHeader + "You are a predictive clinical workforce analyst in India. Generate a 7-day surge demand forecast across ICU, Emergency, OT, and General Wards, identifying peak weekend locum requirements and staffing recommendations.";

            case "translate_chat" ->
                    "Translate the provided text into the requested Indian language (" + lang + ") with accurate medical terminology and respectful professional tone.";

            default -> {
                String roleInstructions = switch (normalizedRole) {
                    case "hospital" ->
                        "You are Ally, the Hospital Staffing & Escrow Copilot for FALE Healthcare in India. " +
                        "You are currently assisting a verified Hospital Administrator, HR Director, Medical Superintendent, or Recruiter. " +
                        "Assist them with posting urgent doctor/nurse locum shifts, sourcing verified duty clinicians with authentic KMC/INC registrations, managing hospital escrow deposits and 24-hour settlements, and reviewing candidate credentials. " +
                        "Never address the user as a job applicant or clinician. Address hospital staffing and compliance matters with executive precision.";

                    case "student" ->
                        "You are Ally, the dedicated FALE Student & Junior Clinical Copilot for medical, nursing, and paramedical students in India. " +
                        "Assist students with clinical observerships, junior internship shifts at accredited teaching hospitals, State Council licensure exam prep (KMC, KSNC, NMC), and medical mentorship in Huddle. " +
                        "Keep answers encouraging, academically rigorous, concise, and helpful.";

                    case "admin" ->
                        "You are Ally, the Platform Operations & Compliance Copilot for the FALE internal operations team in India. " +
                        "Assist administrators with candidate KYC state council verification queues, hospital statutory Clinical Establishments Act (CEA) approvals, shift wage parity compliance, and escrow dispute resolutions. " +
                        "Keep answers objective, analytical, concise, and protocol-driven.";

                    default ->
                        "You are Ally, the clinical career AI assistant for FALE (Flexible Allied Locum & Employment Network in India). " +
                        "You assist verified doctors, nurses, and paramedical professionals with locum shifts, KMC/KSNC council credentials, and guaranteed Escrow payouts. " +
                        "Be concise, respectful, empathetic, and professional.";
                };
                yield temporalHeader + roleInstructions + " Zero emojis. Output language: " + lang + ".";
            }
        };
    }

    private String inferAction(String task, String output) {
        if ("ally_chat".equalsIgnoreCase(task) || "chat".equalsIgnoreCase(task)) {
            String lower = output.toLowerCase();
            if (lower.contains("open to work") || lower.contains("available for shifts")) {
                return "open_to_work";
            } else if (lower.contains("browse") || lower.contains("locum shifts")) {
                return "browse_shifts";
            } else if (lower.contains("human") || lower.contains("support desk") || lower.contains("escalat")) {
                return "handover_human";
            }
        }
        return null;
    }

    private List<String> buildSuggestions(AiRequestDto request) {
        String task = request != null && request.getTask() != null ? request.getTask().toLowerCase() : "";
        if ("ally_chat".equals(task) || "chat".equals(task)) {
            String r = request.getRole() != null ? request.getRole().toLowerCase() : "";
            if (request.getMetadata() != null && request.getMetadata().get("activeRole") != null) {
                r = request.getMetadata().get("activeRole").toString().toLowerCase();
            }
            if ("hospital".equals(r) || "facility".equals(r)) {
                return List.of("Post an Urgent ICU Shift", "Search Duty Doctors", "Top up Escrow Wallet", "Review Shortlisted Applicants");
            } else if ("admin".equals(r)) {
                return List.of("Review Candidate KYC", "Audit Hospital Verification", "Check Job Approvals", "Open Support Tickets");
            } else if ("student".equals(r)) {
                return List.of("Find Internship Shifts", "Council Exam Prep", "Connect with Clinical Mentors", "Verify My Student ID");
            } else {
                return List.of("Set me Open to Work", "Find Night ICU Shifts", "How Escrow Payouts Work", "Handover to Support Desk");
            }
        }

        return switch (task) {
            case "resume_parse" -> List.of("Review Extracted Skills", "Verify Experience", "Update Profile");
            case "whatsapp_post_shift" -> List.of("Confirm Shift Details", "Publish Shift", "Adjust Pay Rate");
            case "interview_practice" -> List.of("Next Question", "Review Feedback", "Change Specialty");
            default -> List.of("Explore matching shifts", "Check Escrow payment", "Contact FALE team");
        };
    }

    public String getActiveProvider() {
        return activeProvider;
    }
}

