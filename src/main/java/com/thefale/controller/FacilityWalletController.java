package com.thefale.controller;

import com.thefale.dto.*;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.FacilityWalletService;
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
@RequestMapping({"/api/v1/employer/wallet", "/api/v1/facility/wallet"})
@RequiredArgsConstructor
@Tag(name = "Facility Wallet", description = "Endpoints for institutional escrow balances, ledger transactions, and candidate sourcing credits")
public class FacilityWalletController {

    private final FacilityWalletService facilityWalletService;

    @GetMapping
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get facility wallet balance and credits")
    public ResponseEntity<ApiResponse<FacilityWalletDto>> getWallet(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        FacilityWalletDto wallet = facilityWalletService.getOrCreateWallet(principal);
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get facility wallet ledger transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionDto>>> getTransactions(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<WalletTransactionDto> transactions = facilityWalletService.getTransactions(principal);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @PostMapping({"/topup", "/top-up"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Submit manual UTR escrow top-up deposit")
    public ResponseEntity<ApiResponse<FacilityWalletDto>> topUp(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody WalletTopUpRequest request) {
        FacilityWalletDto wallet = facilityWalletService.topUp(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully into Escrow Reserve", wallet));
    }

    @PostMapping({"/buy-credits", "/credits/purchase"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Purchase candidate sourcing credits from available wallet balance")
    public ResponseEntity<ApiResponse<FacilityWalletDto>> buyCredits(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody BuyCreditsRequest request) {
        FacilityWalletDto wallet = facilityWalletService.buyCredits(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Candidate sourcing credits activated successfully", wallet));
    }
}
