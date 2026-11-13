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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

    @GetMapping({"/me/method", "/payout-methods"})
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get candidate payout destination bank account or UPI ID")
    public ResponseEntity<ApiResponse<CandidatePayoutMethodDto>> getPayoutMethod(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        CandidatePayoutMethodDto method = candidateService.getPayoutMethod(principal);
        return ResponseEntity.ok(ApiResponse.success(method));
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
    @Operation(summary = "Get verified payout slip details & download link (ownership enforced)")
    public ResponseEntity<ApiResponse<ShiftClaimDto>> getPayoutSlip(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID payoutId) {
        ShiftClaimDto dto = shiftClaimService.getClaimForPayoutSlip(principal, payoutId);
        return ResponseEntity.ok(ApiResponse.success("Payout slip verified", dto));
    }

    @GetMapping(value = "/{payoutId}/payout-slip.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Download official TDS and platform fee deduction payout slip PDF")
    public ResponseEntity<byte[]> downloadPayoutSlipPdf(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID payoutId) {
        ShiftClaimDto dto = shiftClaimService.getClaimForPayoutSlip(principal, payoutId);
        byte[] pdfBytes = buildPayoutSlipPdf(dto);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String filename = (dto.getPayoutSlipRef() != null ? dto.getPayoutSlipRef() : "FALE-Payout-" + payoutId.toString().substring(0, 8)) + ".pdf";
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    private byte[] buildPayoutSlipPdf(ShiftClaimDto dto) {
        List<String> lines = List.of(
                "FALE HEALTHCARE - RECORDED PAYOUT ADVICE",
                "------------------------------------------------------------------------",
                "Slip Reference : " + safe(dto.getPayoutSlipRef(), dto.getId().toString()),
                "Payout Status  : " + safe(dto.getPayoutStatus(), "IN_ESCROW"),
                "UTR Reference  : " + safe(dto.getUtr(), "Not recorded"),
                "Disbursed at   : " + (dto.getDisbursedAt() == null ? "Not recorded" : dto.getDisbursedAt().toString()),
                "Destination    : " + safe(dto.getPayoutMethodSnapshot(), "Historical destination not recorded"),
                "------------------------------------------------------------------------",
                "Clinician      : " + safe(dto.getCandidateName(), "Name not recorded"),
                "Hospital       : " + safe(dto.getFacilityName(), "Partner Facility"),
                "Duty Role      : " + safe(dto.getShiftTitle(), "Locum Shift") + " (" + safe(dto.getDepartment(), "General") + ")",
                "Verified Hours : " + (dto.getActualHours() != null ? dto.getActualHours() : dto.getHours()) + " hrs",
                "------------------------------------------------------------------------",
                String.format("Gross Remuneration       : INR %.2f", dto.getGrossAmountPaise() / 100.0),
                String.format("Platform Fee (%.1f%%)      : -INR %.2f", dto.getPlatformFeePercent(), dto.getPlatformFeePaise() / 100.0),
                String.format("Recorded TDS withholding : -INR %.2f", dto.getTdsAmountPaise() / 100.0),
                "------------------------------------------------------------------------",
                String.format(("DISBURSED".equals(dto.getPayoutStatus()) ? "NET PAID                 : INR %.2f" : "NET PAYABLE (NOT PAID)    : INR %.2f"), dto.getNetAmountPaise() / 100.0),
                "------------------------------------------------------------------------",
                "Saved ledger amounts; not a tax certificate or confirmation of tax deposit."
        );

        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 11 Tf\n50 760 Td\n16 TL\n");
        for (int i = 0; i < lines.size(); i++) {
            String escaped = escapePdfText(lines.get(i));
            if (i == 0) {
                stream.append("/F2 13 Tf\n(").append(escaped).append(") Tj\nT*\n/F1 10 Tf\n");
            } else {
                stream.append("(").append(escaped).append(") Tj\nT*\n");
            }
        }
        stream.append("ET\n");
        byte[] contentBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder body = new StringBuilder();
        body.append("%PDF-1.4\n");
        int obj1 = body.length();
        body.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        int obj2 = body.length();
        body.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        int obj3 = body.length();
        body.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>\nendobj\n");
        int obj4 = body.length();
        body.append("4 0 obj\n<< /Length ").append(contentBytes.length).append(" >>\nstream\n");
        out.writeBytes(body.toString().getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(contentBytes);
        String afterStream = "\nendstream\nendobj\n";
        int obj5 = body.length() + contentBytes.length + afterStream.length();
        String obj5Str = "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n";
        int obj6 = obj5 + obj5Str.length();
        String obj6Str = "6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n";
        int xrefOffset = obj6 + obj6Str.length();

        String tail = afterStream + obj5Str + obj6Str +
                "xref\n0 7\n" +
                "0000000000 65535 f \n" +
                String.format("%010d 00000 n \n", obj1) +
                String.format("%010d 00000 n \n", obj2) +
                String.format("%010d 00000 n \n", obj3) +
                String.format("%010d 00000 n \n", obj4) +
                String.format("%010d 00000 n \n", obj5) +
                String.format("%010d 00000 n \n", obj6) +
                "trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
        out.writeBytes(tail.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private String safe(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private String escapePdfText(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
