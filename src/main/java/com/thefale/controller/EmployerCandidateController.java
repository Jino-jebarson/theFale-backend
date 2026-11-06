package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.CandidateResdexDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.FacilityResdexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employer/candidates")
@RequiredArgsConstructor
@Tag(name = "Employer Resdex", description = "Endpoints for searching verified clinical talent and unlocking contacts with credits")
public class EmployerCandidateController {

    private final FacilityResdexService facilityResdexService;

    @GetMapping
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Search verified candidates in talent directory")
    public ResponseEntity<ApiResponse<List<CandidateResdexDto>>> searchCandidates(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String city) {
        List<CandidateResdexDto> candidates = facilityResdexService.searchCandidates(principal, query, specialty, city);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Unlock candidate contact details using 1 sourcing credit")
    public ResponseEntity<ApiResponse<CandidateResdexDto>> unlockCandidate(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        CandidateResdexDto candidate = facilityResdexService.unlockCandidate(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Candidate contact unlocked successfully", candidate));
    }
}
