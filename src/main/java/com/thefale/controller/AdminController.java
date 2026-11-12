package com.thefale.controller;

import com.thefale.dto.*;
import com.thefale.entity.AuditEvent;
import com.thefale.entity.HuddlePost;
import com.thefale.entity.StudentUpgradeRequest;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.AdminService;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "Endpoints for compliance moderation, KYC approval queues, student graduation upgrades, shift payouts, and audit logs")
public class AdminController {

    private final AdminService adminService;
    private final com.thefale.service.SupportService supportService;

    @GetMapping({"/dashboard", "/metrics"})
    @Operation(summary = "Get admin dashboard KPI metrics")
    public ResponseEntity<ApiResponse<AdminDashboardMetricsDto>> getMetrics() {
        AdminDashboardMetricsDto metrics = adminService.getDashboardMetrics();
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    @GetMapping({"/kyc/candidates", "/candidates/pending"})
    @Operation(summary = "Get candidate KYC approval queue")
    public ResponseEntity<ApiResponse<List<CandidateProfileDto>>> getPendingCandidates(
            @RequestParam(required = false) String status) {
        List<CandidateProfileDto> candidates = adminService.getCandidates(status);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PostMapping({"/kyc/candidates/{id}/verify", "/candidates/{id}/review"})
    @Operation(summary = "Approve candidate KYC credentials")
    public ResponseEntity<ApiResponse<CandidateProfileDto>> reviewCandidateKyc(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("APPROVE")
                    .notes("Approved via KYC workflow")
                    .build();
        }
        CandidateProfileDto candidate = adminService.reviewCandidateKyc(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Candidate KYC decision recorded", candidate));
    }

    @PostMapping("/kyc/candidates/{id}/reject")
    @Operation(summary = "Reject candidate KYC credentials")
    public ResponseEntity<ApiResponse<CandidateProfileDto>> rejectCandidateKyc(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("REJECT")
                    .notes("Rejected via KYC review")
                    .build();
        }
        CandidateProfileDto candidate = adminService.reviewCandidateKyc(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Candidate KYC rejected", candidate));
    }

    @GetMapping({"/candidates/upgrade-requests", "/candidates/upgrades"})
    @Operation(summary = "Get student graduation upgrade requests queue")
    public ResponseEntity<ApiResponse<List<StudentUpgradeRequest>>> getPendingUpgrades() {
        List<StudentUpgradeRequest> upgrades = adminService.getPendingUpgrades();
        return ResponseEntity.ok(ApiResponse.success(upgrades));
    }

    @PostMapping({"/candidates/upgrade-requests/{id}/approve", "/candidates/upgrades/{id}/review"})
    @Operation(summary = "Approve student graduation upgrade")
    public ResponseEntity<ApiResponse<Void>> reviewStudentUpgrade(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("APPROVE")
                    .notes("Degree and registration verified")
                    .build();
        }
        adminService.reviewStudentUpgrade(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Student upgrade decision recorded"));
    }

    @PostMapping("/candidates/upgrade-requests/{id}/reject")
    @Operation(summary = "Reject student graduation upgrade")
    public ResponseEntity<ApiResponse<Void>> rejectStudentUpgrade(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("REJECT")
                    .notes("Upgrade request rejected")
                    .build();
        }
        adminService.reviewStudentUpgrade(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Student upgrade rejection recorded"));
    }

    @GetMapping({"/kyc/facilities", "/facilities/pending"})
    @Operation(summary = "Get facility establishment KYC approval queue")
    public ResponseEntity<ApiResponse<List<FacilityProfileDto>>> getPendingFacilities(
            @RequestParam(required = false) String status) {
        List<FacilityProfileDto> facilities = adminService.getFacilities(status);
        return ResponseEntity.ok(ApiResponse.success(facilities));
    }

    @PostMapping({"/kyc/facilities/{id}/approve", "/facilities/{id}/review"})
    @Operation(summary = "Approve hospital facility KYC")
    public ResponseEntity<ApiResponse<FacilityProfileDto>> reviewFacilityKyc(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("APPROVE")
                    .notes("Hospital license verified")
                    .build();
        }
        FacilityProfileDto facility = adminService.reviewFacilityKyc(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Facility KYC decision recorded", facility));
    }

    @PostMapping("/kyc/facilities/{id}/reject")
    @Operation(summary = "Reject hospital facility KYC")
    public ResponseEntity<ApiResponse<FacilityProfileDto>> rejectFacilityKyc(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("REJECT")
                    .notes("Establishment license invalid or missing")
                    .build();
        }
        FacilityProfileDto facility = adminService.reviewFacilityKyc(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Facility KYC rejection recorded", facility));
    }

    @GetMapping({"/postings", "/postings/pending"})
    @Operation(summary = "Get job postings pending compliance moderation")
    public ResponseEntity<ApiResponse<List<JobPostingDto>>> getPendingPostings(
            @RequestParam(required = false) String status) {
        List<JobPostingDto> postings = adminService.getPostings(status);
        return ResponseEntity.ok(ApiResponse.success(postings));
    }

    @PostMapping({"/postings/{id}/approve", "/postings/{id}/review"})
    @Operation(summary = "Approve clinical job posting")
    public ResponseEntity<ApiResponse<Void>> reviewPosting(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("APPROVE")
                    .notes("Job posting approved")
                    .build();
        }
        adminService.reviewPosting(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Job posting approved"));
    }

    @PostMapping("/postings/{id}/reject")
    @Operation(summary = "Reject clinical job posting")
    public ResponseEntity<ApiResponse<Void>> rejectPosting(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("REJECT")
                    .notes("Violates posting guidelines")
                    .build();
        }
        adminService.reviewPosting(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Job posting rejected"));
    }

    @GetMapping("/payouts/pending")
    @Operation(summary = "Get shift claims completed and pending escrow disbursement")
    public ResponseEntity<ApiResponse<List<ShiftClaimDto>>> getPendingPayouts() {
        List<ShiftClaimDto> payouts = adminService.getPendingPayouts();
        return ResponseEntity.ok(ApiResponse.success(payouts));
    }

    @PostMapping({"/payouts/{id}/mark-paid", "/payouts/{id}/disburse"})
    @Operation(summary = "Record UTR banking disbursement for completed shift payout")
    public ResponseEntity<ApiResponse<Void>> disbursePayout(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdminPayoutApprovalRequest request) {
        adminService.disbursePayout(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Shift payout marked disbursed with UTR"));
    }

    @GetMapping({"/huddle/flagged", "/huddle/flags"})
    @Operation(summary = "Get community posts flagged for review")
    public ResponseEntity<ApiResponse<List<HuddlePostDto>>> getFlaggedPosts() {
        List<HuddlePostDto> posts = adminService.getFlaggedPosts();
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/huddle/cases")
    @Operation(summary = "Get clinical case study and image posts pending privacy review")
    public ResponseEntity<ApiResponse<List<HuddlePostDto>>> getReviewCases() {
        List<HuddlePostDto> cases = adminService.getReviewCases();
        return ResponseEntity.ok(ApiResponse.success(cases));
    }

    @PostMapping("/huddle/cases/{id}/approve")
    @Operation(summary = "Approve clinical review post to go live")
    public ResponseEntity<ApiResponse<HuddlePostDto>> approveReviewCase(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        HuddlePostDto dto = adminService.approveReviewCase(principal, id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/huddle/cases/{id}/reject")
    @Operation(summary = "Reject clinical review post")
    public ResponseEntity<ApiResponse<Void>> rejectReviewCase(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        adminService.rejectReviewCase(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Review post rejected and removed"));
    }

    @DeleteMapping("/huddle/posts/{id}")
    @Operation(summary = "Delete flagged community post")
    public ResponseEntity<ApiResponse<Void>> deleteFlaggedPost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        adminService.resolvePostFlag(principal, id, true);
        return ResponseEntity.ok(ApiResponse.success("Flagged post removed"));
    }

    @PostMapping("/huddle/posts/{id}/moderate")
    @Operation(summary = "Resolve post flag (keep live or remove)")
    public ResponseEntity<ApiResponse<Void>> moderatePost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam boolean remove) {
        adminService.resolvePostFlag(principal, id, remove);
        return ResponseEntity.ok(ApiResponse.success("Flag moderation decision recorded"));
    }

    @PostMapping("/huddle/posts/{id}/pin")
    @Operation(summary = "Toggle pin status for huddle post")
    public ResponseEntity<ApiResponse<HuddlePostDto>> togglePinHuddlePost(@PathVariable UUID id) {
        HuddlePostDto post = adminService.togglePinHuddlePost(id);
        return ResponseEntity.ok(ApiResponse.success(post.getIsPinned() ? "Post pinned to community top" : "Post unpinned", post));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get append-only compliance audit event history")
    public ResponseEntity<ApiResponse<List<AuditEvent>>> getAuditLogs() {
        List<AuditEvent> logs = adminService.getAuditLogs();
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/applications")
    @Operation(summary = "Get applications for compliance review and shortlisting")
    public ResponseEntity<ApiResponse<List<JobApplicationDto>>> getAdminApplications(
            @RequestParam(required = false) String status) {
        List<JobApplicationDto> applications = adminService.getAdminApplications(status);
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    @RequestMapping(value = {"/applications/{id}/screening", "/applications/{id}/screen"}, method = {RequestMethod.PATCH, RequestMethod.POST})
    @Operation(summary = "Screen application: SHORTLIST or REJECT with reason")
    public ResponseEntity<ApiResponse<Void>> screenApplication(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdminApplicationScreeningRequest request) {
        adminService.screenApplication(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Application screening recorded successfully"));
    }

    @GetMapping({"/shifts", "/shifts/pending"})
    @Operation(summary = "Get locum shifts for compliance review and published tracking")
    public ResponseEntity<ApiResponse<List<LocumShiftDto>>> getShifts(
            @RequestParam(required = false) String status) {
        List<LocumShiftDto> shifts = adminService.getShifts(status);
        return ResponseEntity.ok(ApiResponse.success(shifts));
    }

    @PostMapping({"/shifts/{id}/approve", "/shifts/{id}/review"})
    @Operation(summary = "Approve locum shift and release to candidates")
    public ResponseEntity<ApiResponse<Void>> approveShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("APPROVE")
                    .notes("Shift approved by admin")
                    .build();
        }
        adminService.reviewShift(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Locum shift approved and published"));
    }

    @PostMapping("/shifts/{id}/reject")
    @Operation(summary = "Reject locum shift and refund escrow to facility")
    public ResponseEntity<ApiResponse<Void>> rejectShift(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminKycReviewRequest request) {
        if (request == null) {
            request = AdminKycReviewRequest.builder()
                    .action("REJECT")
                    .notes("Shift does not meet compliance requirements")
                    .build();
        }
        adminService.reviewShift(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Locum shift rejected and escrow refunded"));
    }

    @GetMapping("/shifts/claims/pending")
    @Operation(summary = "Get shift claims awaiting admin screening")
    public ResponseEntity<ApiResponse<List<ShiftClaimDto>>> getPendingShiftClaims() {
        List<ShiftClaimDto> claims = adminService.getPendingShiftClaims();
        return ResponseEntity.ok(ApiResponse.success(claims));
    }

    @GetMapping("/shifts/claims/disputed")
    @Operation(summary = "Get disputed shift claims requiring admin mediation")
    public ResponseEntity<ApiResponse<List<ShiftClaimDto>>> getDisputedShiftClaims() {
        List<ShiftClaimDto> claims = adminService.getDisputedShiftClaims();
        return ResponseEntity.ok(ApiResponse.success(claims));
    }

    @PostMapping("/shifts/claims/{id}/resolve-dispute")
    @Operation(summary = "Resolve a disputed shift claim (release, partial split, or refund hospital)")
    public ResponseEntity<ApiResponse<Void>> resolveShiftDispute(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request) {
        adminService.resolveShiftDispute(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Shift dispute resolved cleanly"));
    }

    @RequestMapping(value = {"/shifts/claims/{id}/screening", "/shifts/claims/{id}/screen"}, method = {RequestMethod.PATCH, RequestMethod.POST})
    @Operation(summary = "Screen shift claim: APPROVE or REJECT")
    public ResponseEntity<ApiResponse<Void>> screenShiftClaim(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AdminApplicationScreeningRequest request) {
        adminService.screenShiftClaim(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Shift claim decision recorded"));
    }

    @GetMapping("/huddle/mentors")
    @Operation(summary = "Get clinical mentor applications queue")
    public ResponseEntity<ApiResponse<List<MentorApplicationDto>>> getMentorApplications() {
        List<MentorApplicationDto> apps = adminService.getMentorApplications();
        return ResponseEntity.ok(ApiResponse.success(apps));
    }

    @PostMapping("/huddle/mentors/{id}/review")
    @Operation(summary = "Review clinical mentor application: approve/verify or reject")
    public ResponseEntity<ApiResponse<MentorApplicationDto>> reviewMentorApplication(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean approve,
            @RequestParam(required = false) String notes) {
        MentorApplicationDto app = adminService.reviewMentorApplication(principal, id, approve, notes);
        return ResponseEntity.ok(ApiResponse.success(approve ? "Mentor seal verified & granted" : "Mentor application rejected", app));
    }

    @PostMapping({"/huddle/mentors/{id}/toggle", "/huddle/mentors/{id}/seal"})
    @Operation(summary = "Toggle clinical mentor verification seal")
    public ResponseEntity<ApiResponse<MentorApplicationDto>> toggleMentorSeal(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        MentorApplicationDto app = adminService.toggleMentorSeal(principal, id);
        return ResponseEntity.ok(ApiResponse.success(app.isVerified() ? "Mentor seal verified & active" : "Mentor seal suspended", app));
    }

    @PostMapping("/huddle/mentors")
    @Operation(summary = "Directly onboard and certify a new clinical mentor")
    public ResponseEntity<ApiResponse<MentorApplicationDto>> onboardMentor(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody MentorApplicationDto request) {
        MentorApplicationDto app = adminService.onboardMentor(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Clinical mentor certified and added to directory", app));
    }

    @GetMapping("/support/tickets")
    @Operation(summary = "Get all support tickets for admin help desk")
    public ResponseEntity<ApiResponse<List<SupportTicketDto>>> getAllSupportTickets(
            @RequestParam(required = false) String status) {
        List<SupportTicketDto> tickets = supportService.getAllTickets(status);
        return ResponseEntity.ok(ApiResponse.success("Support tickets fetched successfully", tickets));
    }

    @PutMapping("/support/tickets/{id}/status")
    @Operation(summary = "Update support ticket status and admin resolution notes")
    public ResponseEntity<ApiResponse<SupportTicketDto>> updateSupportTicketStatus(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateSupportTicketStatusRequest request) {
        SupportTicketDto ticket = supportService.updateTicketStatus(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Support ticket updated successfully", ticket));
    }
}
