package com.thefale.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.*;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository profileRepository;
    private final CandidateDocumentRepository documentRepository;
    private final CandidatePayoutMethodRepository payoutMethodRepository;
    private final StudentUpgradeRequestRepository upgradeRequestRepository;
    private final CandidateProfileViewRepository profileViewRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ReferralService referralService;

    @Transactional
    public CandidateProfileDto onboardCandidate(CustomUserPrincipal principal, CandidateOnboardingRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!"ROLE_CANDIDATE".equals(user.getRole())) {
            throw new ForbiddenException("Only candidates can complete candidate onboarding");
        }

        boolean isStudent = "STUDENT".equalsIgnoreCase(request.getCandidateLevel()) ||
                ((request.getCandidateLevel() == null || request.getCandidateLevel().isBlank()) &&
                 request.getCourse() != null && !request.getCourse().isBlank() && request.getCouncilRegistrationNumber() == null);

        String candidateLevel = isStudent ? "STUDENT" :
                ("FRESHER".equalsIgnoreCase(request.getCandidateLevel()) ? "FRESHER" : "PROFESSIONAL");

        // Validate Student constraints vs Graduate constraints
        if (isStudent && (request.getCourse() == null || request.getCourse().isBlank())) {
            throw new BadRequestException("Course is required for students.");
        }
        if (isStudent && request.getUniversityRegNo() != null && !request.getUniversityRegNo().isBlank()
                && request.getUniversityRegNo().trim().length() < 5) {
            throw new BadRequestException("University registration number must be at least 5 characters.");
        }
        if (!isStudent) {
            if (request.getProfession() == null || request.getProfession().isBlank()) {
                throw new BadRequestException("Profession is required for graduates.");
            }
        }

        user.setCandidateLevel(candidateLevel);
        user.setOnboarded(true);
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().toLowerCase().trim();
            if (!email.equals(user.getEmail())) {
                userRepository.findByEmail(email).ifPresent(existing -> {
                    if (!existing.getId().equals(user.getId())) {
                        throw new BadRequestException("Email " + email + " is already in use by another account.");
                    }
                });
                user.setEmail(email);
            }
        }
        userRepository.save(user);

        // Create or update candidate profile
        CandidateProfile profile = profileRepository.findByUserId(user.getId())
                .orElse(CandidateProfile.builder().userId(user.getId()).build());

        profile.setName(request.getFullName().trim());
        profile.setGender(request.getGender());
        if (request.getDob() != null && !request.getDob().isBlank()) {
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(request.getDob().trim());
                if (date.isBefore(java.time.LocalDate.of(1945, 1, 1)) || date.isAfter(java.time.LocalDate.now().minusYears(18))) {
                    throw new BadRequestException("Check your date of birth. You must be 18 or older.");
                }
                profile.setDob(date);
            } catch (java.time.format.DateTimeParseException e) {
                throw new BadRequestException("Enter a valid date of birth.");
            }
        }
        profile.setCity(request.getCity().trim());
        if (request.getLocality() != null) {
            profile.setLocality(request.getLocality().trim());
        }
        profile.setState(request.getState());
        profile.setPincode(request.getPincode());
        profile.setAddressLine(request.getAddressLine());
        profile.setBio(request.getBio());

        if (isStudent) {
            profile.setCourse(request.getCourse());
            profile.setCollege(request.getCollegeName());
            profile.setStudyYear(request.getStudyYear());
            profile.setUniversityRegNo(request.getUniversityRegNo());
            Integer graduationYear = request.getExpectedGraduationYear();
            if (graduationYear == null && request.getExpectedGraduationMonth() != null) {
                graduationYear = Integer.parseInt(request.getExpectedGraduationMonth().substring(0, 4));
            }
            profile.setExpectedGraduationYear(graduationYear);
            profile.setExpectedGraduationMonth(request.getExpectedGraduationMonth());
            profile.setProfession(request.getProfession() != null && !request.getProfession().isBlank()
                    ? request.getProfession() : request.getCourse());
            profile.setQualification(request.getCourse());
        } else {
            profile.setProfession(request.getProfession());
            profile.setSpecialty(request.getPrimarySpecialty());
            profile.setSubSpecialties(listToJson(request.getSubSpecialties()));
            profile.setQualification(request.getQualification());
            profile.setCouncil(request.getCouncil());
            profile.setCouncilState(request.getCouncilState());
            profile.setRegNumber(request.getCouncilRegistrationNumber());
            profile.setRegYear(request.getRegYear());
            profile.setExperienceYears(request.getExperienceYears());

            // Dynamically persist college, course, and graduation year for graduates
            if (request.getCollegeName() != null && !request.getCollegeName().isBlank()) {
                profile.setCollege(request.getCollegeName().trim());
            }
            if (request.getCourse() != null && !request.getCourse().isBlank()) {
                profile.setCourse(request.getCourse().trim());
            } else if (request.getQualification() != null && !request.getQualification().isBlank()) {
                profile.setCourse(request.getQualification().trim());
            }
            if (request.getExpectedGraduationYear() != null) {
                profile.setExpectedGraduationYear(request.getExpectedGraduationYear());
            } else if (request.getRegYear() != null && !request.getRegYear().isBlank()) {
                try {
                    profile.setExpectedGraduationYear(Integer.parseInt(request.getRegYear().trim()));
                } catch (NumberFormatException ignored) {}
            }
            if (request.getUniversityRegNo() != null && !request.getUniversityRegNo().isBlank()) {
                profile.setUniversityRegNo(request.getUniversityRegNo().trim());
            } else {
                profile.setUniversityRegNo(null);
            }
            profile.setStudyYear(null);
        }

        profile.setEmploymentType(request.getEmploymentType());
        profile.setAvailability(request.getAvailability());
        profile.setCurrentSalary(request.getCurrentSalary());
        profile.setSkills(listToJson(request.getSkills()));
        profile.setLanguages(listToJson(request.getLanguages()));
        profile.setPreferredCities(listToJson(request.getPreferredCities()));
        if (request.getOpenToWork() != null) {
            profile.setOpenToWork(request.getOpenToWork());
        }
        if (request.getRelocate() != null) {
            profile.setRelocate(request.getRelocate());
        }

        profile.setExpectedSalaryMinPaise(request.getExpectedSalaryMinPaise());
        profile.setExpectedSalaryMaxPaise(request.getExpectedSalaryMaxPaise());
        profile.setNoticePeriod(request.getNoticePeriod());

        CandidateProfile savedProfile = profileRepository.save(profile);
        savedProfile.setKycStatus("DOCUMENTS_REQUIRED");

        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
            referralService.applyCode(user.getId(), savedProfile.getId(), request.getReferralCode());
        }

        // Record initial documents dynamically if URLs provided
        if (request.getResumeUrl() != null && !request.getResumeUrl().isBlank()) {
            String fileName = (request.getResumeFileName() != null && !request.getResumeFileName().isBlank())
                    ? request.getResumeFileName() : "Resume.pdf";
            recordDocumentIfPresent(savedProfile.getId(), "RESUME", request.getResumeUrl(), fileName);
        }
        if (request.getRegistrationCertificateUrl() != null && !request.getRegistrationCertificateUrl().isBlank()) {
            String fileName = (request.getRegistrationCertificateFileName() != null && !request.getRegistrationCertificateFileName().isBlank())
                    ? request.getRegistrationCertificateFileName() : "Council_Registration_Certificate.pdf";
            recordDocumentIfPresent(savedProfile.getId(), "REGISTRATION_CERTIFICATE", request.getRegistrationCertificateUrl(), fileName);
        }
        if (request.getQualificationCertificateUrl() != null && !request.getQualificationCertificateUrl().isBlank()) {
            String fileName = (request.getQualificationCertificateFileName() != null && !request.getQualificationCertificateFileName().isBlank())
                    ? request.getQualificationCertificateFileName() : "Degree_Certificate.pdf";
            recordDocumentIfPresent(savedProfile.getId(), "DEGREE_CERTIFICATE", request.getQualificationCertificateUrl(), fileName);
        }
        if (request.getIdentityProofUrl() != null && !request.getIdentityProofUrl().isBlank()) {
            String fileName = (request.getIdentityProofFileName() != null && !request.getIdentityProofFileName().isBlank())
                    ? request.getIdentityProofFileName() : "Identity_Proof.pdf";
            recordDocumentIfPresent(savedProfile.getId(), "ID_PROOF", request.getIdentityProofUrl(), fileName);
        }
        if (request.getBonafideCertificateUrl() != null && !request.getBonafideCertificateUrl().isBlank()) {
            String fileName = (request.getBonafideCertificateFileName() != null && !request.getBonafideCertificateFileName().isBlank())
                    ? request.getBonafideCertificateFileName() : "Bonafide_Certificate.pdf";
            recordDocumentIfPresent(savedProfile.getId(), "BONAFIDE", request.getBonafideCertificateUrl(), fileName);
        }
        if (request.getStudentIdCardUrl() != null && !request.getStudentIdCardUrl().isBlank()) {
            String fileName = (request.getStudentIdCardFileName() != null && !request.getStudentIdCardFileName().isBlank())
                    ? request.getStudentIdCardFileName() : "Student_ID_Card.pdf";
            recordDocumentIfPresent(savedProfile.getId(), "STUDENT_ID", request.getStudentIdCardUrl(), fileName);
        }
        if (request.getProfilePhotoUrl() != null && !request.getProfilePhotoUrl().isBlank()) {
            String fileName = (request.getProfilePhotoFileName() != null && !request.getProfilePhotoFileName().isBlank())
                    ? request.getProfilePhotoFileName() : "ProfilePhoto.jpg";
            recordDocumentIfPresent(savedProfile.getId(), "PHOTO", request.getProfilePhotoUrl(), fileName);
        }

        updateKycDocumentReadiness(savedProfile);

        auditLogService.logEvent(user.getId(), "CANDIDATE_ONBOARDED", "CandidateProfile", savedProfile.getId().toString(), null);

        return getCandidateProfile(principal);
    }

    @Transactional(readOnly = true)
    public CandidateProfile getCandidateProfileEntity(CustomUserPrincipal principal) {
        return profileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Complete onboarding first."));
    }

    @Transactional
    public CandidateProfileDto updateCandidateProfile(CustomUserPrincipal principal, CandidateProfileUpdateRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        CandidateProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Complete onboarding first."));

        if (request.getFullName() != null && !request.getFullName().isBlank()) profile.setName(request.getFullName().trim());
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmail(email).ifPresent(existing -> {
                    if (!existing.getId().equals(user.getId())) throw new BadRequestException("That email is already in use by another account.");
                });
                user.setEmail(email);
                userRepository.save(user);
            }
        }
        if (request.getGender() != null) profile.setGender(blankToNull(request.getGender()));
        if (request.getDob() != null) {
            if (request.getDob().isBlank()) {
                profile.setDob(null);
            } else {
                try {
                    java.time.LocalDate dob = java.time.LocalDate.parse(request.getDob().trim());
                    if (dob.isBefore(java.time.LocalDate.of(1945, 1, 1)) || dob.isAfter(java.time.LocalDate.now().minusYears(18))) {
                        throw new BadRequestException("Check your date of birth. You must be 18 or older.");
                    }
                    profile.setDob(dob);
                } catch (java.time.format.DateTimeParseException e) {
                    throw new BadRequestException("Enter a valid date of birth.");
                }
            }
        }
        if (request.getCity() != null && !request.getCity().isBlank()) profile.setCity(request.getCity().trim());
        if (request.getLocality() != null) profile.setLocality(blankToNull(request.getLocality()));
        if (request.getState() != null) profile.setState(blankToNull(request.getState()));
        if (request.getPincode() != null) profile.setPincode(blankToNull(request.getPincode()));
        if (request.getAddressLine() != null) profile.setAddressLine(blankToNull(request.getAddressLine()));

        boolean student = "STUDENT".equalsIgnoreCase(user.getCandidateLevel());
        if (student) {
            if (request.getCourse() != null) {
                String course = blankToNull(request.getCourse());
                profile.setCourse(course);
                profile.setQualification(course);
                if (course != null) profile.setProfession(course);
            }
            if (request.getStudyYear() != null) profile.setStudyYear(blankToNull(request.getStudyYear()));
            if (request.getUniversityRegNo() != null) profile.setUniversityRegNo(blankToNull(request.getUniversityRegNo()));
            if (request.getExpectedGraduationYear() != null) profile.setExpectedGraduationYear(request.getExpectedGraduationYear());
            if (request.getExpectedGraduationMonth() != null) profile.setExpectedGraduationMonth(blankToNull(request.getExpectedGraduationMonth()));
        } else {
            if (request.getProfession() != null) profile.setProfession(blankToNull(request.getProfession()));
            if (request.getPrimarySpecialty() != null) profile.setSpecialty(blankToNull(request.getPrimarySpecialty()));
            if (request.getSubSpecialties() != null) profile.setSubSpecialties(listToJson(request.getSubSpecialties()));
            if (request.getQualification() != null) profile.setQualification(blankToNull(request.getQualification()));
            if (request.getCouncil() != null) profile.setCouncil(blankToNull(request.getCouncil()));
            if (request.getCouncilRegistrationNumber() != null) profile.setRegNumber(blankToNull(request.getCouncilRegistrationNumber()));
            if (request.getCouncilState() != null) profile.setCouncilState(blankToNull(request.getCouncilState()));
            if (request.getRegYear() != null) profile.setRegYear(blankToNull(request.getRegYear()));
            if (request.getExperienceYears() != null) profile.setExperienceYears(blankToNull(request.getExperienceYears()));
        }
        if (request.getCollegeName() != null) profile.setCollege(blankToNull(request.getCollegeName()));
        if (request.getBio() != null) profile.setBio(blankToNull(request.getBio()));
        if (request.getCurrentSalary() != null) profile.setCurrentSalary(blankToNull(request.getCurrentSalary()));
        if (request.getNoticePeriod() != null) profile.setNoticePeriod(blankToNull(request.getNoticePeriod()));
        if (request.getEmploymentType() != null) profile.setEmploymentType(blankToNull(request.getEmploymentType()));
        if (request.getAvailability() != null) profile.setAvailability(blankToNull(request.getAvailability()));
        if (request.getOpenToWork() != null) profile.setOpenToWork(request.getOpenToWork());
        if (request.getRelocate() != null) profile.setRelocate(request.getRelocate());
        if (request.getPreferredCities() != null) profile.setPreferredCities(listToJson(request.getPreferredCities()));
        if (request.getSkills() != null) profile.setSkills(listToJson(request.getSkills()));
        if (request.getLanguages() != null) profile.setLanguages(listToJson(request.getLanguages()));
        if (request.getExperienceHistory() != null) {
            for (CandidateExperienceDto experience : request.getExperienceHistory()) {
                if (experience.getStartDate() == null || (!experience.isCurrent() && experience.getEndDate() == null)) {
                    throw new BadRequestException("Enter a valid start and end month for each experience entry.");
                }
                try {
                    java.time.YearMonth start = java.time.YearMonth.parse(experience.getStartDate());
                    if (!experience.isCurrent()) {
                        java.time.YearMonth end = java.time.YearMonth.parse(experience.getEndDate());
                        if (end.isBefore(start)) throw new BadRequestException("An experience end date cannot be before its start date.");
                    }
                } catch (java.time.format.DateTimeParseException e) {
                    throw new BadRequestException("Enter a valid start and end month for each experience entry.");
                }
            }
            profile.setExperienceHistory(toJson(request.getExperienceHistory()));
        }
        if (request.getCertifications() != null) {
            for (CandidateCertificationDto certification : request.getCertifications()) {
                UUID documentId;
                try {
                    documentId = UUID.fromString(certification.getDocumentId());
                } catch (Exception e) {
                    throw new BadRequestException("Upload a certificate file for each certification.");
                }
                CandidateDocument document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new BadRequestException("The uploaded certificate could not be found. Please upload it again."));
                if (!document.getCandidateId().equals(profile.getId()) || !"CERTIFICATION".equalsIgnoreCase(document.getDocumentType())) {
                    throw new BadRequestException("Each certification must be linked to a certificate uploaded to your profile.");
                }
                try {
                    java.time.LocalDate issue = certification.getIssueDate() == null ? null : java.time.LocalDate.parse(certification.getIssueDate());
                    java.time.LocalDate expiry = certification.getExpiryDate() == null ? null : java.time.LocalDate.parse(certification.getExpiryDate());
                    if (issue != null && expiry != null && expiry.isBefore(issue)) {
                        throw new BadRequestException("A certificate expiry date cannot be before its issue date.");
                    }
                } catch (java.time.format.DateTimeParseException e) {
                    throw new BadRequestException("Enter a valid certificate issue or expiry date.");
                }
            }
            profile.setCertifications(toJson(request.getCertifications()));
        }
        if (Boolean.TRUE.equals(request.getUpdateExpectedSalary())) {
            Long minPaise = request.getExpectedSalaryMinPaise() != null
                    ? request.getExpectedSalaryMinPaise()
                    : request.getExpectedSalaryMinRupees() == null ? null : request.getExpectedSalaryMinRupees() * 100L;
            Long maxPaise = request.getExpectedSalaryMaxPaise() != null
                    ? request.getExpectedSalaryMaxPaise()
                    : request.getExpectedSalaryMaxRupees() == null ? null : request.getExpectedSalaryMaxRupees() * 100L;
            profile.setExpectedSalaryMinPaise(minPaise);
            profile.setExpectedSalaryMaxPaise(maxPaise);
        }

        CandidateProfile saved = profileRepository.save(profile);
        auditLogService.logEvent(user.getId(), "CANDIDATE_PROFILE_UPDATED", "CandidateProfile", saved.getId().toString(), null);
        return getCandidateProfile(principal);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public CandidateProfileDto updateEducation(CustomUserPrincipal principal, CandidateEducationUpdateRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if ("STUDENT".equalsIgnoreCase(user.getCandidateLevel())) {
            throw new BadRequestException("Student profiles use their course as the qualification.");
        }

        CandidateProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Complete onboarding first."));
        profile.setQualification(request.getQualification().trim());
        if (request.getCollegeName() != null) {
            profile.setCollege(request.getCollegeName().trim());
        }
        profileRepository.save(profile);
        auditLogService.logEvent(user.getId(), "CANDIDATE_EDUCATION_UPDATED", "CandidateProfile", profile.getId().toString(), null);
        return getCandidateProfile(principal);
    }

    @Transactional
    public CandidateProfileDto updateOpenToWork(CustomUserPrincipal principal, boolean openToWork) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CandidateProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Please complete onboarding."));

        profile.setOpenToWork(openToWork);
        CandidateProfile saved = profileRepository.save(profile);

        try {
            auditLogService.logEvent(user.getId(), "CANDIDATE_OPEN_TO_WORK_UPDATED", "CandidateProfile", saved.getId().toString(), java.util.Map.of("openToWork", openToWork));
        } catch (Exception e) {
            log.warn("Failed to audit log openToWork update: {}", e.getMessage());
        }

        return getCandidateProfile(principal);
    }

    @Transactional(readOnly = true)
    public CandidateProfileDto getCandidateProfile(CustomUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        CandidateProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found. Please complete onboarding."));

        List<CandidateDocumentDto> documents = documentRepository.findByCandidateId(profile.getId()).stream()
                .map(doc -> CandidateDocumentDto.builder()
                        .id(doc.getId())
                        .documentType(doc.getDocumentType())
                        .originalFileName(doc.getOriginalFileName())
                        .fileUrl(doc.getFileUrl())
                        .fileSizeBytes(doc.getFileSizeBytes())
                        .mimeType(doc.getMimeType())
                        .verified(doc.isVerified())
                        .reviewStatus(doc.getReviewStatus())
                        .reviewNotes(doc.getReviewNotes())
                        .reviewedAt(doc.getReviewedAt())
                        .createdAt(doc.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        CandidatePayoutMethodDto payoutDto = payoutMethodRepository.findByCandidateId(profile.getId())
                .map(this::mapPayoutMethodToDto)
                .orElse(null);

        StudentUpgradeRequestDto upgradeRequestDto = upgradeRequestRepository
                .findTopByCandidateIdOrderByCreatedAtDesc(profile.getId())
                .map(req -> StudentUpgradeRequestDto.builder()
                        .id(req.getId())
                        .qualification(req.getQualification())
                        .council(req.getCouncil())
                        .regNumber(req.getRegNumber())
                        .status(req.getStatus())
                        .adminNotes(req.getAdminNotes())
                        .createdAt(req.getCreatedAt())
                        .reviewedAt(req.getReviewedAt())
                        .build())
                .orElse(null);

        boolean isStudent = "STUDENT".equalsIgnoreCase(user.getCandidateLevel());

        return CandidateProfileDto.builder()
                .id(profile.getId())
                .userId(user.getId())
                .name(profile.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .gender(profile.getGender())
                .dob(profile.getDob())
                .city(profile.getCity())
                .locality(profile.getLocality())
                .state(profile.getState())
                .pincode(profile.getPincode())
                .addressLine(profile.getAddressLine())
                .candidateLevel(user.getCandidateLevel())
                .isStudent(isStudent)
                .course(profile.getCourse())
                .college(profile.getCollege())
                .studyYear(profile.getStudyYear())
                .universityRegNo(profile.getUniversityRegNo())
                .expectedGraduationYear(profile.getExpectedGraduationYear())
                .expectedGraduationMonth(profile.getExpectedGraduationMonth())
                .profession(profile.getProfession())
                .specialty(profile.getSpecialty())
                .subSpecialties(jsonToList(profile.getSubSpecialties()))
                .qualification(profile.getQualification())
                .council(profile.getCouncil())
                .councilState(profile.getCouncilState())
                .regNumber(profile.getRegNumber())
                .regYear(profile.getRegYear())
                .experienceYears(profile.getExperienceYears())
                .experienceHistory(jsonToTypedList(profile.getExperienceHistory(), new TypeReference<List<CandidateExperienceDto>>() {}))
                .certifications(jsonToTypedList(profile.getCertifications(), new TypeReference<List<CandidateCertificationDto>>() {}))
                .bio(profile.getBio())
                .expectedSalaryMinPaise(profile.getExpectedSalaryMinPaise())
                .expectedSalaryMaxPaise(profile.getExpectedSalaryMaxPaise())
                .currentSalary(profile.getCurrentSalary())
                .noticePeriod(profile.getNoticePeriod())
                .employmentType(profile.getEmploymentType())
                .availability(profile.getAvailability())
                .skills(jsonToList(profile.getSkills()))
                .languages(jsonToList(profile.getLanguages()))
                .preferredCities(jsonToList(profile.getPreferredCities()))
                .openToWork(profile.getOpenToWork())
                .relocate(profile.getRelocate())
                .kycStatus(effectiveKycStatus(profile, user))
                .kycNotes(profile.getKycNotes())
                .isMentor(Boolean.TRUE.equals(profile.getIsMentor()))
                .mentorStatus(profile.getMentorStatus() != null ? profile.getMentorStatus() : "NONE")
                .verifiedAt(profile.getVerifiedAt())
                .shiftRatingAvg(profile.getShiftRatingAvg() != null ? profile.getShiftRatingAvg().doubleValue() : 0.0)
                .shiftRatingCount(profile.getShiftRatingCount() != null ? profile.getShiftRatingCount() : 0)
                .completedShiftsCount(profile.getCompletedShiftsCount() != null ? profile.getCompletedShiftsCount() : 0)
                .topRated(profile.getShiftRatingCount() != null && profile.getShiftRatingCount() >= 3
                        && profile.getShiftRatingAvg() != null && profile.getShiftRatingAvg().compareTo(new java.math.BigDecimal("4.50")) >= 0)
                .documents(documents)
                .payoutMethod(payoutDto)
                .activeUpgradeRequest(upgradeRequestDto)
                .build();
    }

    @Transactional(readOnly = true)
    public CandidatePayoutMethodDto getPayoutMethod(CustomUserPrincipal principal) {
        CandidateProfile profile = profileRepository.findByUserId(principal.getId())
                .orElse(null);
        if (profile == null) {
            return null;
        }
        return payoutMethodRepository.findByCandidateId(profile.getId())
                .map(this::mapPayoutMethodToDto)
                .orElse(null);
    }

    @Transactional
    public CandidatePayoutMethodDto savePayoutMethod(CustomUserPrincipal principal, CandidatePayoutMethodDto request) {
        CandidateProfile profile = profileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        if (!"upi".equalsIgnoreCase(request.getMethodType()) && !"bank".equalsIgnoreCase(request.getMethodType())) {
            throw new BadRequestException("Method type must be 'upi' or 'bank'");
        }

        CandidatePayoutMethod method = payoutMethodRepository.findByCandidateIdWithLock(profile.getId())
                .orElse(CandidatePayoutMethod.builder().candidateId(profile.getId()).build());

        method.setMethodType(request.getMethodType().toLowerCase());
        method.setAccountHolderName(request.getAccountHolderName());

        if ("upi".equalsIgnoreCase(request.getMethodType())) {
            if (request.getUpiId() == null || !request.getUpiId().contains("@")) {
                throw new BadRequestException("Enter a valid UPI ID (e.g. name@okhdfcbank)");
            }
            method.setUpiId(request.getUpiId().trim());
        } else {
            if (request.getAccountNumber() == null || request.getAccountNumber().length() < 9) {
                throw new BadRequestException("Enter a valid bank account number");
            }
            if (request.getIfsc() == null || !request.getIfsc().matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
                throw new BadRequestException("Enter a valid 11-character Indian bank IFSC code (e.g. HDFC0001234)");
            }
            method.setBankName(request.getBankName());
            method.setIfsc(request.getIfsc().toUpperCase().trim());
            method.setAccountNumberEncrypted(request.getAccountNumber());
            method.setAccountNumberLast4(request.getAccountNumber().substring(request.getAccountNumber().length() - 4));
        }

        // Saving details is not proof of bank/UPI ownership.
        method.setStatus("Pending");
        method.setVerifiedAt(null);

        CandidatePayoutMethod saved = payoutMethodRepository.save(method);
        return mapPayoutMethodToDto(saved);
    }

    @Transactional
    public StudentUpgradeRequestDto requestStudentUpgrade(CustomUserPrincipal principal, StudentUpgradeRequestDto request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!"STUDENT".equalsIgnoreCase(user.getCandidateLevel())) {
            throw new BadRequestException("Only healthcare students can submit a graduate upgrade request.");
        }

        CandidateProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        // Check if an existing pending upgrade request exists
        Optional<StudentUpgradeRequest> existing = upgradeRequestRepository
                .findTopByCandidateIdOrderByCreatedAtDesc(profile.getId());
        if (existing.isPresent() && "PENDING".equalsIgnoreCase(existing.get().getStatus())) {
            throw new BadRequestException("An upgrade request is already pending admin review.");
        }

        StudentUpgradeRequest upgrade = StudentUpgradeRequest.builder()
                .candidateId(profile.getId())
                .qualification(request.getQualification())
                .council(request.getCouncil())
                .regNumber(request.getRegNumber())
                .status("PENDING")
                .adminNotes("Submitted with provisional/degree documentation")
                .build();

        StudentUpgradeRequest saved = upgradeRequestRepository.save(upgrade);

        auditLogService.logEvent(user.getId(), "STUDENT_UPGRADE_REQUESTED", "StudentUpgradeRequest", saved.getId().toString(), null);

        return StudentUpgradeRequestDto.builder()
                .id(saved.getId())
                .qualification(saved.getQualification())
                .council(saved.getCouncil())
                .regNumber(saved.getRegNumber())
                .status(saved.getStatus())
                .adminNotes(saved.getAdminNotes())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteDocument(CustomUserPrincipal principal, UUID documentId) {
        CandidateProfile profile = profileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        CandidateDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getCandidateId().equals(profile.getId())) {
            throw new ForbiddenException("You cannot delete documents that do not belong to your profile");
        }

        boolean affectsKyc = isKycRequirementDocument(document.getDocumentType());
        if ("CERTIFICATION".equalsIgnoreCase(document.getDocumentType())) {
            List<CandidateCertificationDto> certifications = jsonToTypedList(profile.getCertifications(), new TypeReference<List<CandidateCertificationDto>>() {});
            profile.setCertifications(toJson(certifications.stream()
                    .filter(certification -> !documentId.toString().equals(certification.getDocumentId()))
                    .toList()));
        }
        documentRepository.delete(document);
        if (affectsKyc) {
            if ("VERIFIED".equalsIgnoreCase(profile.getKycStatus())) {
                profile.setKycStatus("DOCUMENTS_REQUIRED");
                profile.setVerifiedAt(null);
            }
            updateKycDocumentReadiness(profile);
        }
    }

    @Transactional
    public CandidateDocumentDto recordDocument(CustomUserPrincipal principal, String documentType, String originalFileName, String fileUrl) {
        if (documentType == null || documentType.isBlank()) {
            throw new BadRequestException("Document type is required");
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BadRequestException("File name is required");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BadRequestException("Document file is required");
        }
        if (estimateFileSize(fileUrl) > 5L * 1024 * 1024) {
            throw new BadRequestException("Document is too large. Maximum file size is 5 MB");
        }

        CandidateProfile profile = profileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        if ("PHOTO".equalsIgnoreCase(documentType)) {
            List<CandidateDocument> existingPhotos = documentRepository.findByCandidateId(profile.getId()).stream()
                    .filter(d -> "PHOTO".equalsIgnoreCase(d.getDocumentType()))
                    .toList();
            if (!existingPhotos.isEmpty()) {
                documentRepository.deleteAll(existingPhotos);
            }
        }

        CandidateDocument doc = CandidateDocument.builder()
                .candidateId(profile.getId())
                .documentType(documentType)
                .fileUrl(fileUrl)
                .originalFileName(originalFileName)
                .fileSizeBytes(estimateFileSize(fileUrl))
                .mimeType(resolveMimeType(fileUrl, originalFileName))
                .verified(false)
                .reviewStatus(isReviewableCredential(documentType) ? "PENDING" : "NOT_REQUIRED")
                .build();

        CandidateDocument saved = documentRepository.save(doc);
        if (isKycRequirementDocument(documentType)) updateKycDocumentReadiness(profile);
        auditLogService.logEvent(principal.getId(), "CANDIDATE_DOCUMENT_UPLOADED", "CandidateDocument", saved.getId().toString(), null);

        return CandidateDocumentDto.builder()
                .id(saved.getId())
                .documentType(saved.getDocumentType())
                .originalFileName(saved.getOriginalFileName())
                .fileUrl(saved.getFileUrl())
                .fileSizeBytes(saved.getFileSizeBytes())
                .mimeType(saved.getMimeType())
                .verified(saved.isVerified())
                .reviewStatus(saved.getReviewStatus())
                .reviewNotes(saved.getReviewNotes())
                .reviewedAt(saved.getReviewedAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public CandidateProfileDto exportCv(CustomUserPrincipal principal) {
        return getCandidateProfile(principal);
    }

    private void recordDocumentIfPresent(UUID candidateId, String docType, String fileUrl, String defaultName) {
        if (fileUrl != null && !fileUrl.isBlank()) {
            String lowerName = (defaultName != null ? defaultName : fileUrl).toLowerCase();
            String mime = "application/pdf";
            if (fileUrl.startsWith("data:application/pdf")) {
                mime = "application/pdf";
            } else if (fileUrl.startsWith("data:image/png")) {
                mime = "image/png";
            } else if (fileUrl.startsWith("data:image/jpeg") || fileUrl.startsWith("data:image/jpg")) {
                mime = "image/jpeg";
            } else if (fileUrl.startsWith("data:image/webp")) {
                mime = "image/webp";
            } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                mime = "image/jpeg";
            } else if (lowerName.endsWith(".png")) {
                mime = "image/png";
            } else if (lowerName.endsWith(".webp")) {
                mime = "image/webp";
            }

            long calculatedSize = 256000L;
            if (fileUrl.startsWith("data:")) {
                int commaIdx = fileUrl.indexOf(",");
                if (commaIdx != -1) {
                    calculatedSize = (long) ((fileUrl.length() - commaIdx - 1) * 0.75);
                }
            }

            CandidateDocument doc = CandidateDocument.builder()
                    .candidateId(candidateId)
                    .documentType(docType)
                    .fileUrl(fileUrl)
                    .originalFileName(defaultName != null ? defaultName : "Document.pdf")
                    .fileSizeBytes(calculatedSize > 0 ? calculatedSize : 256000L)
                    .mimeType(mime)
                    .verified(false)
                    .reviewStatus(isReviewableCredential(docType) ? "PENDING" : "NOT_REQUIRED")
                    .build();
            documentRepository.save(doc);
        }
    }

    private boolean isReviewableCredential(String documentType) {
        return documentType != null && !List.of("PHOTO", "PROFILE_PHOTO", "RESUME").contains(documentType.toUpperCase());
    }

    private boolean isKycRequirementDocument(String documentType) {
        return documentType != null && List.of("ID_PROOF", "REGISTRATION_CERTIFICATE", "DEGREE_CERTIFICATE", "BONAFIDE", "STUDENT_ID")
                .contains(documentType.toUpperCase());
    }

    private void updateKycDocumentReadiness(CandidateProfile profile) {
        if ("VERIFIED".equalsIgnoreCase(profile.getKycStatus())) return;
        User user = userRepository.findById(profile.getUserId()).orElse(null);
        boolean student = user != null && "STUDENT".equalsIgnoreCase(user.getCandidateLevel());
        List<CandidateDocument> documents = documentRepository.findByCandidateId(profile.getId());
        boolean readyForReview = CandidateKycRequirements.hasRequiredDocuments(student, documents, profile.getUniversityRegNo());
        profile.setKycStatus(readyForReview ? "PENDING" : "DOCUMENTS_REQUIRED");
        if (readyForReview) profile.setKycNotes(null);
        profile.setVerifiedAt(null);
        profileRepository.save(profile);
    }

    private String effectiveKycStatus(CandidateProfile profile, User user) {
        String status = profile.getKycStatus();
        if ("VERIFIED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) return status;
        boolean student = user != null && "STUDENT".equalsIgnoreCase(user.getCandidateLevel());
        return CandidateKycRequirements.hasRequiredDocuments(student,
                documentRepository.findByCandidateId(profile.getId()), profile.getUniversityRegNo())
                ? "PENDING" : "DOCUMENTS_REQUIRED";
    }

    private String resolveMimeType(String fileUrl, String fileName) {
        if (fileUrl != null && fileUrl.startsWith("data:")) {
            int end = fileUrl.indexOf(';');
            int comma = fileUrl.indexOf(',');
            int stop = end > 0 ? end : comma;
            if (stop > 5) return fileUrl.substring(5, stop).toLowerCase();
        }
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) return "application/pdf";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".webp")) return "image/webp";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private long estimateFileSize(String fileUrl) {
        if (fileUrl != null && fileUrl.startsWith("data:")) {
            int comma = fileUrl.indexOf(',');
            if (comma >= 0) return Math.max(0, (long) ((fileUrl.length() - comma - 1) * 0.75));
        }
        return 0L;
    }

    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Failed to serialize list to JSON", e);
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BadRequestException("Could not save profile details. Please review your entries and try again.");
        }
    }

    private <T> List<T> jsonToTypedList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to deserialize candidate profile list", e);
            return Collections.emptyList();
        }
    }

    private List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON to list", e);
            return Collections.emptyList();
        }
    }

    private CandidatePayoutMethodDto mapPayoutMethodToDto(CandidatePayoutMethod method) {
        return CandidatePayoutMethodDto.builder()
                .methodType(method.getMethodType())
                .upiId(method.getUpiId())
                .bankName(method.getBankName())
                .accountNumberLast4(method.getAccountNumberLast4())
                .ifsc(method.getIfsc())
                .accountHolderName(method.getAccountHolderName())
                .status("Verified".equalsIgnoreCase(method.getStatus()) && method.getVerifiedAt() == null ? "Pending" : method.getStatus())
                .verifiedAt(method.getVerifiedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public CandidateViewStatsDto getProfileViewStats(CustomUserPrincipal principal) {
        CandidateProfile profile = profileRepository.findByUserId(principal.getId())
                .orElse(null);

        if (profile == null) {
            return CandidateViewStatsDto.builder()
                    .totalViews(0)
                    .viewsToday(0)
                    .hospitalsViewedToday(0)
                    .weeklyGrowthPercent(0)
                    .hasIncreased(false)
                    .build();
        }

        UUID candidateId = profile.getId();
        long total = profileViewRepository.countByCandidateId(candidateId);

        java.time.OffsetDateTime startOfToday = java.time.OffsetDateTime.now()
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayViews = profileViewRepository.countByCandidateIdAndViewedAtGreaterThanEqual(candidateId, startOfToday);
        long hospitalsToday = profileViewRepository.countDistinctFacilitiesByCandidateIdAndViewedAtGreaterThanEqual(candidateId, startOfToday);

        java.time.OffsetDateTime sevenDaysAgo = java.time.OffsetDateTime.now().minusDays(7);
        java.time.OffsetDateTime fourteenDaysAgo = java.time.OffsetDateTime.now().minusDays(14);

        long last7Days = profileViewRepository.countByCandidateIdAndViewedAtGreaterThanEqual(candidateId, sevenDaysAgo);
        long prev7Days = profileViewRepository.countByCandidateIdAndViewedAtBetween(candidateId, fourteenDaysAgo, sevenDaysAgo);

        int growth = 0;
        if (prev7Days > 0) {
            growth = (int) Math.round(((double) (last7Days - prev7Days) / prev7Days) * 100);
        } else if (last7Days > 0) {
            growth = (int) Math.min(100, last7Days * 10);
        }

        return CandidateViewStatsDto.builder()
                .totalViews(total)
                .viewsToday(todayViews)
                .hospitalsViewedToday(hospitalsToday)
                .weeklyGrowthPercent(growth)
                .hasIncreased(growth >= 0)
                .build();
    }

    @Transactional
    public void recordProfileView(UUID candidateId, UUID facilityId, String facilityName) {
        String fac = facilityName != null && !facilityName.isBlank() ? facilityName : "Partner Healthcare Facility";
        CandidateProfileView view = CandidateProfileView.builder()
                .candidateId(candidateId)
                .facilityId(facilityId)
                .facilityName(fac)
                .build();
        profileViewRepository.save(view);

        // Send real-time institutional profile view alert to candidate
        CandidateProfile candidate = profileRepository.findById(candidateId).orElse(null);
        if (candidate != null) {
            Notification notif = Notification.builder()
                    .userId(candidate.getUserId())
                    .title("Profile Viewed by " + fac)
                    .message(fac + " reviewed your verified clinical CV and credentials.")
                    .type("PROFILE_VIEW")
                    .actionUrl("/profile")
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        }
    }
}
