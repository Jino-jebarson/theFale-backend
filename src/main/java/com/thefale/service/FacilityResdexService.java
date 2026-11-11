package com.thefale.service;

import com.thefale.dto.CandidateResdexDto;
import com.thefale.entity.CandidateDocument;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.CandidateProfileView;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.FacilityWallet;
import com.thefale.entity.JobPosting;
import com.thefale.entity.UnlockedCandidate;
import com.thefale.entity.User;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.CandidateDocumentRepository;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.CandidateProfileViewRepository;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.FacilityWalletRepository;
import com.thefale.repository.JobPostingRepository;
import com.thefale.repository.UnlockedCandidateRepository;
import com.thefale.repository.UserRepository;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityResdexService {

    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityService facilityService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityWalletRepository facilityWalletRepository;
    private final UnlockedCandidateRepository unlockedCandidateRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final CandidateProfileViewRepository candidateProfileViewRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public List<CandidateResdexDto> searchCandidates(CustomUserPrincipal principal, String query, String specialty, String city) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        return candidateProfileRepository.findAll().stream()
                .filter(c -> !Boolean.FALSE.equals(c.getOpenToWork()))
                .filter(c -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.toLowerCase();
                    return (c.getName() != null && c.getName().toLowerCase().contains(q)) ||
                            (c.getSpecialty() != null && c.getSpecialty().toLowerCase().contains(q)) ||
                            (c.getProfession() != null && c.getProfession().toLowerCase().contains(q)) ||
                            (c.getCity() != null && c.getCity().toLowerCase().contains(q));
                })
                .filter(c -> specialty == null || specialty.equalsIgnoreCase("All") || specialty.equalsIgnoreCase(c.getSpecialty()) || specialty.equalsIgnoreCase(c.getProfession()))
                .filter(c -> city == null || city.equalsIgnoreCase("All") || city.equalsIgnoreCase(c.getCity()))
                .map(c -> mapToResdexDto(c, facility))
                .collect(Collectors.toList());
    }

    @Transactional
    public CandidateResdexDto unlockCandidate(CustomUserPrincipal principal, UUID candidateId) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        if (Boolean.FALSE.equals(candidate.getOpenToWork())) {
            throw new BadRequestException("This candidate has paused clinical opportunities and is currently hidden from recruiter searches.");
        }

        if (!unlockedCandidateRepository.existsByFacilityIdAndCandidateId(facility.getId(), candidate.getId())) {
            FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));

            if (wallet.getCandidateCredits() < 1) {
                throw new BadRequestException("You have 0 sourcing credits available. Please purchase a credit package to unlock candidate contact details.");
            }

            wallet.setCandidateCredits(wallet.getCandidateCredits() - 1);
            facilityWalletRepository.save(wallet);

            UnlockedCandidate unlock = UnlockedCandidate.builder()
                    .facilityId(facility.getId())
                    .candidateId(candidate.getId())
                    .creditsSpent(1)
                    .build();
            unlockedCandidateRepository.save(unlock);

            // Record institutional profile view dynamically for candidate dashboard
            candidateProfileViewRepository.save(CandidateProfileView.builder()
                    .candidateId(candidate.getId())
                    .facilityId(facility.getId())
                    .facilityName(facility.getHospitalName() != null && !facility.getHospitalName().isBlank() ? facility.getHospitalName() : "Partner Healthcare Facility")
                    .build());

            auditLogService.logEvent(principal.getId(), "CANDIDATE_CONTACT_UNLOCKED", "CandidateProfile", candidate.getId().toString(), null);
        }

        return mapToResdexDto(candidate, facility);
    }

    private CandidateResdexDto mapToResdexDto(CandidateProfile candidate, FacilityProfile facility) {
        UUID facilityId = facility != null ? facility.getId() : null;
        boolean isUnlocked = facilityId != null && unlockedCandidateRepository.existsByFacilityIdAndCandidateId(facilityId, candidate.getId());
        User candidateUser = userRepository.findById(candidate.getUserId()).orElse(null);
        List<JobPosting> facilityJobs = facilityId != null
                ? jobPostingRepository.findByFacilityIdAndDeletedAtIsNull(facilityId).stream()
                        .filter(j -> "OPEN".equalsIgnoreCase(j.getStatus()))
                        .toList()
                : List.of();
        boolean isStudent = candidateUser != null && "STUDENT".equalsIgnoreCase(candidateUser.getCandidateLevel());
        int computedMatch = ClinicalMatchingEngine.calculateResdexMatch(candidate, isStudent, facility, facilityJobs);

        List<CandidateDocument> docs = candidateDocumentRepository.findByCandidateId(candidate.getId());
        CandidateDocument resumeDoc = docs.stream()
                .filter(d -> "RESUME".equalsIgnoreCase(d.getDocumentType()))
                .findFirst()
                .orElse(null);
        String resumeUrl = isUnlocked && resumeDoc != null ? resumeDoc.getFileUrl() : null;
        String resumeFileName = isUnlocked && resumeDoc != null ? resumeDoc.getOriginalFileName() : null;

        String rawPhone = candidateUser != null && candidateUser.getPhone() != null ? candidateUser.getPhone() : "+91 98401 23456";
        String rawEmail = candidateUser != null && candidateUser.getEmail() != null ? candidateUser.getEmail() : "candidate@fale.care";
        String rawReg = candidate.getRegNumber() != null ? candidate.getRegNumber() : "Council Verified";

        String maskedPhone = maskPhone(rawPhone);
        String maskedEmail = maskEmail(rawEmail);
        String maskedReg = "Reg #••••••••";

        String displayPhone = isUnlocked ? rawPhone : maskedPhone;
        String displayEmail = isUnlocked ? rawEmail : maskedEmail;
        String displayReg = isUnlocked ? rawReg : maskedReg;

        return CandidateResdexDto.builder()
                .candidateId(candidate.getId())
                .name(candidate.getName())
                .city(candidate.getCity() != null ? candidate.getCity() : "Chennai")
                .candidateLevel(candidateUser != null ? candidateUser.getCandidateLevel() : "PROFESSIONAL")
                .profession(candidate.getProfession() != null ? candidate.getProfession() : "Clinician")
                .specialty(candidate.getSpecialty() != null ? candidate.getSpecialty() : candidate.getProfession())
                .qualification(candidate.getQualification() != null ? candidate.getQualification() : "MBBS / Healthcare")
                .council(candidate.getCouncil() != null ? candidate.getCouncil() : "State Medical Council")
                .regNumber(displayReg)
                .regNumberMasked(maskedReg)
                .experienceYears(candidate.getExperienceYears() != null ? candidate.getExperienceYears() : "1 year")
                .matchScore(computedMatch)
                .contactUnlocked(isUnlocked)
                .phone(displayPhone)
                .email(displayEmail)
                .phoneMasked(maskedPhone)
                .emailMasked(maskedEmail)
                .unlockedPhone(rawPhone)
                .unlockedEmail(rawEmail)
                .resumeUrl(resumeUrl)
                .resumeFileName(resumeFileName)
                .verified("VERIFIED".equalsIgnoreCase(candidate.getKycStatus()))
                .openToWork(!Boolean.FALSE.equals(candidate.getOpenToWork()))
                .build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "+91 ••••• •••••";
        }
        return phone.substring(0, 5) + "•••••" + phone.substring(phone.length() - 2);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "••••••••@••••••.com";
        }
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        String maskedName = name.length() > 2 ? name.charAt(0) + "•••••" + name.charAt(name.length() - 1) : name.charAt(0) + "••••";
        return maskedName + "@" + domain;
    }
}
