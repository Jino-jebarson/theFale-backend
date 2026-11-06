package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.CandidatePayoutMethodDto;
import com.thefale.dto.ShiftClaimDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.CandidateService;
import com.thefale.service.ShiftClaimService;
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
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payouts", description = "Candidate payouts, earnings ledger, payout methods, and PDF payout slips")
public class PayoutController {

    private final ShiftClaimService shiftClaimService;
    private final CandidateService candidateService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get current candidate's payout history and completed shift earnings")
    public ResponseEntity<ApiResponse<List<ShiftClaimDto>>> getMyPayouts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<ShiftClaimDto> claims = shiftClaimService.getMyClaims(principal);
        return ResponseEntity.ok(ApiResponse.success(claims));
    }

    @PostMapping("/me/method")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Configure candidate payout destination bank account or UPI ID")
    public ResponseEntity<ApiResponse<CandidatePayoutMethodDto>> configurePayoutMethod(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CandidatePayoutMethodDto request) {
        CandidatePayoutMethodDto method = candidateService.savePayoutMethod(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Payout method configured successfully", method));
    }

    @GetMapping("/{payoutId}/slip")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Download official TDS and platform fee deduction payout slip PDF")
    public ResponseEntity<ApiResponse<String>> getPayoutSlip(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID payoutId) {
        String slipUrl = "https://api.thefale.com/api/v1/payouts/" + payoutId + "/payout-slip.pdf";
        return ResponseEntity.ok(ApiResponse.success("Payout slip generated", slipUrl));
    }
}
