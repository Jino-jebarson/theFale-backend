package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.JobApplicationDto;
import com.thefale.dto.WithdrawApplicationRequest;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/applications", "/api/v1/candidate/applications"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Candidate Applications", description = "Candidate applied job tracking and withdrawal")
public class CandidateApplicationController {

    private final JobService jobService;

    @GetMapping({"/me", ""})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get list of candidate submitted applications")
    public ResponseEntity<ApiResponse<List<JobApplicationDto>>> getMyApplications(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<JobApplicationDto> applications = jobService.getMyApplications(principal);
        return ResponseEntity.ok(ApiResponse.success("Applications fetched successfully", applications));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Withdraw an active job application")
    public ResponseEntity<ApiResponse<Void>> withdrawApplicationPost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) WithdrawApplicationRequest request
    ) {
        String reason = request != null ? request.getReason() : null;
        jobService.withdrawApplication(principal, id, reason);
        return ResponseEntity.ok(ApiResponse.success("Application withdrawn successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Withdraw an active job application (delete verb)")
    public ResponseEntity<ApiResponse<Void>> withdrawApplication(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) WithdrawApplicationRequest request
    ) {
        String reason = request != null ? request.getReason() : null;
        jobService.withdrawApplication(principal, id, reason);
        return ResponseEntity.ok(ApiResponse.success("Application withdrawn successfully"));
    }
}
