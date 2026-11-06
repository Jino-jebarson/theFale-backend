package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.JobApplicationDto;
import com.thefale.dto.UpdateApplicationStatusRequest;
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
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/employer/applications", "/api/v1/facility/applicants", "/api/v1/facility/applications"})
@RequiredArgsConstructor
@Tag(name = "Employer Applications", description = "Endpoints for reviewing applicants and updating recruitment pipeline status")
public class EmployerApplicationController {

    private final FacilityJobManagementService facilityJobManagementService;

    @GetMapping
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get applicants for facility jobs")
    public ResponseEntity<ApiResponse<List<JobApplicationDto>>> getApplications(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) String status) {
        List<JobApplicationDto> applications = facilityJobManagementService.getFacilityApplicants(principal, jobId, status);
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Update applicant status and recruitment stage")
    public ResponseEntity<ApiResponse<JobApplicationDto>> updateStatus(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        JobApplicationDto updated = facilityJobManagementService.updateApplicationStatus(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Applicant status updated successfully", updated));
    }
}
