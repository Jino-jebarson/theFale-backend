package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.JobPostingDto;
import com.thefale.dto.PostJobRequest;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.FacilityJobManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/facility/jobs")
@RequiredArgsConstructor
@Tag(name = "Facility Jobs", description = "Endpoints for healthcare facilities to post and manage clinical vacancies")
public class FacilityJobController {

    private final FacilityJobManagementService facilityJobManagementService;

    @PostMapping
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Post a new clinical job or internship")
    public ResponseEntity<ApiResponse<JobPostingDto>> postJob(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody PostJobRequest request) {
        JobPostingDto job = facilityJobManagementService.postJob(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Job posted successfully", job));
    }

    @GetMapping
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get jobs posted by the current facility")
    public ResponseEntity<ApiResponse<List<JobPostingDto>>> getMyJobs(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<JobPostingDto> jobs = facilityJobManagementService.getMyJobs(principal);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }
}
