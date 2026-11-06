package com.thefale.controller;

import com.thefale.dto.ApiResponse;
import com.thefale.dto.CreateSupportTicketRequest;
import com.thefale.dto.SupportTicketDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Support", description = "Candidate and facility help desk support tickets")
public class SupportController {

    private final SupportService supportService;

    @GetMapping
    @Operation(summary = "Get user submitted support tickets")
    public ResponseEntity<ApiResponse<List<SupportTicketDto>>> getMyTickets(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<SupportTicketDto> tickets = supportService.getMyTickets(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Support tickets fetched successfully", tickets));
    }

    @PostMapping
    @Operation(summary = "Submit a new support ticket")
    public ResponseEntity<ApiResponse<SupportTicketDto>> createTicket(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateSupportTicketRequest request
    ) {
        SupportTicketDto ticket = supportService.createTicket(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Support ticket created successfully", ticket));
    }
}
