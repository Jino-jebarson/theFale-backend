package com.thefale.service.ai;

import com.thefale.dto.AiRequestDto;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.FacilityWallet;
import com.thefale.entity.JobApplication;
import com.thefale.entity.JobPosting;
import com.thefale.entity.LocumShift;
import com.thefale.entity.ShiftClaim;
import com.thefale.entity.SupportTicket;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.FacilityWalletRepository;
import com.thefale.repository.JobApplicationRepository;
import com.thefale.repository.JobPostingRepository;
import com.thefale.repository.LocumShiftRepository;
import com.thefale.repository.ShiftClaimRepository;
import com.thefale.repository.StudentUpgradeRequestRepository;
import com.thefale.repository.SupportTicketRepository;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves authenticated user role boundaries, fetches query-aware real-time context for Ally Chat,
 * and validates role-scoped UI action identifiers.
 */
@Service
public class AiChatContextService {

    private static final Logger log = LoggerFactory.getLogger(AiChatContextService.class);

    private static final Map<String, Set<String>> ALLOWED_ACTIONS_BY_ROLE = Map.of(
            "clinician", Set.of("open_to_work", "browse_shifts", "browse_jobs", "check_earnings", "view_profile", "handover_human"),
            "student", Set.of("find_internships", "start_learning", "join_huddle", "view_profile", "handover_human"),
            "hospital", Set.of("post_shift", "search_candidates", "wallet_topup", "review_applicants", "handover_human"),
            "admin", Set.of("audit_candidates", "verify_hospitals", "job_approvals", "shortlisting", "huddle_moderation", "support_tickets")
    );

    private static final Map<String, String> DEFAULT_ACTION_LABELS = Map.ofEntries(
            Map.entry("open_to_work", "Set Me Open to Work"),
            Map.entry("browse_shifts", "Browse Locum Shifts"),
            Map.entry("browse_jobs", "Browse Hospital Jobs"),
            Map.entry("check_earnings", "Check Escrow Earnings"),
            Map.entry("view_profile", "Open My Profile"),
            Map.entry("handover_human", "Escalate to Support"),
            Map.entry("find_internships", "Browse Internship Shifts"),
            Map.entry("start_learning", "Open Clinical Learning"),
            Map.entry("join_huddle", "Open Mentor Huddle"),
            Map.entry("post_shift", "Post a Locum Shift"),
            Map.entry("search_candidates", "Search Duty Clinicians"),
            Map.entry("wallet_topup", "Open Escrow Wallet"),
            Map.entry("review_applicants", "Review Applicants"),
            Map.entry("audit_candidates", "Review Candidate KYC"),
            Map.entry("verify_hospitals", "Audit Hospital Verification"),
            Map.entry("job_approvals", "Open Job Approvals"),
            Map.entry("shortlisting", "Open Shortlisting Queue"),
            Map.entry("huddle_moderation", "Open Huddle Moderation"),
            Map.entry("support_tickets", "Open Support Tickets")
    );

    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityWalletRepository facilityWalletRepository;
    private final JobPostingRepository jobPostingRepository;
    private final LocumShiftRepository locumShiftRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final StudentUpgradeRequestRepository studentUpgradeRequestRepository;

    public AiChatContextService(
            CandidateProfileRepository candidateProfileRepository,
            FacilityProfileRepository facilityProfileRepository,
            FacilityWalletRepository facilityWalletRepository,
            JobPostingRepository jobPostingRepository,
            LocumShiftRepository locumShiftRepository,
            JobApplicationRepository jobApplicationRepository,
            ShiftClaimRepository shiftClaimRepository,
            SupportTicketRepository supportTicketRepository,
            StudentUpgradeRequestRepository studentUpgradeRequestRepository
    ) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.facilityProfileRepository = facilityProfileRepository;
        this.facilityWalletRepository = facilityWalletRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.locumShiftRepository = locumShiftRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.shiftClaimRepository = shiftClaimRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.studentUpgradeRequestRepository = studentUpgradeRequestRepository;
    }

    /**
     * Resolves the effective Ally persona role. Authenticated JWT principals always take precedence
     * over client-provided role hints to prevent role spoofing.
     */
    public String resolveEffectiveRole(AiRequestDto request) {
        Optional<CustomUserPrincipal> principalOpt = SecurityUtils.getCurrentPrincipal();
        if (principalOpt.isPresent()) {
            CustomUserPrincipal p = principalOpt.get();
            if (p.isAdmin()) return "admin";
            if (p.isFacility()) return "hospital";
            if (p.isStudent()) return "student";
            return "clinician";
        }

        String rawRole = request != null ? request.getRole() : null;
        if (rawRole == null && request != null && request.getMetadata() != null) {
            Object r = request.getMetadata().get("activeRole");
            if (r == null) r = request.getMetadata().get("role");
            if (r != null) rawRole = r.toString();
        }
        String normalized = rawRole != null ? rawRole.trim().toLowerCase(Locale.ROOT) : "clinician";
        if ("facility".equals(normalized)) normalized = "hospital";
        if ("professional".equals(normalized)) normalized = "clinician";
        return ALLOWED_ACTIONS_BY_ROLE.containsKey(normalized) ? normalized : "clinician";
    }

    public Set<String> getAllowedActionsForRole(String role) {
        return ALLOWED_ACTIONS_BY_ROLE.getOrDefault(role, ALLOWED_ACTIONS_BY_ROLE.get("clinician"));
    }

    public String sanitizeActionForRole(String role, String rawAction) {
        if (rawAction == null || rawAction.isBlank()) return null;
        String clean = rawAction.trim().toLowerCase(Locale.ROOT);
        if ("null".equals(clean) || "none".equals(clean)) return null;
        return getAllowedActionsForRole(role).contains(clean) ? clean : null;
    }

    public String resolveActionLabel(String action, String rawLabel) {
        if (action == null) return null;
        if (rawLabel != null && !rawLabel.isBlank() && !"null".equalsIgnoreCase(rawLabel.trim())) {
            return rawLabel.trim();
        }
        return DEFAULT_ACTION_LABELS.getOrDefault(action, "Open in FALE");
    }

    @Transactional(readOnly = true)
    public String buildLiveRoleContext(String effectiveRole, String userPrompt) {
        Optional<CustomUserPrincipal> principalOpt = SecurityUtils.getCurrentPrincipal();
        if (principalOpt.isEmpty()) {
            return "Authentication Status: Guest / Preview session (no user DB records attached).";
        }

        CustomUserPrincipal principal = principalOpt.get();
        String query = userPrompt != null ? userPrompt.trim().toLowerCase(Locale.ROOT) : "";
        try {
            return switch (effectiveRole) {
                case "admin" -> buildAdminContext(query);
                case "hospital" -> buildHospitalContext(principal.getId());
                case "student" -> buildStudentContext(principal.getId(), query);
                default -> buildClinicianContext(principal.getId(), query);
            };
        } catch (Exception ex) {
            log.warn("Could not build live AI context for role {}: {}", effectiveRole, ex.getMessage());
            return "";
        }
    }

    private String buildAdminContext(String query) {
        List<CandidateProfile> pendingCandidateList = candidateProfileRepository.findByKycStatus("PENDING");
        long verifiedCandidates = candidateProfileRepository.countByKycStatus("VERIFIED");
        List<FacilityProfile> pendingFacilityList = facilityProfileRepository.findByKycStatus("PENDING");
        long verifiedFacilities = facilityProfileRepository.countByKycStatus("VERIFIED");
        List<JobPosting> pendingJobList = jobPostingRepository.findByStatus("PENDING_APPROVAL");
        long openJobs = jobPostingRepository.countByStatus("OPEN");
        long openShifts = locumShiftRepository.countByStatus("OPEN");
        long screeningApps = jobApplicationRepository.countByStatus("UNDER_ADMIN_SCREENING");
        long pendingPayouts = shiftClaimRepository.countByPayoutStatus("PENDING_DISBURSEMENT");
        List<SupportTicket> openTicketList = supportTicketRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(t.getStatus()))
                .toList();
        long upgradeRequests = studentUpgradeRequestRepository.findByStatus("PENDING").size();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "LIVE ADMIN OPERATIONS SNAPSHOT:\n" +
                "- Candidate KYC: %d pending review, %d verified\n" +
                "- Hospital Verification: %d pending review, %d verified\n" +
                "- Job Approvals: %d pending approval, %d open jobs, %d open locum shifts\n" +
                "- Shortlisting & Upgrades: %d applications under admin screening, %d student upgrade requests pending\n" +
                "- Payouts & Support: %d pending disbursements, %d active open/in-progress support tickets\n",
                pendingCandidateList.size(), verifiedCandidates,
                pendingFacilityList.size(), verifiedFacilities,
                pendingJobList.size(), openJobs, openShifts,
                screeningApps, upgradeRequests,
                pendingPayouts, openTicketList.size()
        ));

        boolean broadOrAll = query.isBlank() || query.contains("queue") || query.contains("summary") || query.contains("pending") || query.contains("status") || query.contains("hello");
        if ((broadOrAll || query.contains("kyc") || query.contains("candidate") || query.contains("doctor") || query.contains("nurse")) && !pendingCandidateList.isEmpty()) {
            sb.append("- Top Pending Candidate KYC Items:\n");
            pendingCandidateList.stream().limit(4).forEach(c -> sb.append(String.format(
                    "  * %s | Role: %s | City: %s | Reg: %s\n",
                    safeStr(c.getName(), "Unnamed"),
                    safeStr(c.getProfession(), safeStr(c.getCourse(), "Student")),
                    safeStr(c.getCity(), "-"),
                    safeStr(c.getRegNumber(), "Pending")
            )));
        }

        if ((broadOrAll || query.contains("hospital") || query.contains("facility") || query.contains("cea")) && !pendingFacilityList.isEmpty()) {
            sb.append("- Top Pending Hospital Verification Items:\n");
            pendingFacilityList.stream().limit(4).forEach(f -> sb.append(String.format(
                    "  * %s (%s) | City: %s | CEA License: %s\n",
                    safeStr(f.getHospitalName(), "Facility"),
                    safeStr(f.getFacilityType(), "Hospital"),
                    safeStr(f.getCity(), "-"),
                    safeStr(f.getLicenseNumber(), "Pending")
            )));
        }

        if ((broadOrAll || query.contains("job") || query.contains("approval") || query.contains("posting")) && !pendingJobList.isEmpty()) {
            sb.append("- Top Pending Job Approval Items:\n");
            pendingJobList.stream().limit(4).forEach(j -> sb.append(String.format(
                    "  * %s | Dept: %s | City: %s\n",
                    safeStr(j.getTitle(), "Job"),
                    safeStr(j.getDepartment(), "-"),
                    safeStr(j.getCity(), "-")
            )));
        }

        if ((broadOrAll || query.contains("ticket") || query.contains("support") || query.contains("escalat")) && !openTicketList.isEmpty()) {
            sb.append("- Active Support Tickets:\n");
            openTicketList.stream().limit(4).forEach(t -> sb.append(String.format(
                    "  * #%s | Category: %s | Status: %s | Subject: %s\n",
                    t.getId() != null ? t.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT) : "TICKET",
                    safeStr(t.getCategory(), "OTHER"),
                    safeStr(t.getStatus(), "OPEN"),
                    safeStr(t.getSubject(), "Support Inquiry")
            )));
        }
        return sb.toString().trim();
    }

    private String buildHospitalContext(UUID userId) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(userId).orElse(null);
        if (facility == null) {
            return "LIVE HOSPITAL SNAPSHOT: Facility onboarding profile is incomplete (not yet submitted).";
        }

        FacilityWallet wallet = facilityWalletRepository.findByFacilityId(facility.getId()).orElse(null);
        BigDecimal available = wallet != null && wallet.getAvailableBalance() != null ? wallet.getAvailableBalance() : BigDecimal.ZERO;
        BigDecimal locked = wallet != null && wallet.getLockedEscrowBalance() != null ? wallet.getLockedEscrowBalance() : BigDecimal.ZERO;
        int credits = wallet != null ? wallet.getCandidateCredits() : 0;

        List<JobPosting> jobs = jobPostingRepository.findByFacilityIdAndDeletedAtIsNull(facility.getId());
        long openJobs = jobs.stream().filter(j -> "OPEN".equalsIgnoreCase(j.getStatus())).count();
        long pendingJobs = jobs.stream().filter(j -> "PENDING_APPROVAL".equalsIgnoreCase(j.getStatus())).count();
        List<UUID> jobIds = jobs.stream().map(JobPosting::getId).toList();
        List<JobApplication> apps = jobIds.isEmpty() ? List.of() : jobApplicationRepository.findByJobIdIn(jobIds);

        List<LocumShift> shifts = locumShiftRepository.findByFacilityIdOrderByStartAtDesc(facility.getId());
        long openShifts = shifts.stream().filter(s -> "OPEN".equalsIgnoreCase(s.getStatus())).count();

        StringBuilder sb = new StringBuilder(String.format(
                "LIVE HOSPITAL SNAPSHOT:\n" +
                "- Facility: %s (%s) in %s, %s\n" +
                "- Verification Status: %s | Capacity: %d beds (%d ICU beds)\n" +
                "- Escrow Wallet: INR %s available, INR %s locked in escrow, %d candidate unlock credits\n" +
                "- Hiring & Shifts: %d open jobs (%d pending admin approval), %d total job applicants, %d active open locum shifts\n",
                safeStr(facility.getHospitalName(), "Healthcare Facility"),
                safeStr(facility.getFacilityType(), "Hospital"),
                safeStr(facility.getCity(), "India"),
                safeStr(facility.getState(), ""),
                safeStr(facility.getKycStatus(), "PENDING"),
                facility.getBeds(),
                facility.getIcuBeds(),
                available.toPlainString(),
                locked.toPlainString(),
                credits,
                openJobs,
                pendingJobs,
                apps.size(),
                openShifts
        ));

        if (!jobs.isEmpty()) {
            sb.append("- Facility Job Postings (up to 4):\n");
            jobs.stream().limit(4).forEach(j -> sb.append(String.format(
                    "  * %s (%s) | City: %s | Status: %s | Openings: %d\n",
                    j.getTitle(), j.getDepartment(), j.getCity(), j.getStatus(), j.getOpenings()
            )));
        }

        if (!shifts.isEmpty()) {
            sb.append("- Recent Locum Shifts (up to 4):\n");
            shifts.stream().limit(4).forEach(s -> sb.append(String.format(
                    "  * %s (%s) | Rate: INR %s/hr | Claimed: %d/%d | Status: %s\n",
                    s.getTitle(), s.getDepartment(),
                    s.getRatePerHour() != null ? s.getRatePerHour().toPlainString() : "0",
                    s.getSlotsClaimed(), s.getOpenings(), s.getStatus()
            )));
        }

        return sb.toString().trim();
    }

    private String buildStudentContext(UUID userId, String query) {
        CandidateProfile student = candidateProfileRepository.findByUserId(userId).orElse(null);
        List<JobPosting> internships = jobPostingRepository.findOpenPostings(true);
        StringBuilder sb = new StringBuilder("LIVE STUDENT SNAPSHOT:\n");
        String preferredCity = "";
        if (student != null) {
            preferredCity = safeStr(student.getCity(), "").toLowerCase(Locale.ROOT);
            List<JobApplication> apps = jobApplicationRepository.findByCandidateIdOrderByAppliedAtDesc(student.getId());
            sb.append(String.format(
                    "- Student: %s | Course: %s (%s) at %s | City: %s | Verification: %s | Open to internships: %s\n" +
                    "- My Applications: %d submitted\n",
                    safeStr(student.getName(), "Student"),
                    safeStr(student.getCourse(), "Clinical Student"),
                    safeStr(student.getStudyYear(), "Enrolled"),
                    safeStr(student.getCollege(), "Medical/Nursing College"),
                    safeStr(student.getCity(), "India"),
                    safeStr(student.getKycStatus(), "PENDING"),
                    Boolean.TRUE.equals(student.getOpenToWork()) ? "Yes" : "No",
                    apps.size()
            ));
            if (!apps.isEmpty()) {
                sb.append("- Latest Applications:\n");
                apps.stream().limit(3).forEach(a -> sb.append(String.format(
                        "  * Application %s -> Status: %s\n",
                        a.getJobId() != null ? a.getJobId().toString().substring(0, 8) : "job",
                        safeStr(a.getStatus(), "APPLIED")
                )));
            }
        } else {
            sb.append("- Student Profile: Onboarding not yet completed.\n");
        }

        final String cityMatch = preferredCity;
        List<JobPosting> sortedInternships = internships.stream()
                .sorted(Comparator.comparingInt((JobPosting j) -> scoreMatch(query, cityMatch, j.getTitle(), j.getDepartment(), j.getCity())).reversed())
                .toList();

        if (sortedInternships.isEmpty()) {
            sb.append("- Live Open Internships: 0 open internships right now.");
        } else {
            sb.append("- Matching Live Open Internships (up to 5):\n");
            sortedInternships.stream().limit(5).forEach(j -> sb.append(String.format(
                    "  * %s | Dept: %s | City: %s | Type: %s\n",
                    j.getTitle(), j.getDepartment(), j.getCity(), j.getEmploymentType()
            )));
        }
        return sb.toString().trim();
    }

    private String buildClinicianContext(UUID userId, String query) {
        CandidateProfile profile = candidateProfileRepository.findByUserId(userId).orElse(null);
        List<JobPosting> openJobs = jobPostingRepository.findOpenPostings(false);
        List<LocumShift> openShifts = locumShiftRepository.findOpenShifts();

        StringBuilder sb = new StringBuilder("LIVE CLINICIAN SNAPSHOT:\n");
        String preferredCity = "";
        if (profile != null) {
            preferredCity = safeStr(profile.getCity(), "").toLowerCase(Locale.ROOT);
            List<JobApplication> apps = jobApplicationRepository.findByCandidateIdOrderByAppliedAtDesc(profile.getId());
            List<ShiftClaim> claims = shiftClaimRepository.findByCandidateIdOrderByClaimedAtDesc(profile.getId());
            sb.append(String.format(
                    "- Clinician: %s | Role: %s (%s) | Qualification: %s | Experience: %s\n" +
                    "- Council: %s (Reg: %s) | City: %s | KYC Status: %s | Open to Work: %s\n" +
                    "- Activity: %d job applications, %d claimed locum shifts\n",
                    safeStr(profile.getName(), "Clinician"),
                    safeStr(profile.getProfession(), "Healthcare Professional"),
                    safeStr(profile.getSpecialty(), "General"),
                    safeStr(profile.getQualification(), "Verified"),
                    safeStr(profile.getExperienceYears(), "Not specified"),
                    safeStr(profile.getCouncil(), "State Council"),
                    safeStr(profile.getRegNumber(), "Pending"),
                    safeStr(profile.getCity(), "India"),
                    safeStr(profile.getKycStatus(), "PENDING"),
                    Boolean.TRUE.equals(profile.getOpenToWork()) ? "Yes" : "No",
                    apps.size(),
                    claims.size()
            ));
            if (!apps.isEmpty()) {
                sb.append("- Latest Job Applications:\n");
                apps.stream().limit(3).forEach(a -> sb.append(String.format(
                        "  * Application %s -> Status: %s\n",
                        a.getJobId() != null ? a.getJobId().toString().substring(0, 8) : "job",
                        safeStr(a.getStatus(), "APPLIED")
                )));
            }
            if (!claims.isEmpty()) {
                sb.append("- Latest Shift Claims:\n");
                claims.stream().limit(3).forEach(c -> sb.append(String.format(
                        "  * Shift %s -> Status: %s, Payout: %s\n",
                        c.getShiftId() != null ? c.getShiftId().toString().substring(0, 8) : "shift",
                        safeStr(c.getStatus(), "CLAIMED"),
                        safeStr(c.getPayoutStatus(), "PENDING")
                )));
            }
        } else {
            sb.append("- Clinician Profile: Onboarding not yet completed.\n");
        }

        final String cityMatch = preferredCity;
        List<LocumShift> rankedShifts = openShifts.stream()
                .sorted(Comparator.comparingInt((LocumShift s) -> scoreMatch(query, cityMatch, s.getTitle(), s.getDepartment(), null)).reversed())
                .toList();

        if (!rankedShifts.isEmpty()) {
            sb.append("- Live Open Locum Shifts (up to 5):\n");
            rankedShifts.stream().limit(5).forEach(s -> sb.append(String.format(
                    "  * %s (%s) | %dh | Rate: INR %s/hr%s\n",
                    s.getTitle(),
                    s.getDepartment(),
                    s.getHours(),
                    s.getRatePerHour() != null ? s.getRatePerHour().toPlainString() : "0",
                    s.isUrgent() ? " [URGENT]" : ""
            )));
        } else {
            sb.append("- Live Open Locum Shifts: 0 open shifts right now.\n");
        }

        List<JobPosting> rankedJobs = openJobs.stream()
                .sorted(Comparator.comparingInt((JobPosting j) -> scoreMatch(query, cityMatch, j.getTitle(), j.getDepartment(), j.getCity())).reversed())
                .toList();

        if (!rankedJobs.isEmpty()) {
            sb.append("- Live Open Hospital Jobs (up to 4):\n");
            rankedJobs.stream().limit(4).forEach(j -> sb.append(String.format(
                    "  * %s | Dept: %s | City: %s\n",
                    j.getTitle(), j.getDepartment(), j.getCity()
            )));
        }
        return sb.toString().trim();
    }

    private int scoreMatch(String query, String userCity, String title, String dept, String city) {
        int score = 0;
        String haystack = (safeStr(title, "") + " " + safeStr(dept, "") + " " + safeStr(city, "")).toLowerCase(Locale.ROOT);
        if (!userCity.isBlank() && city != null && city.toLowerCase(Locale.ROOT).contains(userCity)) {
            score += 2;
        }
        if (query != null && !query.isBlank()) {
            for (String token : query.split("\\s+")) {
                if (token.length() >= 3 && haystack.contains(token)) {
                    score += 3;
                }
            }
        }
        return score;
    }

    public Map<String, Object> buildActionData(String action, String rawLabel) {
        if (action == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", resolveActionLabel(action, rawLabel));
        return data;
    }

    private String safeStr(String val, String fallback) {
        return val != null && !val.isBlank() ? val.trim() : fallback;
    }
}
