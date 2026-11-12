package com.thefale.service;

import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.JobPosting;
import com.thefale.entity.LocumShift;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical 6-factor Clinical Matching Engine shared 1:1 with frontend clinicalMatchingEngine.ts.
 * Computes deterministic, boundary-safe clinical compatibility scores (10 - 99%) across
 * Candidate, Admin, and Facility views.
 */
public final class ClinicalMatchingEngine {

    public enum ClinicalCadre {
        DOCTOR, NURSE, PHYSIOTHERAPIST, PHARMACIST, TECHNICIAN, STUDENT, UNKNOWN
    }

    private ClinicalMatchingEngine() {}

    private static final Map<ClinicalCadre, List<String>> CADRE_KEYWORDS = new LinkedHashMap<>();
    static {
        CADRE_KEYWORDS.put(ClinicalCadre.DOCTOR, List.of(
                "doctor", "physician", "surgeon", "mbbs", "md", "ms", "dnb", "dm", "mch",
                "consultant", "resident", "registrar", "fellow", "intensivist", "anesthesiologist",
                "anaesthesiologist", "cardiologist", "pediatrician", "paediatrician", "gynaecologist",
                "gynecologist", "orthopedist", "orthopaedic surgeon", "general practitioner", "gp",
                "medical officer", "cmo", "gdmo", "rso", "duty doctor", "casualty medical officer", "casuality medical officer"
        ));
        CADRE_KEYWORDS.put(ClinicalCadre.NURSE, List.of(
                "nurse", "nursing", "staff nurse", "sister", "gnm", "b.sc nursing", "bsc nursing",
                "m.sc nursing", "msc nursing", "anm", "charge nurse", "nursing officer", "matron",
                "scrub nurse", "bedside nurse", "icu nurse", "ot nurse", "critical care nurse",
                "neonatal nurse", "sister in charge"
        ));
        CADRE_KEYWORDS.put(ClinicalCadre.PHYSIOTHERAPIST, List.of(
                "physio", "physiotherapy", "physiotherapist", "bpt", "mpt", "dpt", "rehabilitation",
                "physical therapist", "neuro physio", "ortho physio", "sports therapist"
        ));
        CADRE_KEYWORDS.put(ClinicalCadre.PHARMACIST, List.of(
                "pharmacist", "pharmacy", "b.pharm", "bpharm", "m.pharm", "mpharm",
                "d.pharm", "dpharm", "pharm.d", "pharmd", "dispensing pharmacist",
                "clinical pharmacist", "hospital pharmacist"
        ));
        CADRE_KEYWORDS.put(ClinicalCadre.TECHNICIAN, List.of(
                "technician", "technologist", "bmlt", "dmlt", "lab tech", "medical lab",
                "radiographer", "x-ray", "xray", "mri", "ct scan", "dialysis tech", "ot tech",
                "anesthesia tech", "ecg tech", "cath lab tech", "perfusionist", "phlebotomist", "mit"
        ));
        CADRE_KEYWORDS.put(ClinicalCadre.STUDENT, List.of(
                "student", "intern", "internship", "trainee", "clinical rotation", "shadowing", "scholar"
        ));
    }

    private record SpecialtyDomain(String domain, List<String> keywords, List<String> relatedDomains) {}

    private static final List<SpecialtyDomain> SPECIALTY_DOMAINS = List.of(
            new SpecialtyDomain("CRITICAL_CARE",
                    List.of("icu", "ccu", "micu", "sicu", "picu", "nicu", "critical care", "intensive care",
                            "acute care", "resuscitation", "ventilator", "intubation", "central line", "bls", "acls", "code blue", "abg", "airway"),
                    List.of("EMERGENCY", "CARDIOLOGY", "SURGERY_OT")),
            new SpecialtyDomain("EMERGENCY",
                    List.of("emergency", "casualty", "trauma", "er", "ed", "triage", "ambulance", "acute"),
                    List.of("CRITICAL_CARE", "SURGERY_OT")),
            new SpecialtyDomain("CARDIOLOGY",
                    List.of("cardiology", "cardiac", "cath lab", "coronary", "cardiothoracic", "ctvs",
                            "echo", "ecg", "angioplasty", "stemi", "arrhythmia", "heart"),
                    List.of("CRITICAL_CARE")),
            new SpecialtyDomain("SURGERY_OT",
                    List.of("operation theatre", "operation theater", "ot", "general surgery", "surgical",
                            "laparoscopic", "post-op", "pacu", "scrub", "circulating", "sterilization", "cssd", "anesthesia"),
                    List.of("CRITICAL_CARE", "EMERGENCY")),
            new SpecialtyDomain("PEDIATRICS_NEONATOLOGY",
                    List.of("pediatrics", "paediatrics", "pediatric", "neonatology", "neonatal", "nicu", "picu",
                            "child health", "pals", "immunization", "newborn"),
                    List.of("CRITICAL_CARE")),
            new SpecialtyDomain("OBSTETRICS_GYNECOLOGY",
                    List.of("obstetrics", "gynecology", "ob-gyn", "obg", "maternity", "labor room", "labour room",
                            "delivery", "antenatal", "postnatal", "l&d", "midwifery", "cesarean"),
                    List.of("PEDIATRICS_NEONATOLOGY", "SURGERY_OT")),
            new SpecialtyDomain("ORTHOPEDICS",
                    List.of("orthopedics", "ortho", "orthopaedic", "bone", "joint", "spine", "fracture",
                            "arthroplasty", "trauma surgery", "musculoskeletal", "replacement"),
                    List.of("SURGERY_OT", "PHYSIOTHERAPY")),
            new SpecialtyDomain("NEPHROLOGY_DIALYSIS",
                    List.of("nephrology", "dialysis", "hemodialysis", "renal", "kidney", "peritoneal dialysis", "fistula"),
                    List.of("CRITICAL_CARE", "GENERAL_MEDICINE")),
            new SpecialtyDomain("NEUROLOGY_NEUROSURGERY",
                    List.of("neurology", "neuro", "neurosurgery", "stroke", "epilepsy", "neuro-icu", "eeg", "craniotomy"),
                    List.of("CRITICAL_CARE", "SURGERY_OT")),
            new SpecialtyDomain("ONCOLOGY",
                    List.of("oncology", "cancer", "chemotherapy", "radiation oncology", "surgical oncology", "palliative", "tumor"),
                    List.of("GENERAL_MEDICINE", "SURGERY_OT")),
            new SpecialtyDomain("GENERAL_MEDICINE",
                    List.of("general medicine", "internal medicine", "physician", "opd", "inpatient", "ward",
                            "family medicine", "primary care", "fever", "hypertension", "diabetes"),
                    List.of("CRITICAL_CARE")),
            new SpecialtyDomain("DIAGNOSTICS_PATHOLOGY_RADIOLOGY",
                    List.of("pathology", "hematology", "biochemistry", "microbiology", "blood bank", "histopathology",
                            "radiology", "ultrasound", "usg", "ct scan", "mri", "x-ray", "analyzer"),
                    List.of()),
            new SpecialtyDomain("PHYSIOTHERAPY",
                    List.of("physiotherapy", "rehabilitation", "mobility", "exercise therapy", "stroke rehab", "post-op rehab"),
                    List.of("ORTHOPEDICS", "NEUROLOGY_NEUROSURGERY"))
    );

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    public static int calculateJobMatch(CandidateProfile candidate, boolean isStudent, JobPosting job) {
        if (candidate == null || job == null) return 50;

        String candRoleStr = joinNonBlank(candidate.getProfession(), candidate.getQualification(), candidate.getCourse());
        ClinicalCadre candidateCadre = isStudent ? ClinicalCadre.STUDENT : detectCadre(candRoleStr);

        String jobText = joinNonBlank(
                job.getTitle(),
                job.getDepartment(),
                job.getQualificationRequired(),
                job.getEmploymentType(),
                job.getDescription()
        );
        ClinicalCadre jobCadre = detectCadre(joinNonBlank(job.getTitle(), job.getDepartment(), job.getQualificationRequired()));

        return computeScore(
                candidateCadre,
                jobCadre,
                isStudent,
                job.isInternship(),
                joinNonBlank(candidate.getSpecialty(), candidate.getSubSpecialties()),
                jobText,
                parseStringList(candidate.getSkills()),
                candidate.getExperienceYears(),
                job.getExperienceRequired(),
                candidate.getCity(),
                parseStringList(candidate.getPreferredCities()),
                Boolean.TRUE.equals(candidate.getRelocate()),
                job.getCity(),
                candidate.getQualification(),
                job.getQualificationRequired(),
                candidate.getKycStatus()
        );
    }

    public static int calculateShiftMatch(CandidateProfile candidate, boolean isStudent, LocumShift shift, FacilityProfile facility) {
        if (candidate == null || shift == null) return 50;

        String candRoleStr = joinNonBlank(candidate.getProfession(), candidate.getQualification(), candidate.getCourse());
        ClinicalCadre candidateCadre = isStudent ? ClinicalCadre.STUDENT : detectCadre(candRoleStr);
        String shiftText = joinNonBlank(shift.getTitle(), shift.getDepartment());
        ClinicalCadre shiftCadre = detectCadre(shiftText);

        String facilityCity = facility != null ? facility.getCity() : null;

        int baseScore = computeScore(
                candidateCadre,
                shiftCadre,
                isStudent,
                false,
                joinNonBlank(candidate.getSpecialty(), candidate.getSubSpecialties()),
                shiftText,
                parseStringList(candidate.getSkills()),
                candidate.getExperienceYears(),
                null,
                candidate.getCity(),
                parseStringList(candidate.getPreferredCities()),
                Boolean.TRUE.equals(candidate.getRelocate()),
                facilityCity,
                candidate.getQualification(),
                null,
                candidate.getKycStatus()
        );

        // Verified hospital shift rating bonus for eligible clinicians (never penalizes 0-rating newcomers)
        if (!isStudent && baseScore > 28
                && candidate.getShiftRatingCount() != null && candidate.getShiftRatingCount() > 0
                && candidate.getShiftRatingAvg() != null) {
            double avg = candidate.getShiftRatingAvg().doubleValue();
            int ratingBoost = avg >= 4.5 ? 3 : (avg >= 4.0 ? 2 : (avg >= 3.5 ? 1 : 0));
            return Math.min(99, baseScore + ratingBoost);
        }
        return baseScore;
    }

    public static int calculateResdexMatch(CandidateProfile candidate, boolean isStudent, FacilityProfile facility, List<JobPosting> facilityOpenJobs) {
        if (candidate == null) return 50;
        if (facilityOpenJobs != null && !facilityOpenJobs.isEmpty()) {
            int best = 0;
            for (JobPosting j : facilityOpenJobs) {
                int score = calculateJobMatch(candidate, isStudent, j);
                if (score > best) best = score;
            }
            return best;
        }
        String facilityCity = facility != null ? facility.getCity() : null;
        String facilityHires = facility != null ? joinNonBlank(facility.getHiringNeeds(), facility.getDepartments()) : "";
        return computeScore(
                isStudent ? ClinicalCadre.STUDENT : detectCadre(joinNonBlank(candidate.getProfession(), candidate.getQualification())),
                ClinicalCadre.UNKNOWN,
                isStudent,
                isStudent,
                joinNonBlank(candidate.getSpecialty(), candidate.getSubSpecialties()),
                facilityHires,
                parseStringList(candidate.getSkills()),
                candidate.getExperienceYears(),
                null,
                candidate.getCity(),
                parseStringList(candidate.getPreferredCities()),
                Boolean.TRUE.equals(candidate.getRelocate()),
                facilityCity,
                candidate.getQualification(),
                null,
                candidate.getKycStatus()
        );
    }

    private static int computeScore(
            ClinicalCadre candidateCadre,
            ClinicalCadre jobCadre,
            boolean isStudent,
            boolean isInternshipJob,
            String candidateSpecText,
            String jobText,
            List<String> candidateSkills,
            String candidateExpStr,
            String jobExpRequiredStr,
            String candidateCity,
            List<String> preferredCities,
            boolean relocate,
            String jobCity,
            String candidateQual,
            String jobReqQual,
            String kycStatus
    ) {
        // 1. Cadre & Track Safety (Max 35 pts)
        int professionScore;
        boolean cadreMismatch = false;
        if (isStudent) {
            if (isInternshipJob) {
                professionScore = 35;
            } else {
                professionScore = 0;
                cadreMismatch = true;
            }
        } else if (candidateCadre == ClinicalCadre.UNKNOWN || jobCadre == ClinicalCadre.UNKNOWN) {
            professionScore = 20;
        } else if (candidateCadre == jobCadre) {
            professionScore = 35;
        } else {
            professionScore = 0;
            cadreMismatch = true;
        }

        // 2. Specialty Domain Alignment (Max 25 pts)
        List<String> candDomains = findSpecialtyDomains(candidateSpecText);
        List<String> jobDomains = findSpecialtyDomains(jobText);
        int specialtyScore;
        boolean directOverlap = candDomains.stream().anyMatch(jobDomains::contains);
        if (directOverlap) {
            specialtyScore = 25;
        } else {
            boolean relatedOverlap = candDomains.stream().anyMatch(cd -> {
                for (SpecialtyDomain sd : SPECIALTY_DOMAINS) {
                    if (sd.domain().equals(cd) && sd.relatedDomains().stream().anyMatch(jobDomains::contains)) {
                        return true;
                    }
                }
                return false;
            });
            if (relatedOverlap) {
                specialtyScore = 18;
            } else if (jobDomains.contains("GENERAL_MEDICINE") || candDomains.contains("GENERAL_MEDICINE")) {
                specialtyScore = 12;
            } else if (candidateSpecText == null || candidateSpecText.isBlank()) {
                specialtyScore = 8;
            } else {
                specialtyScore = 2;
            }
        }

        // 3. Procedural Skills Overlap (Max 15 pts)
        int skillsScore;
        if (!candidateSkills.isEmpty()) {
            int hits = 0;
            for (String skill : candidateSkills) {
                if (containsWordBoundary(jobText, skill)) {
                    hits++;
                }
            }
            if (hits >= 3) skillsScore = 15;
            else if (hits == 2) skillsScore = 12;
            else if (hits == 1) skillsScore = 9;
            else skillsScore = 3;
        } else {
            skillsScore = (candidateCadre == jobCadre && candidateCadre != ClinicalCadre.UNKNOWN) ? 7 : 4;
        }

        // 4. Experience Alignment (Max 10 pts)
        int experienceScore = computeExperienceScore(candidateExpStr, jobExpRequiredStr);

        // 5. Location & Relocation Feasibility (Max 8 pts)
        int locationScore;
        String cCity = candidateCity != null ? candidateCity.trim().toLowerCase(Locale.ROOT) : "";
        String jCity = jobCity != null ? jobCity.trim().toLowerCase(Locale.ROOT) : "";
        if (!cCity.isEmpty() && !jCity.isEmpty() && (cCity.contains(jCity) || jCity.contains(cCity))) {
            locationScore = 8;
        } else if (!jCity.isEmpty() && preferredCities.stream().anyMatch(p -> jCity.contains(p) || p.contains(jCity))) {
            locationScore = 8;
        } else if (relocate) {
            locationScore = 5;
        } else if (cCity.isEmpty() || jCity.isEmpty()) {
            locationScore = 5;
        } else {
            locationScore = 2;
        }

        // 6. Qualification & KYC Verification (Max 7 pts)
        int qualificationScore = 0;
        String cq = candidateQual != null ? candidateQual.toLowerCase(Locale.ROOT) : "";
        String jq = jobReqQual != null ? jobReqQual.toLowerCase(Locale.ROOT) : "";
        if (!jq.isBlank()) {
            if (!cq.isBlank() && (containsWordBoundary(cq, jq) || containsWordBoundary(jq, cq) || containsWordBoundary(jobText, cq))) {
                qualificationScore += 4;
            } else {
                qualificationScore += 1;
            }
        } else {
            qualificationScore += !cq.isBlank() ? 3 : 2;
        }
        if ("VERIFIED".equalsIgnoreCase(kycStatus)) {
            qualificationScore += 3;
        } else if (!"REJECTED".equalsIgnoreCase(kycStatus)) {
            qualificationScore += 1;
        }

        int total = professionScore + specialtyScore + skillsScore + experienceScore + locationScore + qualificationScore;
        if (cadreMismatch) {
            total = Math.min(28, total);
        }
        return Math.max(10, Math.min(99, total));
    }

    private static int computeExperienceScore(String candidateExpStr, String jobExpRequiredStr) {
        Double reqMin = parseMinYears(jobExpRequiredStr);
        Double candYrs = parseMinYears(candidateExpStr);

        if (reqMin == null) {
            return candYrs != null && candYrs > 0 ? 9 : 7;
        }
        if (reqMin <= 0.0) {
            return 10;
        }
        if (candYrs == null) {
            return 5;
        }
        if (candYrs >= reqMin) {
            return 10;
        }
        if (candYrs >= reqMin - 1.0) {
            return 6;
        }
        return 2;
    }

    static Double parseMinYears(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("fresher") || lower.contains("student") || lower.contains("intern") || lower.contains("entry")) {
            return 0.0;
        }
        Matcher m = NUMBER_PATTERN.matcher(lower);
        if (m.find()) {
            double val = Double.parseDouble(m.group(1));
            if (lower.contains("month") && !lower.contains("year") && !lower.contains("yr")) {
                return val / 12.0;
            }
            return val;
        }
        return null;
    }

    public static ClinicalCadre detectCadre(String text) {
        if (text == null || text.isBlank()) return ClinicalCadre.UNKNOWN;
        for (Map.Entry<ClinicalCadre, List<String>> entry : CADRE_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (containsWordBoundary(text, kw)) {
                    return entry.getKey();
                }
            }
        }
        return ClinicalCadre.UNKNOWN;
    }

    public static List<String> findSpecialtyDomains(String text) {
        List<String> matched = new ArrayList<>();
        if (text == null || text.isBlank()) return matched;
        for (SpecialtyDomain s : SPECIALTY_DOMAINS) {
            for (String kw : s.keywords()) {
                if (containsWordBoundary(text, kw)) {
                    matched.add(s.domain());
                    break;
                }
            }
        }
        return matched;
    }

    static boolean containsWordBoundary(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) return false;
        String escaped = Pattern.quote(needle.trim().toLowerCase(Locale.ROOT));
        Pattern p = Pattern.compile("(?<![a-z0-9])" + escaped + "(?![a-z0-9])", Pattern.CASE_INSENSITIVE);
        return p.matcher(haystack).find();
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static List<String> parseStringList(String jsonOrCsv) {
        List<String> out = new ArrayList<>();
        if (jsonOrCsv == null || jsonOrCsv.isBlank()) return out;
        String trimmed = jsonOrCsv.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        for (String token : trimmed.split(",")) {
            String clean = token.replace("\"", "").trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }
}

