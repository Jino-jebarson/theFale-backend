package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.FacilityOnboardingRequest;
import com.thefale.dto.FacilityProfileDto;
import com.thefale.dto.HospitalBankAccountDto;
import com.thefale.dto.UpdateHospitalBankRequest;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.FacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/facility")
@RequiredArgsConstructor
@Tag(name = "Facility Profile", description = "Endpoints for healthcare facility onboarding and institutional profile management")
public class FacilityController {

    private final FacilityService facilityService;

    @PostMapping("/onboard")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Onboard healthcare facility")
    public ResponseEntity<ApiResponse<FacilityProfileDto>> onboard(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody FacilityOnboardingRequest request) {
        FacilityProfileDto profile = facilityService.onboard(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Facility onboarded successfully", profile));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get facility profile")
    public ResponseEntity<ApiResponse<FacilityProfileDto>> getProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        FacilityProfileDto profile = facilityService.getProfile(principal);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Update facility profile")
    public ResponseEntity<ApiResponse<FacilityProfileDto>> updateProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody FacilityOnboardingRequest request) {
        FacilityProfileDto profile = facilityService.updateProfile(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Facility profile updated successfully", profile));
    }

    @GetMapping("/bank-account")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get linked hospital settlement bank account")
    public ResponseEntity<ApiResponse<HospitalBankAccountDto>> getBankAccount(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        HospitalBankAccountDto bankAccount = facilityService.getBankAccount(principal);
        return ResponseEntity.ok(ApiResponse.success(bankAccount));
    }

    @PutMapping("/bank-account")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Update and verify linked hospital settlement bank account")
    public ResponseEntity<ApiResponse<HospitalBankAccountDto>> updateBankAccount(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody UpdateHospitalBankRequest request) {
        HospitalBankAccountDto bankAccount = facilityService.updateBankAccount(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Hospital bank account verified successfully", bankAccount));
    }
}
