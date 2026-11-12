package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.CompleteShiftRequest;
import com.thefale.dto.RateShiftClaimRequest;
import com.thefale.dto.ShiftCheckInRequest;
import com.thefale.dto.ShiftClaimDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.ShiftClaimService;
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
@RequestMapping({"/api/v1/shifts/claims", "/api/v1/candidate/claims"})
@RequiredArgsConstructor
@Tag(name = "Candidate Earnings & Claims", description = "Endpoints for tracking shift duties, check-in, completion, disputes, and escrow payouts")
public class CandidateEarningsController {

    private final ShiftClaimService shiftClaimService;

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get candidate shift claims and payout breakdown")
    public ResponseEntity<ApiResponse<List<ShiftClaimDto>>> getMyClaims(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<ShiftClaimDto> claims = shiftClaimService.getMyClaims(principal);
        return ResponseEntity.ok(ApiResponse.success(claims));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Check in on hospital floor for confirmed shift duty with PIN verification")
    public ResponseEntity<ApiResponse<ShiftClaimDto>> checkIn(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ShiftCheckInRequest request) {
        ShiftClaimDto claim = shiftClaimService.checkIn(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Checked in to shift duty successfully", claim));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'FACILITY')")
    @Operation(summary = "Sign off completed clinical shift duty with TDS verification")
    public ResponseEntity<ApiResponse<ShiftClaimDto>> completeShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteShiftRequest request) {
        ShiftClaimDto claim = shiftClaimService.completeShift(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Shift duty marked completed. TDS compliance verified and payout scheduled for disbursement.", claim));
    }

    @PostMapping("/{id}/dispute")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'FACILITY')")
    @Operation(summary = "Dispute a shift duty or hours")
    public ResponseEntity<ApiResponse<ShiftClaimDto>> disputeShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String effectiveReason = (reason != null && !reason.isBlank())
                ? reason
                : (body != null ? body.get("reason") : null);
        ShiftClaimDto claim = shiftClaimService.disputeShift(principal, id, effectiveReason);
        return ResponseEntity.ok(ApiResponse.success("Shift duty marked disputed for admin mediation", claim));
    }

    @PostMapping("/{id}/rate")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Submit 1-5 star hospital performance rating for a completed shift")
    public ResponseEntity<ApiResponse<ShiftClaimDto>> rateShiftClaim(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RateShiftClaimRequest request) {
        ShiftClaimDto claim = shiftClaimService.rateShiftClaim(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Clinician shift rating recorded successfully", claim));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Cancel shift claim before shift start (auto-refunds escrow to hospital)")
    public ResponseEntity<ApiResponse<Void>> cancelClaimPost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        shiftClaimService.cancelClaim(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Shift claim cancelled and hospital escrow refunded"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Cancel shift claim before shift start (auto-refunds escrow to hospital)")
    public ResponseEntity<ApiResponse<Void>> cancelClaim(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        shiftClaimService.cancelClaim(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Shift claim cancelled and hospital escrow refunded"));
    }
}
