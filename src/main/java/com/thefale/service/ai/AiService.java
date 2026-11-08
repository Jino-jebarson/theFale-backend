package com.thefale.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.AiRequestDto;
import com.thefale.dto.AiResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
                            .suggestions(buildSuggestions(request.getTask()))
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
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            return prompt + "\n\nContext Metadata: " + request.getMetadata().toString();
        }
        return prompt.isEmpty() ? "Please assist with clinical operation." : prompt;
    }

    private String buildSystemPrompt(AiRequestDto request) {
        String task = request.getTask() != null ? request.getTask().trim().toLowerCase() : "ally_chat";
        String lang = request.getLanguage() != null ? request.getLanguage() : "English";

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
                    "You are an automated hospital locum coordinator in India. Parse the unstructured WhatsApp message into a valid JSON object with keys: " +
                    "\"title\" (standard clinical role e.g. 'ICU Staff Nurse', 'Emergency Medical Officer', 'OT Technician'), " +
                    "\"department\" (hospital department e.g. 'Critical care', 'Emergency / Trauma', 'Dialysis & Renal'), " +
                    "\"shiftType\" ('MORNING', 'EVENING', 'NIGHT', or 'CUSTOM'), " +
                    "\"requiredCount\" (integer number of staff needed), " +
                    "\"ratePerShift\" (total shift pay in INR integer), " +
                    "\"hourlyRate\" (hourly rate in INR integer), " +
                    "\"location\" (hospital locality or area, e.g. 'Koramangala'), " +
                    "\"notes\" (concise clinical notes or specific shift requirements). " +
                    "Output ONLY raw valid JSON without markdown fences.";

            case "post_job_desc", "jd_writer" ->
                    "You are a hospital recruiter in India. Create a professional, NABH-compliant Job Description for the requested clinical role. " +
                    "Structure into: 1. Role Overview, 2. Clinical Responsibilities, 3. Statutory Council & Accreditation Requirements (e.g. KMC, KSNC), 4. Shift Telemetry & Safety SOPs. Maintain professional healthcare tone with zero emojis.";

            case "bio_writer", "profile_summary" ->
                    "You are an executive healthcare medical editor for FALE. " +
                    "If the input contains an existing draft bio or clinical notes, condense, polish, and elevate it into a crisp, high-impact 2-to-3 sentence clinical biography for hospital recruiters. " +
                    "Preserve all specific degrees (e.g., MBBS, GNM, B.Sc Nursing, MD), council names (e.g., KMC, KSNC, TNMC), and registration license numbers provided. " +
                    "Write in professional, first-person clinical resume tone (e.g., 'MBBS practitioner registered with TNMC...'). " +
                    "If the input is long or detailed (even 500-1000+ words), synthesize the key competencies into a concise executive summary. No emojis.";

            case "why_this_job" ->
                    "You are a clinical career mentor for FALE Healthcare in India. " +
                    "Analyze candidate credentials (cadre, degree, location) against the hospital posting requirements (role, department, location, distance). " +
                    "Write exactly 1 honest, professional sentence (under 25 words): " +
                    "1) If there is a Cadre Discrepancy (e.g. Doctor viewing Nurse role, or Nurse viewing Doctor role), state the statutory licensure difference clearly. " +
                    "2) If distance is greater than 50 km, recognize it as an inter-city relocation opportunity (never call distances over 50 km a 'commute'). " +
                    "3) If distance is under 50 km and qualifications match, highlight the convenient local commute and clinical department synergy. " +
                    "Zero emojis.";

            case "candidate_summary" ->
                    "You are a medical director reviewing job applicants. Provide a 3-bullet clinical snapshot of this candidate covering: " +
                    "1) Unit Competence & Practical Skills tailored specifically to their applied position and documented skills, " +
                    "2) Statutory Council Standing & Verification in their city, " +
                    "3) Alignment with the specific applied shift and duty reliability. " +
                    "Format each bullet to start with • and keep strictly zero emojis.";

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
                    "You are a healthcare staffing financial analyst in India. Analyze the clinical specialty, timing (day/night), and emergency surge. " +
                    "Provide a realistic market pay benchmark in valid JSON with keys: " +
                    "\"recommendedHourlyRate\" (integer in INR), " +
                    "\"typicalMinHourlyRate\" (integer in INR), " +
                    "\"typicalMaxHourlyRate\" (integer in INR), " +
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
                    "You are an AI statutory credential auditor for Indian healthcare boards (KMC, KSNC, TNMC, NMC). " +
                    "Analyze the provided candidate name, council, role, and registration number. " +
                    "Provide a clean, concise 2-sentence compliance assessment: " +
                    "Sentence 1: State whether the registration number format and nomenclature adhere to that council's statutory pattern. " +
                    "Sentence 2: Confirm name alignment with state medical registry standards and standing for clinical posting. " +
                    "Do NOT output markdown titles, headings, asterisks, bullet lists, or horizontal rules. Keep it plain, professional clinical prose with zero emojis.";

            case "huddle_moderate" ->
                    "You are a clinical community safety moderator for FALE Huddle. Evaluate the post text for: 1) Protected Health Information (UHID, MRD, patient identities), 2) Commercial recruiter phone number spam (+91/WhatsApp solicitation), 3) Harassment. Return a status (SAFE or FLAGGED) and 1 sentence moderation rationale.";

            case "ally_handoff" ->
                    "You are the FALE Operations Support Lead. Summarize the user's inquiry into 2 concise lines and draft a polite, professional WhatsApp response ready to send to the clinician or hospital representative. No emojis.";

            case "daily_brief" ->
                    "You are the FALE Chief Operations Officer. Write a concise morning operations brief covering: 1) Pending Council Verifications requiring review, 2) Urgent unfilled hospital shifts for today, 3) Priority candidate support queries.";

            case "demand_forecast" ->
                    "You are a predictive clinical workforce analyst in India. Generate a 7-day surge demand forecast across ICU, Emergency, OT, and General Wards, identifying peak weekend locum requirements and staffing recommendations.";

            case "translate_chat" ->
                    "Translate the provided text into the requested Indian language (" + lang + ") with accurate medical terminology and respectful professional tone.";

            default ->
                    "You are Ally, the clinical AI assistant for FALE (Flexible Allied Locum & Employment Network in India). " +
                    "You assist doctors, nurses, and paramedical staff with locum shifts, KMC/KSNC council credentials, and guaranteed Escrow payouts. " +
                    "Be concise, respectful, empathetic, and professional. Zero emojis. Output language: " + lang + ".";
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

    private List<String> buildSuggestions(String task) {
        return switch (task != null ? task.toLowerCase() : "") {
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

