package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.JobApplicationDto;
import com.thefale.dto.JobPostingDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs & Postings", description = "Permanent and internship job postings feed, details, saving, and applications")
public class JobController {

    private final JobService jobService;

    @GetMapping
    @Operation(summary = "Get jobs feed filtered by candidate level (student internship filter) and optional city")
    public ResponseEntity<ApiResponse<List<JobPostingDto>>> getJobs(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) String city
    ) {
        List<JobPostingDto> jobs = jobService.getJobFeed(principal, city);
        return ResponseEntity.ok(ApiResponse.success("Jobs fetched successfully", jobs));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed job posting information")
    public ResponseEntity<ApiResponse<JobPostingDto>> getJob(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id
    ) {
        JobPostingDto job = jobService.getJobById(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Job fetched successfully", job));
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('CANDIDATE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Apply for a job posting (enforces student internship guard)")
    public ResponseEntity<ApiResponse<JobApplicationDto>> apply(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id
    ) {
        JobApplicationDto application = jobService.applyForJob(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Application submitted successfully", application));
    }

    @PostMapping("/{id}/save")
    @PreAuthorize("hasRole('CANDIDATE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Save a job posting to candidate wishlist")
    public ResponseEntity<ApiResponse<Void>> saveJob(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id
    ) {
        jobService.saveJob(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Job saved successfully"));
    }

    @DeleteMapping("/{id}/save")
    @PreAuthorize("hasRole('CANDIDATE')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove a job posting from candidate wishlist")
    public ResponseEntity<ApiResponse<Void>> unsaveJob(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id
    ) {
        jobService.unsaveJob(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Job removed from saved list"));
    }
}
