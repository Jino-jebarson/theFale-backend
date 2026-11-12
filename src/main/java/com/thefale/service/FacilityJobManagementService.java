package com.thefale.service;

import com.thefale.dto.CandidateDocumentDto;
import com.thefale.dto.JobApplicationDto;
import com.thefale.dto.JobPostingDto;
import com.thefale.dto.PostJobRequest;
import com.thefale.dto.UpdateApplicationStatusRequest;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ForbiddenException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityJobManagementService {

    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityService facilityService;
    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final LocumShiftRepository locumShiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UnlockedCandidateRepository unlockedCandidateRepository;
    private final UserRepository userRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ShiftClaimService shiftClaimService;

    @Transactional
    public JobPostingDto postJob(CustomUserPrincipal principal, PostJobRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        if (request.getSalaryMinPaise() > request.getSalaryMaxPaise()) {
            throw new BadRequestException("Minimum salary cannot exceed maximum salary");
        }

        BigDecimal minRupees = BigDecimal.valueOf(request.getSalaryMinPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal maxRupees = BigDecimal.valueOf(request.getSalaryMaxPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String resolvedLocation = (request.getWorkLocation() != null && !request.getWorkLocation().isBlank())
                ? request.getWorkLocation().trim()
                : (request.getCity() != null ? request.getCity().trim() : facility.getCity());

        JobPosting job = JobPosting.builder()
                .facilityId(facility.getId())
                .title(request.getTitle().trim())
                .department(request.getDepartment().trim())
                .employmentType(request.getEmploymentType().trim())
                .internship(request.isInternship())
                .city(resolvedLocation)
                .openings(request.getOpenings())
                .salaryMin(minRupees)
                .salaryMax(maxRupees)
                .experienceRequired(request.getExperienceRequired())
                .qualificationRequired(request.getQualificationRequired())
                .description(request.getDescription().trim())
                .status("PENDING_APPROVAL")
                .build();

        JobPosting saved = jobPostingRepository.save(job);
        auditLogService.logEvent(principal.getId(), "JOB_POSTED", "JobPosting", saved.getId().toString(), null);

        return mapPostingToDto(saved, facility);
    }

    @Transactional
    public List<JobPostingDto> getMyJobs(CustomUserPrincipal principal) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        return jobPostingRepository.findByFacilityIdAndDeletedAtIsNull(facility.getId()).stream()
                .map(job -> mapPostingToDto(job, facility))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<JobApplicationDto> getFacilityApplicants(CustomUserPrincipal principal, UUID jobId, String statusFilter) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        List<JobPosting> facilityJobs;
        if (jobId != null) {
            JobPosting job = jobPostingRepository.findById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));
            if (!job.getFacilityId().equals(facility.getId())) {
                throw new ForbiddenException("Unauthorized to view applicants for this job");
            }
            facilityJobs = List.of(job);
        } else {
            facilityJobs = jobPostingRepository.findByFacilityIdAndDeletedAtIsNull(facility.getId());
        }

        List<UUID> jobIds = facilityJobs.stream().map(JobPosting::getId).collect(Collectors.toList());
        Set<String> allowedShortlistedStatuses = Set.of("SHORTLISTED", "INTERVIEW", "OFFER", "OFFERED", "HIRED", "REJECTED", "POSITION_FILLED");

        List<JobApplicationDto> results = jobIds.isEmpty()
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(jobApplicationRepository.findByJobIdIn(jobIds).stream()
                        .filter(app -> allowedShortlistedStatuses.contains(app.getStatus().toUpperCase()))
                        .filter(app -> statusFilter == null || statusFilter.equalsIgnoreCase("All") || app.getStatus().equalsIgnoreCase(statusFilter))
                        .map(app -> {
                            JobPosting job = facilityJobs.stream().filter(j -> j.getId().equals(app.getJobId())).findFirst().orElse(null);
                            return mapApplicationToDto(app, job, facility.getId());
                        })
                        .collect(Collectors.toList()));

        // Also include confirmed/shortlisted locum shift claims
        if (jobId == null) {
            List<LocumShift> facilityShifts = locumShiftRepository.findByFacilityIdOrderByStartAtDesc(facility.getId());
            List<UUID> shiftIds = facilityShifts.stream().map(LocumShift::getId).collect(Collectors.toList());
            if (!shiftIds.isEmpty()) {
                Set<String> allowedShiftStatuses = Set.of(
                        "CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "TIMESHEET_SUBMITTED", "COMPLETED", "CLAIMED", "CLAIM_PENDING_ADMIN", "REJECTED", "SLOT_FILLED", "DISPUTED", "NO_SHOW"
                );
                List<JobApplicationDto> shiftApplicants = shiftClaimRepository.findByShiftIdIn(shiftIds).stream()
                        .filter(claim -> claim.getStatus() != null && allowedShiftStatuses.contains(claim.getStatus().toUpperCase()))
                        .filter(claim -> statusFilter == null || statusFilter.equalsIgnoreCase("All")
                                || claim.getStatus().equalsIgnoreCase(statusFilter)
                                || (statusFilter.equalsIgnoreCase("SHORTLISTED") && ("CLAIMED".equalsIgnoreCase(claim.getStatus()) || "CONFIRMED".equalsIgnoreCase(claim.getStatus())))
                                || (statusFilter.equalsIgnoreCase("TIMESHEET_SUBMITTED") && "TIMESHEET_SUBMITTED".equalsIgnoreCase(claim.getStatus())))
                        .map(claim -> {
                            LocumShift shift = facilityShifts.stream().filter(s -> s.getId().equals(claim.getShiftId())).findFirst().orElse(null);
                            return mapShiftClaimToDto(claim, shift, facility.getId());
                        })
                        .collect(Collectors.toList());
                results.addAll(shiftApplicants);
            }
        }

        return results;
    }

    @Transactional
    public JobApplicationDto updateApplicationStatus(CustomUserPrincipal principal, UUID applicationId, UpdateApplicationStatusRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        java.util.Optional<ShiftClaim> shiftClaimOpt = shiftClaimRepository.findByIdWithLock(applicationId);
        if (shiftClaimOpt.isPresent()) {
            ShiftClaim claim = shiftClaimOpt.get();
            LocumShift shift = locumShiftRepository.findByIdWithLock(claim.getShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
            if (!shift.getFacilityId().equals(facility.getId())) {
                throw new ForbiddenException("Unauthorized to manage shift claim for this facility");
            }
            Set<String> nonFinalStatuses = Set.of("CANCELLED", "WITHDRAWN", "REJECTED", "SLOT_FILLED", "NO_SHOW");

            if ("COMPLETED".equalsIgnoreCase(request.getStatus()) || "TIMESHEET_VERIFIED".equalsIgnoreCase(request.getStatus())) {
                com.thefale.dto.CompleteShiftRequest completeReq = new com.thefale.dto.CompleteShiftRequest();
                completeReq.setActualHours(request.getActualHours());
                shiftClaimService.completeShift(principal, claim.getId(), completeReq);
                ShiftClaim updated = shiftClaimRepository.findById(claim.getId()).orElse(claim);
                return mapShiftClaimToDto(updated, shift, facility.getId());
            } else if ("REJECTED".equalsIgnoreCase(request.getStatus()) || "NO_SHOW".equalsIgnoreCase(request.getStatus())) {
                boolean isNoShow = "NO_SHOW".equalsIgnoreCase(request.getStatus());
                claim.setStatus(isNoShow ? "NO_SHOW" : "REJECTED");
                claim.setPayoutStatus("REFUNDED");
                claim.setCheckInPin(null);
                shiftClaimRepository.save(claim);

                List<ShiftClaim> allShiftClaims = shiftClaimRepository.findByShiftId(shift.getId());
                long activeCount = allShiftClaims.stream()
                        .filter(c -> c.getStatus() != null && !nonFinalStatuses.contains(c.getStatus().toUpperCase()))
                        .count();
                shift.setSlotsClaimed((int) activeCount);
                if (activeCount < shift.getOpenings() && !Set.of("CANCELLED", "REJECTED", "COMPLETED", "EXPIRED").contains(shift.getStatus().toUpperCase())) {
                    shift.setStatus("OPEN");
                }
                locumShiftRepository.save(shift);

                CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
                if (candidate != null) {
                    Notification notification = Notification.builder()
                            .userId(candidate.getUserId())
                            .title((isNoShow ? "Marked No-Show: " : "Shift Claim Declined: ") + shift.getTitle())
                            .message(isNoShow
                                    ? facility.getHospitalName() + " marked you as No-Show for " + shift.getTitle() + "."
                                    : "Your claim for " + shift.getTitle() + " at " + facility.getHospitalName() + " was not accepted" + (request.getNotes() != null && !request.getNotes().isBlank() ? ": " + request.getNotes() : "."))
                            .type("SHIFT")
                            .actionUrl("/shifts")
                            .read(false)
                            .build();
                    notificationRepository.save(notification);
                }
            } else if ("CONFIRMED".equalsIgnoreCase(request.getStatus()) || "CONFIRM_SHIFT".equalsIgnoreCase(request.getStatus()) || "HIRED".equalsIgnoreCase(request.getStatus())) {
                if (!Set.of("CLAIMED", "CLAIM_PENDING_ADMIN").contains(claim.getStatus().toUpperCase())) {
                    throw new BadRequestException("Cannot confirm shift claim from status " + claim.getStatus());
                }
                String pin = (claim.getCheckInPin() != null && !claim.getCheckInPin().isBlank())
                        ? claim.getCheckInPin()
                        : ShiftClaimService.generateCheckInPin();
                claim.setStatus("CONFIRMED");
                claim.setCheckInPin(pin);
                shiftClaimRepository.save(claim);

                CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
                if (candidate != null) {
                    Notification notification = Notification.builder()
                            .userId(candidate.getUserId())
                            .title("Shift Confirmed: " + shift.getTitle())
                            .message("Your slot for " + shift.getTitle() + " at " + facility.getHospitalName() + " is confirmed. Your floor Check-In PIN: " + pin)
                            .type("SHIFT")
                            .actionUrl("/shifts")
                            .read(false)
                            .build();
                    notificationRepository.save(notification);
                }

                List<ShiftClaim> allShiftClaims = shiftClaimRepository.findByShiftId(shift.getId());
                long confirmedCount = allShiftClaims.stream()
                        .filter(c -> "CONFIRMED".equalsIgnoreCase(c.getStatus()) || "CHECKED_IN".equalsIgnoreCase(c.getStatus())
                                || "TIMESHEET_SUBMITTED".equalsIgnoreCase(c.getStatus()) || "COMPLETED".equalsIgnoreCase(c.getStatus()))
                        .count();

                if (confirmedCount >= shift.getOpenings()) {
                    shift.setStatus("FILLED");
                    shift.setSlotsClaimed(shift.getOpenings());
                    locumShiftRepository.save(shift);

                    for (ShiftClaim other : allShiftClaims) {
                        if (!other.getId().equals(claim.getId()) && "CLAIMED".equalsIgnoreCase(other.getStatus())) {
                            other.setStatus("SLOT_FILLED");
                            other.setPayoutStatus("REFUNDED");
                            shiftClaimRepository.save(other);

                            CandidateProfile otherCand = candidateProfileRepository.findById(other.getCandidateId()).orElse(null);
                            if (otherCand != null) {
                                notificationRepository.save(Notification.builder()
                                        .userId(otherCand.getUserId())
                                        .title("Shift Openings Filled: " + shift.getTitle())
                                        .message("The requested " + shift.getOpenings() + " slot(s) for " + shift.getTitle() + " at " + facility.getHospitalName() + " have been filled. Your schedule has been unlocked for other clinical shifts.")
                                        .type("SHIFT")
                                        .actionUrl("/shifts")
                                        .read(false)
                                        .build());
                            }
                        }
                    }
                }
            }
            return mapShiftClaimToDto(claim, shift, facility.getId());
        }

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        JobPosting job = jobPostingRepository.findById(application.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        if (!job.getFacilityId().equals(facility.getId())) {
            throw new ForbiddenException("Unauthorized to manage applications for this facility");
        }

        String currentStatus = application.getStatus().toUpperCase();
        String targetStatus = request.getStatus().toUpperCase();
        if ("INTERVIEW_SCHEDULED".equals(targetStatus)) {
            targetStatus = "INTERVIEW";
        }
        if ("OFFER".equals(targetStatus)) {
            targetStatus = "OFFERED";
        }
        if ("CONFIRMED".equals(targetStatus) || "CONFIRM_SHIFT".equals(targetStatus)) {
            targetStatus = "HIRED";
        }

        // Strict Valid-Transition Checks:
        // SHORTLISTED -> INTERVIEW, HIRED, REJECTED
        // INTERVIEW -> OFFERED, HIRED, REJECTED
        // OFFERED -> HIRED, REJECTED
        boolean isValid = false;
        if ("SHORTLISTED".equals(currentStatus)) {
            isValid = "INTERVIEW".equals(targetStatus) || "REJECTED".equals(targetStatus) || "HIRED".equals(targetStatus);
        } else if ("INTERVIEW".equals(currentStatus)) {
            isValid = "OFFERED".equals(targetStatus) || "REJECTED".equals(targetStatus) || "HIRED".equals(targetStatus);
        } else if ("OFFERED".equals(currentStatus)) {
            isValid = "HIRED".equals(targetStatus) || "REJECTED".equals(targetStatus);
        }

        if (!isValid) {
            throw new BadRequestException("Invalid status transition from " + currentStatus + " to " + targetStatus + ". Allowed progression: SHORTLISTED -> INTERVIEW -> OFFERED -> HIRED (or REJECTED at any stage).");
        }

        application.setStatus(targetStatus);
        if (request.getInterviewScheduledAt() != null) {
            application.setInterviewScheduledAt(request.getInterviewScheduledAt());
        }
        if (request.getNotes() != null) {
            application.setInterviewLocationOrLink(request.getNotes());
        }
        if ("REJECTED".equalsIgnoreCase(targetStatus)) {
            application.setWithdrawalReason(request.getNotes() != null ? request.getNotes() : "Candidate not selected after interview evaluation");
        }

        JobApplication saved = jobApplicationRepository.save(application);

        // When a candidate is hired for this role, check if all openings are filled
        if ("HIRED".equalsIgnoreCase(targetStatus)) {
            List<JobApplication> allApps = jobApplicationRepository.findByJobId(job.getId());
            long hiredCount = allApps.stream().filter(a -> "HIRED".equalsIgnoreCase(a.getStatus())).count();
            int totalOpenings = job.getOpenings() > 0 ? job.getOpenings() : 1;

            if (hiredCount >= totalOpenings) {
                job.setStatus("CLOSED");
                jobPostingRepository.save(job);

                for (JobApplication other : allApps) {
                    if (!other.getId().equals(saved.getId()) && !"HIRED".equalsIgnoreCase(other.getStatus())) {
                        other.setStatus("POSITION_FILLED");
                        other.setWithdrawalReason("All positions filled");
                        jobApplicationRepository.save(other);

                        CandidateProfile otherCandidate = candidateProfileRepository.findById(other.getCandidateId()).orElse(null);
                        if (otherCandidate != null) {
                            Notification notif = Notification.builder()
                                    .userId(otherCandidate.getUserId())
                                    .title("Position Filled: " + job.getTitle())
                                    .message("All " + totalOpenings + " opening(s) for " + job.getTitle() + " at " + facility.getHospitalName() + " have now been filled. Your verified credentials remain active for other clinical openings.")
                                    .type("APPLICATION")
                                    .actionUrl("/applications")
                                    .read(false)
                                    .build();
                            notificationRepository.save(notif);
                        }
                    }
                }
            }
        }

        // Notify Candidate
        CandidateProfile candidate = candidateProfileRepository.findById(application.getCandidateId()).orElse(null);
        if (candidate != null) {
            String notifTitle = "REJECTED".equalsIgnoreCase(targetStatus)
                    ? "Application Update: " + job.getTitle()
                    : "Application Update: " + targetStatus;
            String notifMessage = "REJECTED".equalsIgnoreCase(targetStatus)
                    ? "Thank you for interviewing for " + job.getTitle() + " at " + facility.getHospitalName() + ". At this time, the hospital has decided to proceed with other candidates. Your verified credentials remain active for other hospital openings."
                    : "Your application for " + job.getTitle() + " at " + facility.getHospitalName() + " has advanced to " + targetStatus + ".";

            Notification notification = Notification.builder()
                    .userId(candidate.getUserId())
                    .title(notifTitle)
                    .message(notifMessage)
                    .type("APPLICATION")
                    .actionUrl("/applications")
                    .read(false)
                    .build();
            notificationRepository.save(notification);
        }

        auditLogService.logEvent(principal.getId(), "APPLICATION_STATUS_UPDATED", "JobApplication", saved.getId().toString(), null);

        return mapApplicationToDto(saved, job, facility.getId());
    }

    private JobPostingDto mapPostingToDto(JobPosting job, FacilityProfile facility) {
        return JobPostingDto.builder()
                .id(job.getId())
                .facilityId(job.getFacilityId())
                .facilityName(facility.getHospitalName())
                .facilityCity(facility.getCity())
                .city(job.getCity())
                .workLocation(job.getCity())
                .title(job.getTitle())
                .department(job.getDepartment())
                .employmentType(job.getEmploymentType())
                .internship(job.isInternship())
                .openings(job.getOpenings())
                .salaryMinPaise(job.getSalaryMin().multiply(BigDecimal.valueOf(100)).longValue())
                .salaryMaxPaise(job.getSalaryMax().multiply(BigDecimal.valueOf(100)).longValue())
                .experienceRequired(job.getExperienceRequired())
                .qualificationRequired(job.getQualificationRequired())
                .description(job.getDescription())
                .status(job.getStatus())
                .createdAt(job.getCreatedAt())
                .build();
    }

    private JobApplicationDto mapApplicationToDto(JobApplication app, JobPosting job, UUID facilityId) {
        CandidateProfile candidate = candidateProfileRepository.findById(app.getCandidateId()).orElse(null);
        boolean isUnlocked = facilityId != null && candidate != null &&
                (unlockedCandidateRepository.existsByFacilityIdAndCandidateId(facilityId, candidate.getId()) ||
                 "HIRED".equalsIgnoreCase(app.getStatus()));

        User user = (candidate != null && candidate.getUserId() != null) ? userRepository.findById(candidate.getUserId()).orElse(null) : null;

        String phone;
        String email;
        if (isUnlocked && user != null) {
            phone = user.getPhone();
            email = user.getEmail() != null ? user.getEmail() : "candidate@fale.care";
        } else {
            phone = maskPhone(user != null ? user.getPhone() : "+919876543210");
            email = maskEmail(user != null && user.getEmail() != null ? user.getEmail() : "candidate@fale.care");
        }

        List<CandidateDocument> docs = candidate != null ? candidateDocumentRepository.findByCandidateId(candidate.getId()) : List.of();
        CandidateDocument resumeDoc = docs.stream()
                .filter(d -> "RESUME".equalsIgnoreCase(d.getDocumentType()))
                .findFirst()
                .orElse(null);
        String resumeUrl = resumeDoc != null ? resumeDoc.getFileUrl() : null;
        String resumeFileName = resumeDoc != null ? resumeDoc.getOriginalFileName() : null;

        List<CandidateDocumentDto> docDtos = docs.stream().map(d -> CandidateDocumentDto.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .originalFileName(d.getOriginalFileName())
                .fileUrl(d.getFileUrl())
                .fileSizeBytes(d.getFileSizeBytes())
                .mimeType(d.getMimeType())
                .verified(d.isVerified())
                .createdAt(d.getCreatedAt())
                .build()).collect(Collectors.toList());

        boolean candIsStudent = user != null && "STUDENT".equalsIgnoreCase(user.getCandidateLevel());
        int canonicalScore = (candidate != null && job != null)
                ? ClinicalMatchingEngine.calculateJobMatch(candidate, candIsStudent, job)
                : app.getMatchScore();
        FacilityProfile facility = (job != null && job.getFacilityId() != null)
                ? facilityProfileRepository.findById(job.getFacilityId()).orElse(null)
                : null;

        return JobApplicationDto.builder()
                .id(app.getId())
                .jobId(app.getJobId())
                .jobTitle(job != null ? job.getTitle() : "Position")
                .facilityName(facility != null ? facility.getHospitalName() : null)
                .department(job != null ? job.getDepartment() : null)
                .city(job != null ? job.getCity() : null)
                .candidateId(app.getCandidateId())
                .candidateName(candidate != null ? candidate.getName() : "Candidate")
                .candidateRole(candidate != null ? (candidate.getProfession() != null ? candidate.getProfession() : candidate.getCourse()) : null)
                .candidateSpecialty(candidate != null ? candidate.getSpecialty() : null)
                .candidateCity(candidate != null ? candidate.getCity() : null)
                .candidateExperience(candidate != null ? candidate.getExperienceYears() : null)
                .councilVerified(candidate != null && "VERIFIED".equalsIgnoreCase(candidate.getKycStatus()))
                .regNumber(candidate != null ? candidate.getRegNumber() : null)
                .candidateQualification(candidate != null ? candidate.getQualification() : null)
                .candidateCouncil(candidate != null ? candidate.getCouncil() : null)
                .candidateBio(candidate != null ? candidate.getBio() : null)
                .candidateState(candidate != null ? candidate.getState() : null)
                .candidatePhone(phone)
                .candidateEmail(email)
                .resumeUrl(resumeUrl)
                .resumeFileName(resumeFileName)
                .candidateDocuments(docDtos)
                .status(app.getStatus())
                .matchScore(canonicalScore)
                .appliedAt(app.getAppliedAt())
                .interviewScheduledAt(app.getInterviewScheduledAt())
                .interviewLocationOrLink(app.getInterviewLocationOrLink())
                .withdrawalReason(app.getWithdrawalReason())
                .build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "+91 ***** *****";
        }
        return phone.substring(0, 5) + "*****" + phone.substring(phone.length() - 2);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "c*****@fale.care";
        }
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        String masked = name.length() > 2 ? name.charAt(0) + "*****" + name.charAt(name.length() - 1) : name.charAt(0) + "****";
        return masked + "@" + domain;
    }

    private JobApplicationDto mapShiftClaimToDto(ShiftClaim claim, LocumShift shift, UUID facilityId) {
        CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
        User user = (candidate != null && candidate.getUserId() != null) ? userRepository.findById(candidate.getUserId()).orElse(null) : null;

        String phone = user != null && user.getPhone() != null ? user.getPhone() : "+91 ***** *****";
        String email = user != null && user.getEmail() != null ? user.getEmail() : "candidate@fale.care";

        String shiftStatus = claim.getStatus() != null ? claim.getStatus().toUpperCase() : "CLAIMED";
        if (Set.of("REJECTED", "CONFIRMED", "COMPLETED", "CHECKED_IN", "TIMESHEET_SUBMITTED", "SLOT_FILLED", "DISPUTED", "NO_SHOW").contains(shiftStatus)) {
            // keep canonical status
        } else {
            shiftStatus = "CLAIMED";
        }

        List<CandidateDocument> docs = candidate != null ? candidateDocumentRepository.findByCandidateId(candidate.getId()) : List.of();
        CandidateDocument resumeDoc = docs.stream()
                .filter(d -> "RESUME".equalsIgnoreCase(d.getDocumentType()))
                .findFirst()
                .orElse(null);
        String resumeUrl = resumeDoc != null ? resumeDoc.getFileUrl() : null;
        String resumeFileName = resumeDoc != null ? resumeDoc.getOriginalFileName() : null;

        List<CandidateDocumentDto> docDtos = docs.stream().map(d -> CandidateDocumentDto.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .originalFileName(d.getOriginalFileName())
                .fileUrl(d.getFileUrl())
                .fileSizeBytes(d.getFileSizeBytes())
                .mimeType(d.getMimeType())
                .verified(d.isVerified())
                .createdAt(d.getCreatedAt())
                .build()).collect(Collectors.toList());

        FacilityProfile shiftFacility = facilityId != null ? facilityProfileRepository.findById(facilityId).orElse(null) : null;
        boolean candIsStudent = user != null && "STUDENT".equalsIgnoreCase(user.getCandidateLevel());
        int canonicalShiftScore = (candidate != null && shift != null)
                ? ClinicalMatchingEngine.calculateShiftMatch(candidate, candIsStudent, shift, shiftFacility)
                : 50;

        return JobApplicationDto.builder()
                .id(claim.getId())
                .jobId(shift != null ? shift.getId() : null)
                .jobTitle(shift != null ? shift.getTitle() + " (Locum Shift)" : "Locum Shift")
                .facilityName(shiftFacility != null ? shiftFacility.getHospitalName() : null)
                .department(shift != null ? shift.getDepartment() : null)
                .city(shiftFacility != null ? shiftFacility.getCity() : null)
                .candidateId(claim.getCandidateId())
                .candidateName(candidate != null ? candidate.getName() : "Candidate")
                .candidateRole(candidate != null ? (candidate.getProfession() != null ? candidate.getProfession() : "Clinician") : "Clinician")
                .candidateSpecialty(candidate != null ? candidate.getSpecialty() : null)
                .candidateCity(candidate != null ? candidate.getCity() : null)
                .candidateExperience(candidate != null ? candidate.getExperienceYears() : null)
                .councilVerified(candidate != null && "VERIFIED".equalsIgnoreCase(candidate.getKycStatus()))
                .regNumber(candidate != null ? candidate.getRegNumber() : null)
                .candidateQualification(candidate != null ? candidate.getQualification() : null)
                .candidateCouncil(candidate != null ? candidate.getCouncil() : null)
                .candidateBio(candidate != null ? candidate.getBio() : null)
                .candidateState(candidate != null ? candidate.getState() : null)
                .candidatePhone(phone)
                .candidateEmail(email)
                .resumeUrl(resumeUrl)
                .resumeFileName(resumeFileName)
                .candidateDocuments(docDtos)
                .status(shiftStatus)
                .actualHours(claim.getActualHours())
                .scheduledHours(shift != null ? shift.getHours() : 8)
                .hourlyRatePaise(shift != null ? shift.getRatePerHour().add(shift.getSurgeRatePerHour() != null ? shift.getSurgeRatePerHour() : BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).longValue() : 0L)
                .checkInPin(claim.getCheckInPin())
                .disputeReason(claim.getDisputeReason())
                .hospitalRating(claim.getHospitalRating())
                .hospitalReview(claim.getHospitalReview())
                .ratingTags(claim.getRatingTags())
                .ratedAt(claim.getRatedAt())
                .candidateRatingAvg(candidate != null && candidate.getShiftRatingAvg() != null ? candidate.getShiftRatingAvg().doubleValue() : 0.0)
                .candidateRatingCount(candidate != null && candidate.getShiftRatingCount() != null ? candidate.getShiftRatingCount() : 0)
                .completedShiftsCount(candidate != null && candidate.getCompletedShiftsCount() != null ? candidate.getCompletedShiftsCount() : 0)
                .matchScore(canonicalShiftScore)
                .appliedAt(claim.getClaimedAt())
                .build();
    }
}
