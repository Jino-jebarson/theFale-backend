package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.FacilityShiftPolicyDto;
import com.thefale.dto.LocumShiftDto;
import com.thefale.dto.PostShiftRequest;
import com.thefale.dto.ShiftClaimDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.ShiftClaimService;
import com.thefale.service.ShiftManagementService;
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
@RequiredArgsConstructor
@Tag(name = "Locum Shifts", description = "Endpoints for broadcasting, browsing, and claiming clinical locum shifts with escrow locking")
public class ShiftController {

    private final ShiftManagementService shiftManagementService;
    private final ShiftClaimService shiftClaimService;

    @GetMapping("/api/v1/shifts")
    @Operation(summary = "Get available open locum shifts")
    public ResponseEntity<ApiResponse<List<LocumShiftDto>>> getOpenShifts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<LocumShiftDto> shifts = shiftManagementService.getOpenShifts(principal);
        return ResponseEntity.ok(ApiResponse.success(shifts));
    }

    @PostMapping({"/api/v1/shifts", "/api/v1/facility/shifts"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Broadcast a new locum shift with mandatory escrow hold")
    public ResponseEntity<ApiResponse<LocumShiftDto>> postShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody PostShiftRequest request) {
        LocumShiftDto shift = shiftManagementService.postShift(principal, request);
        String msg = "OPEN".equalsIgnoreCase(shift.getStatus())
                ? "Locum shift is live immediately with escrow hold"
                : "First locum shift submitted for Compliance Admin review with escrow hold";
        return ResponseEntity.ok(ApiResponse.success(msg, shift));
    }

    @GetMapping({"/api/v1/shifts/facility/policy", "/api/v1/facility/shifts/policy"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get facility shift fee waiver and auto-approval policy")
    public ResponseEntity<ApiResponse<FacilityShiftPolicyDto>> getFacilityShiftPolicy(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(shiftManagementService.getFacilityShiftPolicy(principal)));
    }

    @PutMapping({"/api/v1/shifts/{id}", "/api/v1/facility/shifts/{id}"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Update a posted locum shift and reconcile escrow balance")
    public ResponseEntity<ApiResponse<LocumShiftDto>> updateShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PostShiftRequest request) {
        LocumShiftDto updated = shiftManagementService.updateShift(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Locum shift updated and escrow reconciled", updated));
    }

    @GetMapping({"/api/v1/shifts/facility", "/api/v1/facility/shifts"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Get shifts broadcasted by the current facility")
    public ResponseEntity<ApiResponse<List<LocumShiftDto>>> getFacilityShifts(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<LocumShiftDto> shifts = shiftManagementService.getFacilityShifts(principal);
        return ResponseEntity.ok(ApiResponse.success(shifts));
    }

    @DeleteMapping({"/api/v1/shifts/{id}", "/api/v1/facility/shifts/{id}"})
    @PreAuthorize("hasRole('FACILITY')")
    @Operation(summary = "Cancel an unfilled shift and refund escrow balance")
    public ResponseEntity<ApiResponse<Void>> cancelShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        shiftManagementService.cancelShift(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Shift cancelled and escrow refunded to available balance"));
    }

    @PostMapping("/api/v1/shifts/{id}/claim")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Claim a locum shift duty slot (Strictly barred for healthcare students)")
    public ResponseEntity<ApiResponse<ShiftClaimDto>> claimShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        ShiftClaimDto claim = shiftClaimService.claimShift(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Shift claimed successfully. Escrow payout held.", claim));
    }
}
