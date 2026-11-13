package com.thefale.controller;
import com.thefale.dto.*;
import com.thefale.service.FinanceReviewService;
import com.thefale.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/admin/finance") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class FinanceReviewController {
    private final FinanceReviewService service;
    @GetMapping("/deposits") public ApiResponse<List<Map<String,Object>>> deposits() { return ApiResponse.success(service.pendingTopUps()); }
    @PostMapping("/deposits/{id}/review") public ApiResponse<Void> review(@AuthenticationPrincipal CustomUserPrincipal actor, @PathVariable UUID id, @Valid @RequestBody TopUpReviewRequest request) {
        service.reviewTopUp(actor,id,request); return ApiResponse.success("Deposit reconciliation recorded");
    }
    @GetMapping("/accounts") public ApiResponse<List<Map<String,Object>>> accounts() { return ApiResponse.success(service.pendingAccounts()); }
    @PostMapping("/accounts/{kind}/{id}/review") public ApiResponse<Void> account(@AuthenticationPrincipal CustomUserPrincipal actor, @PathVariable String kind, @PathVariable UUID id, @Valid @RequestBody BankReviewRequest request) {
        service.reviewAccount(actor,kind,id,request); return ApiResponse.success("Ownership review recorded");
    }
}
