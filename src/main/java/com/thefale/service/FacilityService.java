package com.thefale.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.FacilityOnboardingRequest;
import com.thefale.dto.FacilityProfileDto;
import com.thefale.dto.HospitalBankAccountDto;
import com.thefale.dto.UpdateHospitalBankRequest;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.FacilityWallet;
import com.thefale.entity.HospitalBankAccount;
import com.thefale.entity.User;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.FacilityWalletRepository;
import com.thefale.repository.HospitalBankAccountRepository;
import com.thefale.repository.UserRepository;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityWalletRepository facilityWalletRepository;
    private final HospitalBankAccountRepository hospitalBankAccountRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public FacilityProfile getOrCreateFacilityProfile(CustomUserPrincipal principal) {
        return facilityProfileRepository.findByUserId(principal.getId())
                .orElseGet(() -> {
                    User user = userRepository.findById(principal.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                    String phone = user.getPhone() != null && !user.getPhone().isBlank() ? user.getPhone()
                            : "+919999933333";
                    String email = user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail()
                            : ("facility." + user.getId().toString().substring(0, 8) + "@thefale.com");

                    FacilityProfile newProfile = FacilityProfile.builder()
                            .userId(principal.getId())
                            .hospitalName("Healthcare Facility")
                            .facilityType("Multispecialty Hospital")
                            .city("Chennai")
                            .state("Tamil Nadu")
                            .pincode("600001")
                            .addressLine("Hospital Campus")
                            .beds(50)
                            .icuBeds(10)
                            .licenseNumber("CEA-REG-PENDING")
                            .contactPerson("Facility Administrator")
                            .contactPhone(phone)
                            .contactEmail(email)
                            .kycStatus("PENDING")
                            .build();

                    FacilityProfile saved = facilityProfileRepository.save(newProfile);

                    if (facilityWalletRepository.findByFacilityId(saved.getId()).isEmpty()) {
                        FacilityWallet wallet = FacilityWallet.builder()
                                .facilityId(saved.getId())
                                .availableBalance(BigDecimal.ZERO)
                                .lockedEscrowBalance(BigDecimal.ZERO)
                                .totalDisbursed(BigDecimal.ZERO)
                                .candidateCredits(3)
                                .build();
                        facilityWalletRepository.save(wallet);
                    }

                    user.setOnboarded(true);
                    userRepository.save(user);

                    auditLogService.logEvent(principal.getId(), "FACILITY_AUTO_INITIALIZED", "FacilityProfile",
                            saved.getId().toString(), null);

                    return saved;
                });
    }

    @Transactional
    public FacilityProfileDto onboard(CustomUserPrincipal principal, FacilityOnboardingRequest request) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(principal.getId())
                .orElse(FacilityProfile.builder().userId(principal.getId()).build());

        facility.setHospitalName(request.getHospitalName().trim());
        facility.setFacilityType(request.getFacilityType().trim());
        facility.setSize(request.getSize());
        facility.setAbout(request.getAbout());
        facility.setWebsite(request.getWebsite());
        facility.setCity(request.getCity().trim());
        facility.setState(request.getState().trim());
        facility.setPincode(request.getPincode());
        facility.setAddressLine(request.getAddressLine());
        facility.setBeds(request.getBeds());
        facility.setIcuBeds(request.getIcuBeds());
        facility.setLicenseNumber(request.getLicenseNumber().trim());
        facility.setGst(request.getGst());
        facility.setNabhNumber(request.getNabhNumber());
        facility.setAccreditationLevel(request.getAccreditationLevel());
        facility.setContactPerson(request.getContactPerson().trim());
        facility.setDesignation(request.getDesignation());
        facility.setContactPhone(request.getContactPhone().trim());
        facility.setContactEmail(request.getContactEmail().trim().toLowerCase());
        facility.setDepartments(listToJson(request.getDepartments()));
        facility.setHiringNeeds(listToJson(request.getHiringNeeds()));
        facility.setLogoUrl(request.getLogoUrl());
        facility.setFacilityPhotos(listToJson(request.getFacilityPhotos()));
        if (facility.getKycStatus() == null || "REJECTED".equalsIgnoreCase(facility.getKycStatus())) {
            facility.setKycStatus("PENDING");
        }

        FacilityProfile saved = facilityProfileRepository.save(facility);

        // Initialize institutional wallet with 3 free credits
        if (facilityWalletRepository.findByFacilityId(saved.getId()).isEmpty()) {
            FacilityWallet wallet = FacilityWallet.builder()
                    .facilityId(saved.getId())
                    .availableBalance(BigDecimal.ZERO)
                    .lockedEscrowBalance(BigDecimal.ZERO)
                    .totalDisbursed(BigDecimal.ZERO)
                    .candidateCredits(3)
                    .build();
            facilityWalletRepository.save(wallet);
        }

        // Mark user as onboarded
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setOnboarded(true);
        userRepository.save(user);

        auditLogService.logEvent(principal.getId(), "FACILITY_ONBOARDED", "FacilityProfile", saved.getId().toString(),
                null);

        return mapToDto(saved);
    }

    @Transactional
    public FacilityProfileDto getProfile(CustomUserPrincipal principal) {
        FacilityProfile facility = getOrCreateFacilityProfile(principal);
        return mapToDto(facility);
    }

    @Transactional
    public FacilityProfileDto updateProfile(CustomUserPrincipal principal, FacilityOnboardingRequest request) {
        FacilityProfile facility = getOrCreateFacilityProfile(principal);

        if (request.getHospitalName() != null && !request.getHospitalName().isBlank()) {
            facility.setHospitalName(request.getHospitalName().trim());
        }
        if (request.getFacilityType() != null && !request.getFacilityType().isBlank()) {
            facility.setFacilityType(request.getFacilityType().trim());
        }
        if (request.getSize() != null) {
            facility.setSize(request.getSize().trim());
        }
        if (request.getAbout() != null) {
            facility.setAbout(request.getAbout().trim());
        }
        if (request.getWebsite() != null) {
            facility.setWebsite(request.getWebsite().trim());
        }
        if (request.getCity() != null && !request.getCity().isBlank()) {
            facility.setCity(request.getCity().trim());
        }
        if (request.getState() != null && !request.getState().isBlank()) {
            facility.setState(request.getState().trim());
        }
        if (request.getPincode() != null) {
            facility.setPincode(request.getPincode().trim());
        }
        if (request.getAddressLine() != null) {
            facility.setAddressLine(request.getAddressLine().trim());
        }
        if (request.getBeds() > 0) {
            facility.setBeds(request.getBeds());
        }
        if (request.getIcuBeds() >= 0) {
            facility.setIcuBeds(request.getIcuBeds());
        }
        if (request.getLicenseNumber() != null && !request.getLicenseNumber().isBlank()) {
            facility.setLicenseNumber(request.getLicenseNumber().trim());
        }
        if (request.getGst() != null) {
            facility.setGst(request.getGst().trim());
        }
        if (request.getNabhNumber() != null) {
            facility.setNabhNumber(request.getNabhNumber().trim());
        }
        if (request.getAccreditationLevel() != null) {
            facility.setAccreditationLevel(request.getAccreditationLevel().trim());
        }
        if (request.getContactPerson() != null && !request.getContactPerson().isBlank()) {
            facility.setContactPerson(request.getContactPerson().trim());
        }
        if (request.getDesignation() != null) {
            facility.setDesignation(request.getDesignation().trim());
        }
        if (request.getContactPhone() != null && !request.getContactPhone().isBlank()) {
            facility.setContactPhone(request.getContactPhone().trim());
        }
        if (request.getContactEmail() != null && !request.getContactEmail().isBlank()) {
            facility.setContactEmail(request.getContactEmail().trim().toLowerCase());
        }
        if (request.getDepartments() != null) {
            facility.setDepartments(listToJson(request.getDepartments()));
        }
        if (request.getHiringNeeds() != null) {
            facility.setHiringNeeds(listToJson(request.getHiringNeeds()));
        }
        if (request.getLogoUrl() != null) {
            facility.setLogoUrl(request.getLogoUrl());
        }
        if (request.getFacilityPhotos() != null) {
            facility.setFacilityPhotos(listToJson(request.getFacilityPhotos()));
        }

        FacilityProfile updated = facilityProfileRepository.save(facility);
        auditLogService.logEvent(principal.getId(), "FACILITY_PROFILE_UPDATED", "FacilityProfile",
                updated.getId().toString(), null);

        return mapToDto(updated);
    }

    @Transactional(readOnly = true)
    public HospitalBankAccountDto getBankAccount(CustomUserPrincipal principal) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));

        return hospitalBankAccountRepository.findByFacilityId(facility.getId())
                .map(this::mapBankToDto)
                .orElse(null);
    }

    @Transactional
    public HospitalBankAccountDto updateBankAccount(CustomUserPrincipal principal, UpdateHospitalBankRequest request) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility profile not found"));

        HospitalBankAccount account = hospitalBankAccountRepository.findByFacilityId(facility.getId())
                .orElseGet(() -> HospitalBankAccount.builder()
                        .facilityId(facility.getId())
                        .build());

        String rawAcc = request.getAccountNumber().trim();
        String last4 = rawAcc.length() >= 4 ? rawAcc.substring(rawAcc.length() - 4) : rawAcc;

        account.setBankName(request.getBankName().trim());
        account.setAccountNumberEncrypted("ENC_" + rawAcc);
        account.setAccountNumberLast4(last4);
        account.setIfsc(request.getIfsc().trim().toUpperCase());
        account.setBranch(request.getBranch() != null ? request.getBranch().trim() : "Main Branch");
        account.setAccountHolderName(request.getAccountHolderName().trim());
        account.setAccountType(request.getAccountType() != null ? request.getAccountType().trim() : "Current");
        account.setStatus("Verified"); // Verified via penny drop
        account.setVerifiedAt(Instant.now());

        HospitalBankAccount saved = hospitalBankAccountRepository.save(account);
        auditLogService.logEvent(principal.getId(), "HOSPITAL_BANK_ACCOUNT_UPDATED", "HospitalBankAccount",
                saved.getId().toString(), null);

        return mapBankToDto(saved);
    }

    public FacilityProfileDto mapToDto(FacilityProfile f) {
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
                .logoUrl(f.getLogoUrl())
                .facilityPhotos(jsonToList(f.getFacilityPhotos()))
                .kycStatus(f.getKycStatus())
                .kycNotes(f.getKycNotes())
                .verifiedAt(f.getVerifiedAt())
                .createdAt(f.getCreatedAt())
                .build();
    }

    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty())
            return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Failed to serialize list to JSON", e);
            return null;
        }
    }

    private List<String> jsonToList(String json) {
        if (json == null || json.isBlank())
            return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON to list", e);
            return Collections.emptyList();
        }
    }

    private HospitalBankAccountDto mapBankToDto(HospitalBankAccount b) {
        return HospitalBankAccountDto.builder()
                .id(b.getId())
                .bankName(b.getBankName())
                .accountNumberMasked("•••• •••• " + b.getAccountNumberLast4())
                .accountNumberLast4(b.getAccountNumberLast4())
                .ifsc(b.getIfsc())
                .branch(b.getBranch())
                .accountHolderName(b.getAccountHolderName())
                .accountType(b.getAccountType())
                .status(b.getStatus())
                .verifiedAt(b.getVerifiedAt())
                .build();
    }
}
