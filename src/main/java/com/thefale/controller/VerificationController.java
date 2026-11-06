package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.VerificationBadgeResponse;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.FacilityProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verifications")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Public credential verification badge check")
public class VerificationController {

    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;

    @GetMapping("/{id}")
    @Operation(summary = "Public verification check by QR/ID")
    public ResponseEntity<ApiResponse<VerificationBadgeResponse>> getVerificationBadge(@PathVariable UUID id) {
        // Try candidate profile
        Optional<CandidateProfile> candidateOpt = candidateProfileRepository.findById(id);
        if (candidateOpt.isPresent()) {
            CandidateProfile candidate = candidateOpt.get();
            String name = candidate.getName() != null ? candidate.getName() : "Candidate";
            String maskedName = maskName(name);
            boolean verified = "VERIFIED".equalsIgnoreCase(candidate.getKycStatus());
            String accreditation = verified ? "Verified Healthcare Professional (" + candidate.getProfession() + ")" : "Pending Council Verification";

            VerificationBadgeResponse response = VerificationBadgeResponse.builder()
                    .verificationId(candidate.getId())
                    .entityType("CANDIDATE")
                    .maskedName(maskedName)
                    .verified(verified)
                    .accreditationText(accreditation)
                    .build();
            return ResponseEntity.ok(ApiResponse.success(response));
        }

        // Try facility profile
        Optional<FacilityProfile> facilityOpt = facilityProfileRepository.findById(id);
        if (facilityOpt.isPresent()) {
            FacilityProfile facility = facilityOpt.get();
            String name = facility.getHospitalName() != null ? facility.getHospitalName() : "Hospital";
            String maskedName = maskName(name);
            boolean verified = "VERIFIED".equalsIgnoreCase(facility.getKycStatus());
            String accreditation = verified ? "Accredited Healthcare Facility (" + facility.getCity() + ")" : "Establishment License Review Pending";

            VerificationBadgeResponse response = VerificationBadgeResponse.builder()
                    .verificationId(facility.getId())
                    .entityType("FACILITY")
                    .maskedName(maskedName)
                    .verified(verified)
                    .accreditationText(accreditation)
                    .build();
            return ResponseEntity.ok(ApiResponse.success(response));
        }

        throw new ResourceNotFoundException("Verification credential record not found for ID: " + id);
    }

    private String maskName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() <= 2) {
                sb.append(part);
            } else {
                sb.append(part.charAt(0))
                  .append("*".repeat(part.length() - 2))
                  .append(part.charAt(part.length() - 1));
            }
            if (i < parts.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}
