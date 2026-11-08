package com.thefale.controller;

import com.thefale.dto.*;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/candidates", "/api/v1/candidate"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Candidate Profile", description = "Healthcare student and graduate onboarding, profiles, documents, and payout methods")
public class CandidateController {

    private final CandidateService candidateService;

    @PostMapping("/onboarding")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Complete student or graduate candidate onboarding")
    public ResponseEntity<ApiResponse<CandidateProfileDto>> onboardCandidate(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CandidateOnboardingRequest request
    ) {
        CandidateProfileDto profile = candidateService.onboardCandidate(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Candidate onboarding completed successfully", profile));
    }

    @GetMapping({"/me/profile", "/profile"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get candidate profile details with verification status and documents")
    public ResponseEntity<ApiResponse<CandidateProfileDto>> getProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        CandidateProfileDto profile = candidateService.getCandidateProfile(principal);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", profile));
    }

    @PutMapping({"/me/profile", "/profile"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Update candidate profile details")
    public ResponseEntity<ApiResponse<CandidateProfileDto>> updateProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CandidateOnboardingRequest request
    ) {
        CandidateProfileDto profile = candidateService.onboardCandidate(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }

    @RequestMapping(value = {"/me/open-to-work", "/open-to-work"}, method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Toggle candidate open to clinical opportunities status")
    public ResponseEntity<ApiResponse<CandidateProfileDto>> updateOpenToWork(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody(required = false) java.util.Map<String, Object> body
    ) {
        boolean open = false;
        if (body != null) {
            Object val = body.get("openToWork");
            if (val == null) {
                val = body.get("open");
            }
            if (val instanceof Boolean b) {
                open = b;
            } else if (val != null) {
                open = Boolean.parseBoolean(val.toString());
            }
        }
        CandidateProfileDto profile = candidateService.updateOpenToWork(principal, open);
        return ResponseEntity.ok(ApiResponse.success("Availability status updated successfully", profile));
    }

    @GetMapping({"/me/payout-methods", "/payout-methods"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get candidate payout destination bank account or UPI ID")
    public ResponseEntity<ApiResponse<CandidatePayoutMethodDto>> getPayoutMethod(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        CandidatePayoutMethodDto method = candidateService.getPayoutMethod(principal);
        return ResponseEntity.ok(ApiResponse.success(method));
    }

    @PostMapping({"/me/payout-methods", "/payout-methods"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Save or update candidate payout bank account or UPI ID")
    public ResponseEntity<ApiResponse<CandidatePayoutMethodDto>> savePayoutMethod(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CandidatePayoutMethodDto request
    ) {
        CandidatePayoutMethodDto method = candidateService.savePayoutMethod(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Payout method saved successfully", method));
    }

    @PostMapping({"/upgrade-request", "/student-upgrade"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Submit a request to upgrade healthcare Student profile to Graduate with degree verification")
    public ResponseEntity<ApiResponse<StudentUpgradeRequestDto>> requestStudentUpgrade(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody StudentUpgradeRequestDto request
    ) {
        StudentUpgradeRequestDto upgrade = candidateService.requestStudentUpgrade(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Student upgrade request submitted for review", upgrade));
    }

    @PostMapping({"/me/documents", "/documents"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Upload candidate document metadata")
    public ResponseEntity<ApiResponse<CandidateDocumentDto>> uploadDocument(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody(required = false) CandidateDocumentUploadRequest body,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String originalFileName,
            @RequestParam(required = false) String fileUrl
    ) {
        String finalType = body != null && body.getDocumentType() != null ? body.getDocumentType() : documentType;
        String finalName = body != null && body.getOriginalFileName() != null ? body.getOriginalFileName() : originalFileName;
        String finalUrl = body != null && body.getFileUrl() != null ? body.getFileUrl() : fileUrl;

        CandidateDocumentDto doc = candidateService.recordDocument(principal, finalType, finalName, finalUrl);
        return ResponseEntity.ok(ApiResponse.success("Document uploaded successfully", doc));
    }

    @DeleteMapping({"/me/documents/{id}", "/documents/{id}"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Delete an uploaded candidate document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id
    ) {
        candidateService.deleteDocument(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully"));
    }

    @GetMapping("/me/export-cv")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Export standardized verified CV")
    public ResponseEntity<ApiResponse<String>> exportCv(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        String cvUrl = candidateService.exportCv(principal);
        return ResponseEntity.ok(ApiResponse.success("CV exported successfully", cvUrl));
    }

    @GetMapping({"/me/view-stats", "/view-stats"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get dynamic institutional view analytics for candidate profile")
    public ResponseEntity<ApiResponse<CandidateViewStatsDto>> getViewStats(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        CandidateViewStatsDto stats = candidateService.getProfileViewStats(principal);
        return ResponseEntity.ok(ApiResponse.success("Profile view statistics fetched successfully", stats));
    }

    @PostMapping("/{id}/view")
    @Operation(summary = "Record institutional view when facility browses candidate profile")
    public ResponseEntity<ApiResponse<Void>> recordView(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false, defaultValue = "Partner Healthcare Facility") String facilityName
    ) {
        candidateService.recordProfileView(id, facilityId, facilityName);
        return ResponseEntity.ok(ApiResponse.success("Profile view recorded"));
    }
}
