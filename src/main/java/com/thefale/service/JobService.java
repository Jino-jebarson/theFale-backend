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

        int matchScore = calculateMatchScore(candidate, job);

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

        return JobApplicationDto.builder()
                .id(app.getId())
                .jobId(app.getJobId())
                .jobTitle(jobTitle)
                .facilityName(facilityName)
                .department(dept)
                .city(city)
                .status(app.getStatus())
                .matchScore(app.getMatchScore())
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

    private static boolean isDoctorCadre(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("doctor") || t.contains("physician") || t.contains("surgeon") ||
               t.contains("cardiolog") || t.contains("pediatr") || t.contains("orthoped") ||
               t.contains("gynecolog") || t.contains("gynaecolog") || t.contains("anesthesiolog") ||
               t.contains("anaesthesiolog") || t.contains("intensivist") || t.contains("radiolog") ||
               t.contains("patholog") || t.contains("consultant") || t.contains("resident") ||
               t.contains("mbbs") || t.contains("md") || t.contains("ms") || t.contains("dnb") ||
               t.contains("medical officer");
    }

    private static boolean isNurseCadre(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("nurse") || t.contains("nursing") || t.contains("sister") ||
               t.contains("gnm") || t.contains("anm") || t.contains("matron") || t.contains("b.sc nursing");
    }

    private static boolean isPhysioCadre(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("physio") || t.contains("bpt") || t.contains("mpt") || t.contains("rehab");
    }

    private static boolean isPharmacistCadre(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("pharm") || t.contains("dispens");
    }

    private static boolean isTechnicianCadre(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("tech") || t.contains("dmlt") || t.contains("bmlt") || t.contains("x-ray") ||
               t.contains("mri") || t.contains("radiograph") || t.contains("dialysis") ||
               t.contains("ecg") || t.contains("cath");
    }

    public static int calculateMatchScore(CandidateProfile candidate, JobPosting job) {
        if (candidate == null || job == null) {
            return 70;
        }

        int score = 30; // Baseline floor for complete profile submission

        // 1. Role / Specialty / Department alignment (up to 25 pts)
        String candRole = candidate.getProfession() != null ? candidate.getProfession().toLowerCase() : "";
        String candSpec = candidate.getSpecialty() != null ? candidate.getSpecialty().toLowerCase() : "";
        String candCourse = candidate.getCourse() != null ? candidate.getCourse().toLowerCase() : "";
        String jobTitle = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        String jobDept = job.getDepartment() != null ? job.getDepartment().toLowerCase() : "";

        boolean sameCadre = (isDoctorCadre(candRole) && isDoctorCadre(jobTitle)) ||
                            (isNurseCadre(candRole) && isNurseCadre(jobTitle)) ||
                            (isPhysioCadre(candRole) && isPhysioCadre(jobTitle)) ||
                            (isPharmacistCadre(candRole) && isPharmacistCadre(jobTitle)) ||
                            (isTechnicianCadre(candRole) && isTechnicianCadre(jobTitle));

        boolean specialtyMatch = false;
        if (!candSpec.isBlank()) {
            String cleanSpec = candSpec.length() > 5 ? candSpec.substring(0, 5) : candSpec;
            if (jobTitle.contains(cleanSpec) || jobDept.contains(cleanSpec)) {
                specialtyMatch = true;
            }
        }
        if (!specialtyMatch && isDoctorCadre(candRole) && isDoctorCadre(jobTitle)) {
            specialtyMatch = true;
        }

        if (sameCadre && specialtyMatch) {
            score += 25;
        } else if (sameCadre) {
            score += 20;
        } else if ((!candSpec.isBlank() && (jobTitle.contains(candSpec) || jobDept.contains(candSpec))) ||
                   (!candRole.isBlank() && (jobTitle.contains(candRole) || jobDept.contains(candRole))) ||
                   (!candCourse.isBlank() && (jobTitle.contains(candCourse) || jobDept.contains(candCourse)))) {
            score += 25;
        } else {
            score += 10;
        }

        // 2. Qualification Match (up to 15 pts)
        String qualReq = job.getQualificationRequired() != null ? job.getQualificationRequired().toLowerCase() : "";
        String candQual = candidate.getQualification() != null ? candidate.getQualification().toLowerCase() : "";
        if (!qualReq.isBlank() && !candQual.isBlank() && (candQual.contains(qualReq) || qualReq.contains(candQual))) {
            score += 15;
        } else if (!candQual.isBlank() || !candCourse.isBlank()) {
            score += 10;
        }

        // 3. Council / Bona Fide KYC Verification (15 pts)
        if ("VERIFIED".equalsIgnoreCase(candidate.getKycStatus())) {
            score += 15;
        } else if (candidate.getRegNumber() != null && !candidate.getRegNumber().isBlank()) {
            score += 10;
        } else if (candidate.getUniversityRegNo() != null && !candidate.getUniversityRegNo().isBlank()) {
            score += 10;
        }

        // 4. City Location Match (10 pts)
        if (candidate.getCity() != null && job.getCity() != null &&
            candidate.getCity().trim().equalsIgnoreCase(job.getCity().trim())) {
            score += 10;
        } else {
            score += 5;
        }

        // 5. Experience / Academic Progression Match (5 pts)
        if (job.isInternship()) {
            score += 5;
        } else if (candidate.getExperienceYears() != null && !candidate.getExperienceYears().isBlank()) {
            score += 5;
        }

        return Math.min(100, Math.max(30, score));
    }

    private UUID getCandidateIdIfPresent(CustomUserPrincipal principal) {
        if (principal == null) return null;
        return candidateProfileRepository.findByUserId(principal.getId())
                .map(CandidateProfile::getId)
                .orElse(null);
    }
}
