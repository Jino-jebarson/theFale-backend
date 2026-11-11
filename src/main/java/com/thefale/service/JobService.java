package com.thefale.service;

import com.thefale.dto.JobApplicationDto;
import com.thefale.dto.JobPostingDto;
import com.thefale.entity.*;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ForbiddenException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final SavedJobRepository savedJobRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    private static final Locale INDIA_LOCALE = new Locale("en", "IN");

    @Transactional(readOnly = true)
    public List<JobPostingDto> getJobFeed(CustomUserPrincipal principal, String city) {
        boolean isStudent = principal != null && "STUDENT".equalsIgnoreCase(principal.getCandidateLevel());

        List<JobPosting> postings = (city != null && !city.isBlank())
                ? jobPostingRepository.findOpenPostingsByCity(isStudent, city)
                : jobPostingRepository.findOpenPostings(isStudent);

        UUID candidateId = getCandidateIdIfPresent(principal);

        Set<UUID> appliedJobIds = candidateId != null
                ? jobApplicationRepository.findByCandidateIdOrderByAppliedAtDesc(candidateId).stream()
                .map(JobApplication::getJobId)
                .collect(Collectors.toSet())
                : Collections.emptySet();

        Set<UUID> savedJobIds = candidateId != null
                ? savedJobRepository.findByCandidateId(candidateId).stream()
                .map(SavedJob::getJobId)
                .collect(Collectors.toSet())
                : Collections.emptySet();

        return postings.stream()
                .map(job -> mapToDto(job, appliedJobIds.contains(job.getId()), savedJobIds.contains(job.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobPostingDto getJobById(CustomUserPrincipal principal, UUID jobId) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        boolean isStudent = principal != null && "STUDENT".equalsIgnoreCase(principal.getCandidateLevel());
        if (isStudent && !job.isInternship()) {
            throw new ForbiddenException("Healthcare students are eligible to view and apply for internship roles only.");
        }

        UUID candidateId = getCandidateIdIfPresent(principal);
        boolean hasApplied = candidateId != null && jobApplicationRepository.existsByJobIdAndCandidateId(jobId, candidateId);
        boolean isSaved = candidateId != null && savedJobRepository.existsByCandidateIdAndJobId(candidateId, jobId);

        return mapToDto(job, hasApplied, isSaved);
    }

    @Transactional
    public JobApplicationDto applyForJob(CustomUserPrincipal principal, UUID jobId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Please complete onboarding."));

        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        if (!"OPEN".equalsIgnoreCase(job.getStatus())) {
            throw new ForbiddenException("This job posting is no longer open for applications.");
        }

        // Student Guard: Students can ONLY apply to internships
        boolean isStudent = "STUDENT".equalsIgnoreCase(principal.getCandidateLevel());
        if (isStudent && !job.isInternship()) {
            throw new ForbiddenException("Healthcare students cannot apply for permanent clinical roles. Please select internship postings.");
        }

        if (jobApplicationRepository.existsByJobIdAndCandidateId(jobId, candidate.getId())) {
            throw new ConflictException("You have already submitted an application for this position.");
        }

        int matchScore = ClinicalMatchingEngine.calculateJobMatch(candidate, isStudent, job);

        JobApplication application = JobApplication.builder()
                .jobId(jobId)
                .candidateId(candidate.getId())
                .status("UNDER_ADMIN_SCREENING")
                .matchScore(matchScore)
                .build();

        JobApplication saved = jobApplicationRepository.save(application);

        // Audit log
        auditLogService.logEvent(principal.getId(), "JOB_APPLICATION_SUBMITTED", "JobApplication", saved.getId().toString(), null);

        // Notification for candidate
        Notification notification = Notification.builder()
                .userId(principal.getId())
                .title("Application Submitted — Screening in Progress")
                .message("Your application for " + job.getTitle() + " has arrived at FALE Admin for compliance screening. (Calculated Match: " + matchScore + "%)")
                .type("APPLICATION")
                .actionUrl("/applications")
                .read(false)
                .build();
        notificationRepository.save(notification);

        return mapApplicationToDto(saved, job);
    }

    @Transactional
    public void saveJob(CustomUserPrincipal principal, UUID jobId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        if (!savedJobRepository.existsByCandidateIdAndJobId(candidate.getId(), jobId)) {
            SavedJob savedJob = SavedJob.builder()
                    .candidateId(candidate.getId())
                    .jobId(jobId)
                    .build();
            savedJobRepository.save(savedJob);
        }
    }

    @Transactional
    public void unsaveJob(CustomUserPrincipal principal, UUID jobId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        savedJobRepository.deleteByCandidateIdAndJobId(candidate.getId(), jobId);
    }

    @Transactional(readOnly = true)
    public List<JobApplicationDto> getMyApplications(CustomUserPrincipal principal) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        return jobApplicationRepository.findByCandidateIdOrderByAppliedAtDesc(candidate.getId()).stream()
                .map(app -> {
                    JobPosting job = jobPostingRepository.findById(app.getJobId()).orElse(null);
                    return mapApplicationToDto(app, job);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void withdrawApplication(CustomUserPrincipal principal, UUID applicationId, String reason) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        if (!application.getCandidateId().equals(candidate.getId())) {
            throw new ForbiddenException("You cannot withdraw an application that does not belong to you.");
        }

        application.setStatus("WITHDRAWN");
        application.setWithdrawalReason(reason != null ? reason : "Candidate withdrew application");
        jobApplicationRepository.save(application);

        auditLogService.logEvent(principal.getId(), "JOB_APPLICATION_WITHDRAWN", "JobApplication", applicationId.toString(), null);
    }

    private JobPostingDto mapToDto(JobPosting job, boolean hasApplied, boolean isSaved) {
        FacilityProfile facility = facilityProfileRepository.findById(job.getFacilityId()).orElse(null);
        String facilityName = facility != null ? facility.getHospitalName() : "Healthcare Facility";
        String facilityCity = facility != null ? facility.getCity() : job.getCity();
        String accreditation = facility != null ? facility.getAccreditationLevel() : "NABH Accredited";

        long minPaise = job.getSalaryMin().multiply(BigDecimal.valueOf(100)).longValue();
        long maxPaise = job.getSalaryMax().multiply(BigDecimal.valueOf(100)).longValue();

        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA_LOCALE);
        String display = format.format(job.getSalaryMin()) + " – " + format.format(job.getSalaryMax()) + " / mo";

        return JobPostingDto.builder()
                .id(job.getId())
                .facilityId(job.getFacilityId())
                .facilityName(facilityName)
                .facilityCity(facilityCity)
                .facilityAccreditation(accreditation)
                .title(job.getTitle())
                .department(job.getDepartment())
                .employmentType(job.getEmploymentType())
                .internship(job.isInternship())
                .city(job.getCity())
                .workLocation(job.getCity())
                .openings(job.getOpenings())
                .salaryMinPaise(minPaise)
                .salaryMaxPaise(maxPaise)
                .salaryDisplay(display)
                .experienceRequired(job.getExperienceRequired())
                .qualificationRequired(job.getQualificationRequired())
                .description(job.getDescription())
                .status(job.getStatus())
                .postedAt(job.getCreatedAt())
                .hasApplied(hasApplied)
                .isSaved(isSaved)
                .build();
    }

    private JobApplicationDto mapApplicationToDto(JobApplication app, JobPosting job) {
        String jobTitle = job != null ? job.getTitle() : "Healthcare Role";
        String dept = job != null ? job.getDepartment() : "General";
        String city = job != null ? job.getCity() : "India";
        String facilityName = "Healthcare Facility";

        if (job != null) {
            facilityName = facilityProfileRepository.findById(job.getFacilityId())
                    .map(FacilityProfile::getHospitalName)
                    .orElse("Healthcare Facility");
        }

        CandidateProfile candidate = candidateProfileRepository.findById(app.getCandidateId()).orElse(null);
        int canonicalScore = (candidate != null && job != null)
                ? ClinicalMatchingEngine.calculateJobMatch(
                        candidate,
                        candidate.getRegNumber() == null && candidate.getCourse() != null && !candidate.getCourse().isBlank(),
                        job
                )
                : app.getMatchScore();

        return JobApplicationDto.builder()
                .id(app.getId())
                .jobId(app.getJobId())
                .jobTitle(jobTitle)
                .facilityName(facilityName)
                .department(dept)
                .city(city)
                .status(app.getStatus())
                .matchScore(canonicalScore)
                .appliedAt(app.getAppliedAt())
                .interviewScheduledAt(app.getInterviewScheduledAt())
                .interviewLocationOrLink(app.getInterviewLocationOrLink())
                .withdrawalReason(app.getWithdrawalReason())
                .candidateId(app.getCandidateId())
                .candidateName(candidate != null ? candidate.getName() : "Candidate")
                .candidateRole(candidate != null ? (candidate.getProfession() != null ? candidate.getProfession() : candidate.getCourse()) : null)
                .candidateSpecialty(candidate != null ? candidate.getSpecialty() : null)
                .candidateCity(candidate != null ? candidate.getCity() : null)
                .candidateExperience(candidate != null ? candidate.getExperienceYears() : null)
                .councilVerified(candidate != null && "VERIFIED".equalsIgnoreCase(candidate.getKycStatus()))
                .regNumber(candidate != null ? candidate.getRegNumber() : null)
                .build();
    }

    public static int calculateMatchScore(CandidateProfile candidate, JobPosting job) {
        boolean inferredStudent = candidate != null
                && (candidate.getRegNumber() == null || candidate.getRegNumber().isBlank())
                && candidate.getCourse() != null && !candidate.getCourse().isBlank()
                && (candidate.getProfession() == null || candidate.getProfession().isBlank());
        return ClinicalMatchingEngine.calculateJobMatch(candidate, inferredStudent, job);
    }

    private UUID getCandidateIdIfPresent(CustomUserPrincipal principal) {
        if (principal == null) return null;
        return candidateProfileRepository.findByUserId(principal.getId())
                .map(CandidateProfile::getId)
                .orElse(null);
    }
}
