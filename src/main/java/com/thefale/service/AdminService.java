package com.thefale.service;

import com.thefale.dto.*;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final LocumShiftRepository locumShiftRepository;
    private final FacilityWalletRepository facilityWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final StudentUpgradeRequestRepository studentUpgradeRequestRepository;
    private final CandidateLevelHistoryRepository candidateLevelHistoryRepository;
    private final HuddlePostRepository huddlePostRepository;
    private final HuddleFlagRepository huddleFlagRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditLogService auditLogService;
    private final JobApplicationRepository jobApplicationRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final FacilityDocumentRepository facilityDocumentRepository;
    private final HuddleService huddleService;
    private final MentorApplicationRepository mentorApplicationRepository;

    @Transactional(readOnly = true)
    public AdminDashboardMetricsDto getDashboardMetrics() {
        long pendingCandidates = candidateProfileRepository.countByKycStatus("PENDING");
        long verifiedCandidates = candidateProfileRepository.countByKycStatus("VERIFIED");
        long totalCandidates = candidateProfileRepository.count();

        long pendingFacilities = facilityProfileRepository.countByKycStatus("PENDING");
        long verifiedFacilities = facilityProfileRepository.countByKycStatus("VERIFIED");
        long totalFacilities = facilityProfileRepository.count();

        long pendingPostings = jobPostingRepository.countByStatus("PENDING_APPROVAL");
        long activePostings = jobPostingRepository.countByStatus("OPEN");
        long totalPostings = jobPostingRepository.count();

        long pendingApplications = jobApplicationRepository.countByStatus("UNDER_ADMIN_SCREENING");
        long shortlistedApplications = jobApplicationRepository.countByStatus("SHORTLISTED")
                + jobApplicationRepository.countByStatus("INTERVIEW")
                + jobApplicationRepository.countByStatus("OFFERED")
                + jobApplicationRepository.countByStatus("HIRED");
        long totalApplications = jobApplicationRepository.count();

        long pendingPayouts = shiftClaimRepository.countByPayoutStatus("PENDING_DISBURSEMENT");
        long flaggedHuddlePosts = huddlePostRepository.findFlaggedPosts().size();
        long upgradeRequests = studentUpgradeRequestRepository.findByStatus("PENDING").size();

        Map<String, Long> cadre = candidateProfileRepository.findAll().stream()
                .filter(c -> c.getProfession() != null && !c.getProfession().isBlank())
                .collect(Collectors.groupingBy(CandidateProfile::getProfession, Collectors.counting()));

        // Dynamic SLA Turnaround calculation from real database submissions
        List<Double> turnaroundHoursList = new ArrayList<>();
        int onTimeCount = 0;

        List<CandidateProfile> allCandidates = candidateProfileRepository.findAll();
        for (CandidateProfile c : allCandidates) {
            if (c.getCreatedAt() != null) {
                if (!"PENDING".equalsIgnoreCase(c.getKycStatus()) && c.getUpdatedAt() != null) {
                    double hours = Math.max(0.1, java.time.Duration.between(c.getCreatedAt(), c.getUpdatedAt()).toMinutes() / 60.0);
                    turnaroundHoursList.add(hours);
                    if (hours <= 24.0) onTimeCount++;
                } else if ("PENDING".equalsIgnoreCase(c.getKycStatus())) {
                    double currentWaitingHours = Math.max(0.1, java.time.Duration.between(c.getCreatedAt(), java.time.Instant.now()).toMinutes() / 60.0);
                    turnaroundHoursList.add(currentWaitingHours);
                    if (currentWaitingHours <= 24.0) onTimeCount++;
                }
            }
        }

        List<FacilityProfile> allFacilities = facilityProfileRepository.findAll();
        for (FacilityProfile f : allFacilities) {
            if (f.getCreatedAt() != null) {
                if (f.getVerifiedAt() != null) {
                    double hours = Math.max(0.1, java.time.Duration.between(f.getCreatedAt(), f.getVerifiedAt()).toMinutes() / 60.0);
                    turnaroundHoursList.add(hours);
                    if (hours <= 24.0) onTimeCount++;
                } else if (!"PENDING".equalsIgnoreCase(f.getKycStatus()) && f.getUpdatedAt() != null) {
                    double hours = Math.max(0.1, java.time.Duration.between(f.getCreatedAt(), f.getUpdatedAt()).toMinutes() / 60.0);
                    turnaroundHoursList.add(hours);
                    if (hours <= 24.0) onTimeCount++;
                } else if ("PENDING".equalsIgnoreCase(f.getKycStatus())) {
                    double currentWaitingHours = Math.max(0.1, java.time.Duration.between(f.getCreatedAt(), java.time.Instant.now()).toMinutes() / 60.0);
                    turnaroundHoursList.add(currentWaitingHours);
                    if (currentWaitingHours <= 24.0) onTimeCount++;
                }
            }
        }

        List<StudentUpgradeRequest> allUpgrades = studentUpgradeRequestRepository.findAll();
        for (StudentUpgradeRequest u : allUpgrades) {
            if (u.getCreatedAt() != null) {
                if (u.getReviewedAt() != null) {
                    double hours = Math.max(0.1, java.time.Duration.between(u.getCreatedAt(), u.getReviewedAt()).toMinutes() / 60.0);
                    turnaroundHoursList.add(hours);
                    if (hours <= 24.0) onTimeCount++;
                } else if ("PENDING".equalsIgnoreCase(u.getStatus())) {
                    double currentWaitingHours = Math.max(0.1, java.time.Duration.between(u.getCreatedAt(), java.time.Instant.now()).toMinutes() / 60.0);
                    turnaroundHoursList.add(currentWaitingHours);
                    if (currentWaitingHours <= 24.0) onTimeCount++;
                }
            }
        }

        double dynamicTurnaround = 0.0;
        double dynamicOnTimePct = 100.0;
        if (!turnaroundHoursList.isEmpty()) {
            double sum = turnaroundHoursList.stream().mapToDouble(Double::doubleValue).sum();
            dynamicTurnaround = Math.round((sum / turnaroundHoursList.size()) * 10.0) / 10.0;
            dynamicOnTimePct = Math.round(((double) onTimeCount / turnaroundHoursList.size()) * 1000.0) / 10.0;
        }

        return AdminDashboardMetricsDto.builder()
                .pendingCandidateKycCount(pendingCandidates)
                .verifiedCandidatesCount(verifiedCandidates)
                .totalCandidatesCount(totalCandidates)
                .pendingFacilityKycCount(pendingFacilities)
                .verifiedFacilitiesCount(verifiedFacilities)
                .totalFacilitiesCount(totalFacilities)
                .pendingPostingsCount(pendingPostings)
                .activePostingsCount(activePostings)
                .totalPostingsCount(totalPostings)
                .pendingApplicationsCount(pendingApplications)
                .shortlistedApplicationsCount(shortlistedApplications)
                .totalApplicationsCount(totalApplications)
                .pendingShiftPayoutsCount(pendingPayouts)
                .flaggedHuddlePostsCount(flaggedHuddlePosts)
                .studentUpgradeRequestsCount(upgradeRequests)
                .slaTurnaroundHours(dynamicTurnaround)
                .slaOnTimePercentage(dynamicOnTimePct)
                .cadreBreakdown(cadre)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CandidateProfileDto> getCandidates(String status) {
        List<CandidateProfile> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = candidateProfileRepository.findAll();
        } else {
            list = candidateProfileRepository.findByKycStatus(status.trim().toUpperCase());
        }
        return list.stream()
                .map(this::mapCandidateToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CandidateProfileDto> getPendingCandidates() {
        return getCandidates(null);
    }

    @Transactional
    public CandidateProfileDto reviewCandidateKyc(CustomUserPrincipal principal, UUID candidateId, AdminKycReviewRequest request) {
        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        boolean approved = "APPROVE".equalsIgnoreCase(request.getAction());
        candidate.setKycStatus(approved ? "VERIFIED" : "REJECTED");
        candidate.setVerifiedAt(approved ? Instant.now() : null);
        CandidateProfile saved = candidateProfileRepository.save(candidate);

        // Notify Candidate
        Notification notif = Notification.builder()
                .userId(candidate.getUserId())
                .title("KYC Verification: " + candidate.getKycStatus())
                .message(approved ? "Your council credentials have been verified by FALE Compliance." :
                        "Your verification requires attention: " + request.getNotes())
                .type("KYC")
                .actionUrl("/profile")
                .read(false)
                .build();
        notificationRepository.save(notif);

        auditLogService.logEvent(principal.getId(), "ADMIN_CANDIDATE_KYC_REVIEWED", "CandidateProfile", saved.getId().toString(), null);

        return mapCandidateToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<StudentUpgradeRequest> getPendingUpgrades() {
        return studentUpgradeRequestRepository.findByStatus("PENDING");
    }

    @Transactional
    public void reviewStudentUpgrade(CustomUserPrincipal principal, UUID requestId, AdminKycReviewRequest request) {
        StudentUpgradeRequest upgrade = studentUpgradeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Upgrade request not found"));

        CandidateProfile candidate = candidateProfileRepository.findById(upgrade.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        User user = userRepository.findById(candidate.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean approved = "APPROVE".equalsIgnoreCase(request.getAction());
        upgrade.setStatus(approved ? "APPROVED" : "REJECTED");
        upgrade.setAdminNotes(request.getNotes());
        upgrade.setReviewedAt(Instant.now());
        studentUpgradeRequestRepository.save(upgrade);

        if (approved) {
            String fromLevel = user.getCandidateLevel();
            user.setCandidateLevel("PROFESSIONAL");
            userRepository.save(user);

            candidate.setQualification(upgrade.getQualification());
            candidate.setCouncil(upgrade.getCouncil());
            candidate.setRegNumber(upgrade.getRegNumber());
            candidate.setKycStatus("VERIFIED");
            candidate.setVerifiedAt(Instant.now());
            candidateProfileRepository.save(candidate);

            // Level History Audit Trail
            CandidateLevelHistory history = CandidateLevelHistory.builder()
                    .candidateId(candidate.getId())
                    .fromLevel(fromLevel)
                    .toLevel("PROFESSIONAL")
                    .changedBy(principal.getId())
                    .reason("Approved graduation credentials: " + upgrade.getCouncil() + " (" + upgrade.getRegNumber() + ")")
                    .build();
            candidateLevelHistoryRepository.save(history);
        }

        Notification notif = Notification.builder()
                .userId(user.getId())
                .title("Student Graduate Upgrade: " + upgrade.getStatus())
                .message(approved ? "Congratulations! Your graduation upgrade has been approved. You now have full access to locum shift claims and permanent clinical roles." :
                        "Your graduation upgrade was not approved: " + request.getNotes())
                .type("UPGRADE")
                .actionUrl("/profile")
                .read(false)
                .build();
        notificationRepository.save(notif);

        auditLogService.logEvent(principal.getId(), "ADMIN_STUDENT_UPGRADE_REVIEWED", "StudentUpgradeRequest", requestId.toString(), null);
    }

    @Transactional(readOnly = true)
    public List<FacilityProfileDto> getFacilities(String status) {
        List<FacilityProfile> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = facilityProfileRepository.findAll();
        } else {
            list = facilityProfileRepository.findByKycStatus(status.trim().toUpperCase());
        }
        return list.stream()
                .map(this::mapFacilityToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FacilityProfileDto> getPendingFacilities() {
        return getFacilities(null);
    }

    @Transactional
    public FacilityProfileDto reviewFacilityKyc(CustomUserPrincipal principal, UUID facilityId, AdminKycReviewRequest request) {
        FacilityProfile facility = facilityProfileRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));

        boolean approved = "APPROVE".equalsIgnoreCase(request.getAction());
        facility.setKycStatus(approved ? "VERIFIED" : "REJECTED");
        facility.setVerifiedAt(approved ? Instant.now() : null);
        facility.setKycNotes(request.getNotes());
        FacilityProfile saved = facilityProfileRepository.save(facility);

        auditLogService.logEvent(principal.getId(), "ADMIN_FACILITY_KYC_REVIEWED", "FacilityProfile", saved.getId().toString(), null);

        return mapFacilityToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<JobPostingDto> getPostings(String status) {
        List<JobPosting> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = jobPostingRepository.findAll();
        } else if ("PENDING".equalsIgnoreCase(status) || "PENDING_APPROVAL".equalsIgnoreCase(status)) {
            list = jobPostingRepository.findByStatus("PENDING_APPROVAL");
        } else if ("OPEN".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status)) {
            list = jobPostingRepository.findByStatus("OPEN");
        } else {
            list = jobPostingRepository.findByStatus(status.trim().toUpperCase());
        }
        return list.stream()
                .filter(j -> j.getDeletedAt() == null)
                .map(j -> {
                    FacilityProfile f = facilityProfileRepository.findById(j.getFacilityId()).orElse(null);
                    return JobPostingDto.builder()
                            .id(j.getId())
                            .facilityId(j.getFacilityId())
                            .facilityName(f != null ? f.getHospitalName() : "Hospital")
                            .city(j.getCity())
                            .title(j.getTitle())
                            .department(j.getDepartment())
                            .employmentType(j.getEmploymentType())
                            .internship(j.isInternship())
                            .openings(j.getOpenings())
                            .salaryMinPaise(j.getSalaryMin() != null ? j.getSalaryMin().multiply(BigDecimal.valueOf(100)).longValue() : 0L)
                            .salaryMaxPaise(j.getSalaryMax() != null ? j.getSalaryMax().multiply(BigDecimal.valueOf(100)).longValue() : 0L)
                            .experienceRequired(j.getExperienceRequired())
                            .qualificationRequired(j.getQualificationRequired())
                            .description(j.getDescription())
                            .status(j.getStatus())
                            .postedAt(j.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JobPostingDto> getPendingPostings() {
        return getPostings(null);
    }

    @Transactional
    public void reviewPosting(CustomUserPrincipal principal, UUID postingId, AdminKycReviewRequest request) {
        JobPosting posting = jobPostingRepository.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        boolean approved = "APPROVE".equalsIgnoreCase(request.getAction());
        posting.setStatus(approved ? "OPEN" : "REJECTED");
        posting.setModeratedBy(principal.getId());
        posting.setModerationNotes(request.getNotes());
        jobPostingRepository.save(posting);

        // Notify Facility
        FacilityProfile facility = facilityProfileRepository.findById(posting.getFacilityId()).orElse(null);
        if (facility != null) {
            Notification notif = Notification.builder()
                    .userId(facility.getUserId())
                    .title("Job Requirement " + (approved ? "Approved & Live" : "Rejected"))
                    .message(approved ? "Your posting for " + posting.getTitle() + " has been approved by Admin and is now live on FALE." :
                            "Your posting for " + posting.getTitle() + " was rejected: " + (request.getNotes() != null ? request.getNotes() : "Guidelines not met."))
                    .type("POSTING")
                    .actionUrl("/employer")
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        }

        // Notify matching candidates of new approved role (Smart AI Match)
        if (approved) {
            List<CandidateProfile> candidates = candidateProfileRepository.findAll();
            for (CandidateProfile c : candidates) {
                boolean matchesSpecialty = (c.getSpecialty() != null && posting.getDepartment() != null &&
                        c.getSpecialty().toLowerCase().contains(posting.getDepartment().toLowerCase()))
                        || (c.getProfession() != null && posting.getTitle().toLowerCase().contains(c.getProfession().toLowerCase()));
                if (matchesSpecialty) {
                    Notification matchNotif = Notification.builder()
                            .userId(c.getUserId())
                            .title("Smart AI Match: " + posting.getTitle())
                            .message((facility != null ? facility.getHospitalName() : "Partner Hospital") + " posted a new opening matching your clinical specialty in " + (posting.getCity() != null ? posting.getCity() : "your region") + ".")
                            .type("JOB_MATCH")
                            .actionUrl("/jobs")
                            .read(false)
                            .build();
                    notificationRepository.save(matchNotif);
                }
            }
        }

        auditLogService.logEvent(principal.getId(), "ADMIN_POSTING_REVIEWED", "JobPosting", postingId.toString(), null);
    }

    @Transactional(readOnly = true)
    public List<JobApplicationDto> getAdminApplications(String status) {
        List<JobApplication> apps = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? jobApplicationRepository.findByStatusOrderByAppliedAtDesc(status)
                : jobApplicationRepository.findAllByOrderByAppliedAtDesc();

        return apps.stream()
                .map(app -> {
                    JobPosting job = jobPostingRepository.findById(app.getJobId()).orElse(null);
                    CandidateProfile candidate = candidateProfileRepository.findById(app.getCandidateId()).orElse(null);
                    FacilityProfile facility = job != null ? facilityProfileRepository.findById(job.getFacilityId()).orElse(null) : null;
                    User candidateUser = candidate != null ? userRepository.findById(candidate.getUserId()).orElse(null) : null;
                    List<CandidateDocumentDto> docs = candidate != null
                            ? candidateDocumentRepository.findByCandidateId(candidate.getId()).stream()
                                    .map(d -> CandidateDocumentDto.builder()
                                            .id(d.getId())
                                            .documentType(d.getDocumentType())
                                            .originalFileName(d.getOriginalFileName())
                                            .fileUrl(d.getFileUrl())
                                            .fileSizeBytes(d.getFileSizeBytes())
                                            .mimeType(d.getMimeType())
                                            .verified(d.isVerified())
                                            .createdAt(d.getCreatedAt())
                                            .build())
                                    .collect(Collectors.toList())
                            : Collections.emptyList();

                    return JobApplicationDto.builder()
                            .id(app.getId())
                            .jobId(app.getJobId())
                            .jobTitle(job != null ? job.getTitle() : "Healthcare Role")
                            .candidateId(app.getCandidateId())
                            .candidateName(candidate != null ? candidate.getName() : "Candidate")
                            .candidateRole(candidate != null ? (candidate.getProfession() != null ? candidate.getProfession() : candidate.getCourse()) : null)
                            .candidateCity(candidate != null ? candidate.getCity() : null)
                            .candidateState(candidate != null ? candidate.getState() : null)
                            .candidateQualification(candidate != null ? candidate.getQualification() : null)
                            .candidateCouncil(candidate != null ? (candidate.getCouncil() != null ? candidate.getCouncil() : candidate.getCouncilState()) : null)
                            .candidateBio(candidate != null ? candidate.getBio() : null)
                            .candidateNoticePeriod(candidate != null ? candidate.getNoticePeriod() : null)
                            .expectedSalaryMinPaise(candidate != null ? candidate.getExpectedSalaryMinPaise() : null)
                            .expectedSalaryMaxPaise(candidate != null ? candidate.getExpectedSalaryMaxPaise() : null)
                            .candidateExperience(candidate != null ? candidate.getExperienceYears() : null)
                            .candidatePhone(candidateUser != null ? candidateUser.getPhone() : null)
                            .candidateEmail(candidateUser != null ? candidateUser.getEmail() : null)
                            .candidateDocuments(docs)
                            .councilVerified(candidate != null && "VERIFIED".equalsIgnoreCase(candidate.getKycStatus()))
                            .regNumber(candidate != null ? candidate.getRegNumber() : null)
                            .facilityName(facility != null ? facility.getHospitalName() : "Hospital Partner")
                            .department(job != null ? job.getDepartment() : "Clinical")
                            .city(job != null ? job.getCity() : "Location")
                            .status(app.getStatus())
                            .matchScore(app.getMatchScore())
                            .appliedAt(app.getAppliedAt())
                            .interviewScheduledAt(app.getInterviewScheduledAt())
                            .interviewLocationOrLink(app.getInterviewLocationOrLink())
                            .withdrawalReason(app.getWithdrawalReason())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void screenApplication(CustomUserPrincipal principal, UUID applicationId, AdminApplicationScreeningRequest request) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        boolean shortlist = "SHORTLIST".equalsIgnoreCase(request.getAction());
        application.setStatus(shortlist ? "SHORTLISTED" : "REJECTED");
        if (!shortlist && request.getNotes() != null) {
            application.setWithdrawalReason("Admin Screening: " + request.getNotes());
        }
        JobApplication saved = jobApplicationRepository.save(application);

        JobPosting job = jobPostingRepository.findById(application.getJobId()).orElse(null);
        CandidateProfile candidate = candidateProfileRepository.findById(application.getCandidateId()).orElse(null);

        // Notify Candidate
        if (candidate != null) {
            Notification candidateNotif = Notification.builder()
                    .userId(candidate.getUserId())
                    .title("Application " + (shortlist ? "Shortlisted!" : "Update"))
                    .message(shortlist
                            ? "Congratulations! Your application for " + (job != null ? job.getTitle() : "position") + " has been shortlisted by FALE Admin and forwarded to the hospital."
                            : "Your application for " + (job != null ? job.getTitle() : "position") + " was not shortlisted: " + (request.getNotes() != null ? request.getNotes() : "Guidelines not met."))
                    .type("APPLICATION")
                    .actionUrl("/applications")
                    .read(false)
                    .build();
            notificationRepository.save(candidateNotif);
        }

        // Notify Facility if shortlisted
        if (shortlist && job != null) {
            FacilityProfile facility = facilityProfileRepository.findById(job.getFacilityId()).orElse(null);
            if (facility != null) {
                Notification facilityNotif = Notification.builder()
                        .userId(facility.getUserId())
                        .title("New Shortlisted Applicant")
                        .message("FALE Admin shortlisted a verified candidate for " + job.getTitle() + " (Match: " + saved.getMatchScore() + "%).")
                        .type("APPLICATION")
                        .actionUrl("/employer/applicants")
                        .read(false)
                        .build();
                notificationRepository.save(facilityNotif);
            }
        }

        auditLogService.logEvent(principal.getId(), "ADMIN_APPLICATION_SCREENED", "JobApplication", saved.getId().toString(), null);
    }

    @Transactional(readOnly = true)
    public List<ShiftClaimDto> getPendingPayouts() {
        return shiftClaimRepository.findByPayoutStatus("PENDING_DISBURSEMENT").stream()
                .map(c -> {
                    LocumShift s = locumShiftRepository.findById(c.getShiftId()).orElse(null);
                    FacilityProfile f = s != null ? facilityProfileRepository.findById(s.getFacilityId()).orElse(null) : null;
                    return ShiftClaimDto.builder()
                            .id(c.getId())
                            .shiftId(c.getShiftId())
                            .shiftTitle(s != null ? s.getTitle() : "Locum Duty")
                            .facilityName(f != null ? f.getHospitalName() : "Hospital")
                            .department(s != null ? s.getDepartment() : "Department")
                            .hours(s != null ? s.getHours() : 8)
                            .status(c.getStatus())
                            .grossAmountPaise(c.getGrossAmount().multiply(BigDecimal.valueOf(100)).longValue())
                            .platformFeePaise(c.getPlatformFee().multiply(BigDecimal.valueOf(100)).longValue())
                            .tdsAmountPaise(c.getTdsAmount().multiply(BigDecimal.valueOf(100)).longValue())
                            .netAmountPaise(c.getNetAmount().multiply(BigDecimal.valueOf(100)).longValue())
                            .payoutStatus(c.getPayoutStatus())
                            .payoutSlipRef(c.getPayoutSlipRef())
                            .completedAt(c.getCompletedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void disbursePayout(CustomUserPrincipal principal, UUID claimId, AdminPayoutApprovalRequest request) {
        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        if (!"PENDING_DISBURSEMENT".equalsIgnoreCase(claim.getPayoutStatus())) {
            throw new BadRequestException("Claim is not pending disbursement. Current status: " + claim.getPayoutStatus());
        }

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        claim.setPayoutStatus("DISBURSED");
        claim.setUtr(request.getUtr().trim().toUpperCase());
        claim.setDisbursedAt(Instant.now());
        shiftClaimRepository.save(claim);

        // Update facility wallet: release locked escrow to disbursed
        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));

        wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(claim.getGrossAmount()));
        wallet.setTotalDisbursed(wallet.getTotalDisbursed().add(claim.getGrossAmount()));
        facilityWalletRepository.save(wallet);

        String refNum = "DISB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type("disbursement")
                .amount(claim.getGrossAmount().negate())
                .balanceAfter(wallet.getAvailableBalance())
                .status("Completed")
                .refNumber(refNum)
                .utr(request.getUtr().trim().toUpperCase())
                .title("Disbursement: " + shift.getTitle())
                .subtitle("Net credited to clinician: ₹" + claim.getNetAmount() + " (UTR: " + request.getUtr() + ")")
                .build();
        walletTransactionRepository.save(tx);

        // Notify Clinician
        CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
        if (candidate != null) {
            Notification notif = Notification.builder()
                    .userId(candidate.getUserId())
                    .title("Escrow Payout Disbursed")
                    .message("₹" + claim.getNetAmount() + " has been disbursed to your verified payout method. Banking UTR: " + request.getUtr())
                    .type("PAYOUT")
                    .actionUrl("/earnings")
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        }

        auditLogService.logEvent(principal.getId(), "ADMIN_SHIFT_PAYOUT_DISBURSED", "ShiftClaim", claimId.toString(), null);
    }

    @Transactional(readOnly = true)
    public List<HuddlePostDto> getFlaggedPosts() {
        return huddlePostRepository.findFlaggedPosts().stream()
                .map(huddleService::mapPostToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HuddlePostDto> getReviewCases() {
        return huddlePostRepository.findReviewPosts().stream()
                .map(huddleService::mapPostToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public HuddlePostDto approveReviewCase(CustomUserPrincipal principal, UUID postId) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));
        post.setStatus("LIVE");
        HuddlePost saved = huddlePostRepository.save(post);
        auditLogService.logEvent(principal.getId(), "ADMIN_HUDDLE_CASE_APPROVED", "HuddlePost", postId.toString(), null);
        return huddleService.mapPostToDto(saved);
    }

    @Transactional
    public void rejectReviewCase(CustomUserPrincipal principal, UUID postId) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));
        post.setStatus("REMOVED");
        post.setDeletedAt(Instant.now());
        huddlePostRepository.save(post);
        auditLogService.logEvent(principal.getId(), "ADMIN_HUDDLE_CASE_REJECTED", "HuddlePost", postId.toString(), null);
    }

    @Transactional
    public void resolvePostFlag(CustomUserPrincipal principal, UUID postId, boolean remove) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));

        if (remove) {
            post.setStatus("REMOVED");
            post.setDeletedAt(Instant.now());
        } else {
            post.setStatus("LIVE");
            post.setFlagCount(0);
        }
        huddlePostRepository.save(post);

        huddleFlagRepository.findByResolvedFalseOrderByCreatedAtDesc().stream()
                .filter(f -> f.getPostId().equals(postId))
                .forEach(f -> {
                    f.setResolved(true);
                    huddleFlagRepository.save(f);
                });

        auditLogService.logEvent(principal.getId(), "ADMIN_HUDDLE_FLAG_RESOLVED", "HuddlePost", postId.toString(), null);
    }

    @Transactional
    public HuddlePostDto togglePinHuddlePost(UUID postId) {
        return huddleService.togglePinPost(postId);
    }

    @Transactional(readOnly = true)
    public List<MentorApplicationDto> getMentorApplications() {
        return mentorApplicationRepository.findAllByOrderByAppliedAtDesc().stream()
                .map(m -> MentorApplicationDto.builder()
                        .id(m.getId())
                        .candidateId(m.getCandidateId())
                        .userId(m.getUserId())
                        .name(m.getName())
                        .profession(m.getProfession())
                        .specialty(m.getSpecialty())
                        .yearsExperience(m.getYearsExperience())
                        .organization(m.getOrganization())
                        .status(m.getStatus())
                        .verified("APPROVED".equalsIgnoreCase(m.getStatus()))
                        .appliedAt(m.getAppliedAt())
                        .reviewedAt(m.getReviewedAt())
                        .adminNotes(m.getAdminNotes())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public MentorApplicationDto reviewMentorApplication(CustomUserPrincipal principal, UUID applicationId, boolean approve, String notes) {
        MentorApplication app = mentorApplicationRepository.findById(applicationId)
                .orElseGet(() -> mentorApplicationRepository.findByCandidateId(applicationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Mentor application not found with id: " + applicationId)));

        app.setStatus(approve ? "APPROVED" : "REJECTED");
        app.setReviewedAt(Instant.now());
        app.setAdminNotes(notes);
        MentorApplication saved = mentorApplicationRepository.save(app);

        if (app.getCandidateId() != null) {
            candidateProfileRepository.findById(app.getCandidateId()).ifPresent(cp -> {
                cp.setIsMentor(approve);
                cp.setMentorStatus(approve ? "APPROVED" : "REJECTED");
                candidateProfileRepository.save(cp);
            });
        }

        auditLogService.logEvent(principal.getId(), approve ? "ADMIN_MENTOR_APPROVED" : "ADMIN_MENTOR_REJECTED", "MentorApplication", applicationId.toString(), null);

        return MentorApplicationDto.builder()
                .id(saved.getId())
                .candidateId(saved.getCandidateId())
                .userId(saved.getUserId())
                .name(saved.getName())
                .profession(saved.getProfession())
                .specialty(saved.getSpecialty())
                .yearsExperience(saved.getYearsExperience())
                .organization(saved.getOrganization())
                .status(saved.getStatus())
                .verified(approve)
                .appliedAt(saved.getAppliedAt())
                .reviewedAt(saved.getReviewedAt())
                .adminNotes(saved.getAdminNotes())
                .build();
    }

    @Transactional
    public MentorApplicationDto toggleMentorSeal(CustomUserPrincipal principal, UUID id) {
        MentorApplication app = mentorApplicationRepository.findById(id)
                .orElseGet(() -> mentorApplicationRepository.findByCandidateId(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Mentor not found with id: " + id)));

        boolean willApprove = !"APPROVED".equalsIgnoreCase(app.getStatus());
        app.setStatus(willApprove ? "APPROVED" : "REJECTED");
        app.setReviewedAt(Instant.now());
        MentorApplication saved = mentorApplicationRepository.save(app);

        if (app.getCandidateId() != null) {
            candidateProfileRepository.findById(app.getCandidateId()).ifPresent(cp -> {
                cp.setIsMentor(willApprove);
                cp.setMentorStatus(willApprove ? "APPROVED" : "REJECTED");
                candidateProfileRepository.save(cp);
            });
        }

        auditLogService.logEvent(principal.getId(), willApprove ? "ADMIN_MENTOR_TOGGLED_ACTIVE" : "ADMIN_MENTOR_TOGGLED_SUSPENDED", "MentorApplication", id.toString(), null);

        return MentorApplicationDto.builder()
                .id(saved.getId())
                .candidateId(saved.getCandidateId())
                .userId(saved.getUserId())
                .name(saved.getName())
                .profession(saved.getProfession())
                .specialty(saved.getSpecialty())
                .yearsExperience(saved.getYearsExperience())
                .organization(saved.getOrganization())
                .status(saved.getStatus())
                .verified(willApprove)
                .appliedAt(saved.getAppliedAt())
                .reviewedAt(saved.getReviewedAt())
                .adminNotes(saved.getAdminNotes())
                .build();
    }

    @Transactional
    public MentorApplicationDto onboardMentor(CustomUserPrincipal principal, MentorApplicationDto request) {
        MentorApplication app = MentorApplication.builder()
                .name(request.getName() != null ? request.getName().trim() : "Clinical Mentor")
                .profession(request.getProfession())
                .specialty(request.getSpecialty())
                .yearsExperience(request.getYearsExperience() > 0 ? request.getYearsExperience() : 3)
                .organization(request.getOrganization())
                .status("APPROVED")
                .appliedAt(Instant.now())
                .reviewedAt(Instant.now())
                .adminNotes("Directly onboarded and certified by Administrator")
                .build();

        MentorApplication saved = mentorApplicationRepository.save(app);
        auditLogService.logEvent(principal.getId(), "ADMIN_MENTOR_ONBOARDED", "MentorApplication", saved.getId().toString(), null);

        return MentorApplicationDto.builder()
                .id(saved.getId())
                .name(saved.getName())
                .profession(saved.getProfession())
                .specialty(saved.getSpecialty())
                .yearsExperience(saved.getYearsExperience())
                .organization(saved.getOrganization())
                .status(saved.getStatus())
                .verified(true)
                .appliedAt(saved.getAppliedAt())
                .reviewedAt(saved.getReviewedAt())
                .adminNotes(saved.getAdminNotes())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> getAuditLogs() {
        return auditEventRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<LocumShiftDto> getShifts(String status) {
        List<LocumShift> list;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            list = locumShiftRepository.findAll();
        } else if ("PENDING".equalsIgnoreCase(status) || "PENDING_APPROVAL".equalsIgnoreCase(status)) {
            list = locumShiftRepository.findByStatus("PENDING_APPROVAL");
        } else if ("OPEN".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status) || "LIVE".equalsIgnoreCase(status) || "PUBLISHED".equalsIgnoreCase(status)) {
            list = locumShiftRepository.findByStatus("OPEN");
        } else {
            list = locumShiftRepository.findByStatus(status.trim().toUpperCase());
        }

        return list.stream()
                .map(s -> {
                    FacilityProfile facility = facilityProfileRepository.findById(s.getFacilityId()).orElse(null);
                    BigDecimal totalPay = s.getRatePerHour().add(s.getSurgeRatePerHour()).multiply(BigDecimal.valueOf(s.getHours()));
                    return LocumShiftDto.builder()
                            .id(s.getId())
                            .facilityId(s.getFacilityId())
                            .facilityName(facility != null ? facility.getHospitalName() : "Partner Facility")
                            .city(facility != null && facility.getCity() != null ? facility.getCity() : "Pan-India")
                            .title(s.getTitle())
                            .department(s.getDepartment())
                            .startAt(s.getStartAt())
                            .endAt(s.getEndAt())
                            .hours(s.getHours())
                            .ratePerHourPaise(s.getRatePerHour().multiply(BigDecimal.valueOf(100)).longValue())
                            .surgeRatePerHourPaise(s.getSurgeRatePerHour().multiply(BigDecimal.valueOf(100)).longValue())
                            .surgeLabel(s.getSurgeLabel())
                            .totalPayPaise(totalPay.multiply(BigDecimal.valueOf(100)).longValue())
                            .openings(s.getOpenings())
                            .slotsClaimed(s.getSlotsClaimed())
                            .urgent(s.isUrgent())
                            .status(s.getStatus())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LocumShiftDto> getPendingShifts() {
        return getShifts("PENDING_APPROVAL");
    }

    @Transactional
    public void reviewShift(CustomUserPrincipal principal, UUID shiftId, AdminKycReviewRequest request) {
        LocumShift shift = locumShiftRepository.findByIdWithLock(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        boolean approve = "APPROVE".equalsIgnoreCase(request.getAction());
        if (approve) {
            shift.setStatus("OPEN");
            locumShiftRepository.save(shift);

            FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
            if (facility != null) {
                Notification notif = Notification.builder()
                        .userId(facility.getUserId())
                        .title("Locum Shift Approved & Live")
                        .message("Your locum shift \"" + shift.getTitle() + "\" has been approved by compliance admin and is now open for claims.")
                        .type("SHIFT")
                        .actionUrl("/employer")
                        .read(false)
                        .build();
                notificationRepository.save(notif);
            }

            // Urgent On-Call Shift Broadcast to local clinicians
            if (facility != null && (shift.isUrgent() || (shift.getSurgeRatePerHour() != null && shift.getSurgeRatePerHour().compareTo(BigDecimal.ZERO) > 0))) {
                String surgeInfo = shift.getSurgeLabel() != null && !shift.getSurgeLabel().isBlank()
                        ? " (" + shift.getSurgeLabel() + " +₹" + shift.getSurgeRatePerHour() + "/hr surge)"
                        : "";
                List<CandidateProfile> localCandidates = candidateProfileRepository.findAll();
                for (CandidateProfile cand : localCandidates) {
                    if (cand.getCity() != null && cand.getCity().equalsIgnoreCase(facility.getCity())) {
                        Notification urgentNotif = Notification.builder()
                                .userId(cand.getUserId())
                                .title("Urgent On-Call Shift: " + shift.getTitle())
                                .message(facility.getHospitalName() + " posted an emergency duty: " + shift.getTitle() + surgeInfo + " in " + facility.getCity() + ". Guaranteed escrow payout.")
                                .type("SHIFT_URGENT")
                                .actionUrl("/shifts")
                                .read(false)
                                .build();
                        notificationRepository.save(urgentNotif);
                    }
                }
            }

            auditLogService.logEvent(principal.getId(), "ADMIN_SHIFT_APPROVED", "LocumShift", shiftId.toString(), null);
        } else {
            shift.setStatus("REJECTED");
            locumShiftRepository.save(shift);

            FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId()).orElse(null);
            if (wallet != null) {
                BigDecimal effectiveHourlyRate = shift.getRatePerHour().add(shift.getSurgeRatePerHour());
                BigDecimal totalEscrow = effectiveHourlyRate.multiply(BigDecimal.valueOf(shift.getHours()))
                        .multiply(BigDecimal.valueOf(shift.getOpenings()));

                wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(totalEscrow));
                wallet.setAvailableBalance(wallet.getAvailableBalance().add(totalEscrow));
                facilityWalletRepository.save(wallet);

                String refundRef = "REFUND-SHIFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                WalletTransaction tx = WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .type("refund")
                        .amount(totalEscrow)
                        .balanceAfter(wallet.getAvailableBalance())
                        .status("Completed")
                        .refNumber(refundRef)
                        .title("Escrow Refund: " + shift.getTitle())
                        .subtitle("Shift rejected by admin: " + (request.getNotes() != null ? request.getNotes() : "Guidelines mismatch"))
                        .build();
                walletTransactionRepository.save(tx);
            }

            FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
            if (facility != null) {
                Notification notif = Notification.builder()
                        .userId(facility.getUserId())
                        .title("Locum Shift Rejected")
                        .message("Your locum shift \"" + shift.getTitle() + "\" was rejected: " + (request.getNotes() != null ? request.getNotes() : "Compliance mismatch") + ". Full escrow refunded.")
                        .type("SHIFT")
                        .actionUrl("/employer")
                        .read(false)
                        .build();
                notificationRepository.save(notif);
            }
            auditLogService.logEvent(principal.getId(), "ADMIN_SHIFT_REJECTED", "LocumShift", shiftId.toString(), null);
        }
    }

    @Transactional(readOnly = true)
    public List<ShiftClaimDto> getPendingShiftClaims() {
        return shiftClaimRepository.findByStatus("CLAIM_PENDING_ADMIN").stream()
                .map(c -> {
                    LocumShift shift = locumShiftRepository.findById(c.getShiftId()).orElse(null);
                    FacilityProfile facility = shift != null ? facilityProfileRepository.findById(shift.getFacilityId()).orElse(null) : null;
                    CandidateProfile candidate = candidateProfileRepository.findById(c.getCandidateId()).orElse(null);
                    return ShiftClaimDto.builder()
                            .id(c.getId())
                            .shiftId(c.getShiftId())
                            .candidateId(c.getCandidateId())
                            .candidateName(candidate != null ? candidate.getName() : "Candidate")
                            .candidateRole(candidate != null ? candidate.getProfession() : "Clinical Practitioner")
                            .candidateCity(candidate != null ? candidate.getCity() : "Pan-India")
                            .facilityName(facility != null ? facility.getHospitalName() : "Partner Facility")
                            .shiftTitle(shift != null ? shift.getTitle() : "Locum Shift")
                            .department(shift != null ? shift.getDepartment() : "General")
                            .hours(shift != null ? shift.getHours() : 8)
                            .startAt(shift != null ? shift.getStartAt() : null)
                            .endAt(shift != null ? shift.getEndAt() : null)
                            .grossAmountPaise(c.getGrossAmount().multiply(BigDecimal.valueOf(100)).longValue())
                            .platformFeePaise(c.getPlatformFee().multiply(BigDecimal.valueOf(100)).longValue())
                            .tdsAmountPaise(c.getTdsAmount().multiply(BigDecimal.valueOf(100)).longValue())
                            .netAmountPaise(c.getNetAmount().multiply(BigDecimal.valueOf(100)).longValue())
                            .status(c.getStatus())
                            .payoutStatus(c.getPayoutStatus())
                            .claimedAt(c.getClaimedAt())
                            .matchScore(85)
                            .rejectionReason(null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void screenShiftClaim(CustomUserPrincipal principal, UUID claimId, AdminApplicationScreeningRequest request) {
        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        LocumShift shift = locumShiftRepository.findByIdWithLock(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);

        boolean confirm = "SHORTLIST".equalsIgnoreCase(request.getAction()) || "APPROVE".equalsIgnoreCase(request.getAction());
        if (confirm) {
            claim.setStatus("CONFIRMED");
            shiftClaimRepository.save(claim);

            if (candidate != null) {
                Notification notif = Notification.builder()
                        .userId(candidate.getUserId())
                        .title("Shift Claim Confirmed by Admin")
                        .message("Your claim for " + shift.getTitle() + " at " + (facility != null ? facility.getHospitalName() : "Hospital") + " has been confirmed. Payout guarantee held in escrow.")
                        .type("SHIFT")
                        .actionUrl("/shifts")
                        .read(false)
                        .build();
                notificationRepository.save(notif);
            }
            if (facility != null) {
                Notification facNotif = Notification.builder()
                        .userId(facility.getUserId())
                        .title("Clinician Confirmed for Shift")
                        .message("FALE Admin confirmed verified clinician " + (candidate != null ? candidate.getName() : "Candidate") + " for " + shift.getTitle() + ".")
                        .type("SHIFT")
                        .actionUrl("/employer/applicants")
                        .read(false)
                        .build();
                notificationRepository.save(facNotif);
            }
            auditLogService.logEvent(principal.getId(), "ADMIN_SHIFT_CLAIM_CONFIRMED", "ShiftClaim", claimId.toString(), null);
        } else {
            claim.setStatus("REJECTED");
            claim.setPayoutStatus("REFUNDED");
            shiftClaimRepository.save(claim);

            shift.setSlotsClaimed(Math.max(0, shift.getSlotsClaimed() - 1));
            shift.setStatus("OPEN");
            locumShiftRepository.save(shift);

            String reason = request.getNotes() != null ? request.getNotes() : "Ineligible for this shift";
            if (candidate != null) {
                Notification notif = Notification.builder()
                        .userId(candidate.getUserId())
                        .title("Shift Claim Not Approved")
                        .message("Your claim for " + shift.getTitle() + " could not be approved: " + reason)
                        .type("SHIFT")
                        .actionUrl("/shifts")
                        .read(false)
                        .build();
                notificationRepository.save(notif);
            }
            auditLogService.logEvent(principal.getId(), "ADMIN_SHIFT_CLAIM_REJECTED", "ShiftClaim", claimId.toString(), null);
        }
    }

    private CandidateProfileDto mapCandidateToDto(CandidateProfile c) {
        User u = userRepository.findById(c.getUserId()).orElse(null);
        List<CandidateDocumentDto> docs = candidateDocumentRepository.findByCandidateId(c.getId()).stream()
                .map(d -> CandidateDocumentDto.builder()
                        .id(d.getId())
                        .documentType(d.getDocumentType())
                        .originalFileName(d.getOriginalFileName())
                        .fileUrl(d.getFileUrl())
                        .fileSizeBytes(d.getFileSizeBytes())
                        .mimeType(d.getMimeType())
                        .verified(d.isVerified())
                        .createdAt(d.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return CandidateProfileDto.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .phone(u != null ? u.getPhone() : null)
                .email(u != null ? u.getEmail() : null)
                .name(c.getName())
                .gender(c.getGender())
                .dob(c.getDob())
                .city(c.getCity())
                .state(c.getState())
                .pincode(c.getPincode())
                .addressLine(c.getAddressLine())
                .candidateLevel(u != null ? u.getCandidateLevel() : null)
                .isStudent(u != null && "STUDENT".equalsIgnoreCase(u.getCandidateLevel()))
                .course(c.getCourse())
                .college(c.getCollege())
                .studyYear(c.getStudyYear())
                .universityRegNo(c.getUniversityRegNo())
                .expectedGraduationYear(c.getExpectedGraduationYear())
                .profession(c.getProfession())
                .specialty(c.getSpecialty())
                .subSpecialties(jsonToList(c.getSubSpecialties()))
                .qualification(c.getQualification())
                .council(c.getCouncil())
                .councilState(c.getCouncilState())
                .regNumber(c.getRegNumber())
                .regYear(c.getRegYear())
                .experienceYears(c.getExperienceYears())
                .bio(c.getBio())
                .expectedSalaryMinPaise(c.getExpectedSalaryMinPaise())
                .expectedSalaryMaxPaise(c.getExpectedSalaryMaxPaise())
                .currentSalary(c.getCurrentSalary())
                .noticePeriod(c.getNoticePeriod())
                .employmentType(c.getEmploymentType())
                .availability(c.getAvailability())
                .skills(jsonToList(c.getSkills()))
                .languages(jsonToList(c.getLanguages()))
                .preferredCities(jsonToList(c.getPreferredCities()))
                .openToWork(c.getOpenToWork())
                .relocate(c.getRelocate())
                .kycStatus(c.getKycStatus())
                .kycNotes(c.getKycNotes())
                .isMentor(Boolean.TRUE.equals(c.getIsMentor()))
                .mentorStatus(c.getMentorStatus() != null ? c.getMentorStatus() : "NONE")
                .verifiedAt(c.getVerifiedAt())
                .createdAt(c.getCreatedAt())
                .documents(docs)
                .build();
    }

    private List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) return java.util.Collections.emptyList();
        String clean = json.trim();
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (clean.isBlank()) return java.util.Collections.emptyList();
        return java.util.Arrays.stream(clean.split(","))
                .map(s -> s.replace("\"", "").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private FacilityProfileDto mapFacilityToDto(FacilityProfile f) {
        List<FacilityDocumentDto> docs = Collections.emptyList();
        if (f.getId() != null) {
            try {
                docs = facilityDocumentRepository.findByFacilityId(f.getId()).stream()
                        .map(d -> FacilityDocumentDto.builder()
                                .id(d.getId())
                                .documentType(d.getDocumentType())
                                .originalFileName(d.getOriginalFileName())
                                .fileUrl(d.getFileUrl())
                                .fileSizeBytes(d.getFileSizeBytes())
                                .mimeType(d.getMimeType())
                                .verified(d.isVerified())
                                .createdAt(d.getCreatedAt())
                                .build())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to load facility docs for admin: {}", e.getMessage());
            }
        }

        return FacilityProfileDto.builder()
                .id(f.getId())
                .userId(f.getUserId())
                .hospitalName(f.getHospitalName())
                .facilityType(f.getFacilityType())
                .size(f.getSize())
                .about(f.getAbout())
                .website(f.getWebsite())
                .city(f.getCity())
                .state(f.getState())
                .pincode(f.getPincode())
                .addressLine(f.getAddressLine())
                .beds(f.getBeds())
                .icuBeds(f.getIcuBeds())
                .licenseNumber(f.getLicenseNumber())
                .gst(f.getGst())
                .nabhNumber(f.getNabhNumber())
                .accreditationLevel(f.getAccreditationLevel())
                .contactPerson(f.getContactPerson())
                .designation(f.getDesignation())
                .contactPhone(f.getContactPhone())
                .contactEmail(f.getContactEmail())
                .departments(jsonToList(f.getDepartments()))
                .hiringNeeds(jsonToList(f.getHiringNeeds()))
                .facilityPhotos(jsonToList(f.getFacilityPhotos()))
                .logoUrl(f.getLogoUrl())
                .documents(docs)
                .kycStatus(f.getKycStatus())
                .kycNotes(f.getKycNotes())
                .verifiedAt(f.getVerifiedAt())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
