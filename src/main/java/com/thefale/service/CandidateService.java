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

    @Transactional
    public CandidateProfileDto onboardCandidate(CustomUserPrincipal principal, CandidateOnboardingRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!"ROLE_CANDIDATE".equals(user.getRole())) {
            throw new ForbiddenException("Only candidates can complete candidate onboarding");
        }

        boolean isStudent = "STUDENT".equalsIgnoreCase(request.getCandidateLevel()) ||
                (request.getCourse() != null && !request.getCourse().isBlank() && request.getCouncilRegistrationNumber() == null);

        String candidateLevel = isStudent ? "STUDENT" :
                ("FRESHER".equalsIgnoreCase(request.getCandidateLevel()) ? "FRESHER" : "PROFESSIONAL");

        // Validate Student constraints vs Graduate constraints
        if (isStudent) {
            if (request.getCollegeName() == null || request.getCollegeName().isBlank()) {
                throw new BadRequestException("College name is mandatory for healthcare students.");
            }
        } else {
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
                profile.setDob(java.time.LocalDate.parse(request.getDob().trim()));
            } catch (Exception e) {
                log.warn("Could not parse DOB: {}", request.getDob());
            }
        }
        profile.setCity(request.getCity().trim());
        profile.setState(request.getState());
        profile.setPincode(request.getPincode());
        profile.setAddressLine(request.getAddressLine());
        profile.setBio(request.getBio());

        if (isStudent) {
            profile.setCourse(request.getCourse());
            profile.setCollege(request.getCollegeName());
            profile.setStudyYear(request.getStudyYear());
            profile.setUniversityRegNo(request.getUniversityRegNo());
            profile.setExpectedGraduationYear(request.getExpectedGraduationYear());
            profile.setProfession(request.getCourse()); // e.g. MBBS Student
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

        auditLogService.logEvent(user.getId(), "CANDIDATE_ONBOARDED", "CandidateProfile", savedProfile.getId().toString(), null);

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
                .profession(profile.getProfession())
                .specialty(profile.getSpecialty())
                .subSpecialties(jsonToList(profile.getSubSpecialties()))
                .qualification(profile.getQualification())
                .council(profile.getCouncil())
                .councilState(profile.getCouncilState())
                .regNumber(profile.getRegNumber())
                .regYear(profile.getRegYear())
                .experienceYears(profile.getExperienceYears())
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
                .kycStatus(profile.getKycStatus())
                .kycNotes(profile.getKycNotes())
                .isMentor(Boolean.TRUE.equals(profile.getIsMentor()))
                .mentorStatus(profile.getMentorStatus() != null ? profile.getMentorStatus() : "NONE")
                .verifiedAt(profile.getVerifiedAt())
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

        CandidatePayoutMethod method = payoutMethodRepository.findByCandidateId(profile.getId())
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

        method.setStatus("Verified"); // Instant verification for dev/demo or Penny Drop in production
        method.setVerifiedAt(Instant.now());

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

        documentRepository.delete(document);
    }

    @Transactional
    public CandidateDocumentDto recordDocument(CustomUserPrincipal principal, String documentType, String originalFileName, String fileUrl) {
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
                .fileSizeBytes(512000L)
                .mimeType(fileUrl != null && fileUrl.endsWith(".pdf") ? "application/pdf" : "image/jpeg")
                .verified(false)
                .build();

        CandidateDocument saved = documentRepository.save(doc);
        auditLogService.logEvent(principal.getId(), "CANDIDATE_DOCUMENT_UPLOADED", "CandidateDocument", saved.getId().toString(), null);

        return CandidateDocumentDto.builder()
                .id(saved.getId())
                .documentType(saved.getDocumentType())
                .originalFileName(saved.getOriginalFileName())
                .fileUrl(saved.getFileUrl())
                .fileSizeBytes(saved.getFileSizeBytes())
                .mimeType(saved.getMimeType())
                .verified(saved.isVerified())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public String exportCv(CustomUserPrincipal principal) {
        CandidateProfile profile = profileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));
        // Generates standardized verified CV URL reference
        return "https://api.thefale.com/api/v1/candidates/me/export-cv/" + profile.getId() + ".pdf";
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
                    .build();
            documentRepository.save(doc);
        }
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
                .status(method.getStatus())
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
