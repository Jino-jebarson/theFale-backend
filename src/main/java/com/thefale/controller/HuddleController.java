package com.thefale.controller;

import com.thefale.dto.*;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.HuddleService;
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
@RequestMapping("/api/v1/huddle")
@RequiredArgsConstructor
@Tag(name = "Huddle Community", description = "Endpoints for clinical peer discussions, mentor Q&A, and PHI-filtered healthcare knowledge exchange")
public class HuddleController {

    private final HuddleService huddleService;

    @GetMapping("/posts")
    @Operation(summary = "Get live clinical community posts with field filter")
    public ResponseEntity<ApiResponse<List<HuddlePostDto>>> getPosts(
            @RequestParam(required = false, defaultValue = "All") String field) {
        List<HuddlePostDto> posts = huddleService.getPosts(field);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @PostMapping("/posts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new clinical post (guarded by automated PHI/privacy filters)")
    public ResponseEntity<ApiResponse<HuddlePostDto>> createPost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateHuddlePostRequest request) {
        HuddlePostDto post = huddleService.createPost(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Post published to Huddle", post));
    }

    @PutMapping("/posts/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update an existing clinical post")
    public ResponseEntity<ApiResponse<HuddlePostDto>> updatePost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateHuddlePostRequest request) {
        HuddlePostDto post = huddleService.updatePost(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Post updated successfully", post));
    }

    @DeleteMapping("/posts/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete an existing clinical post")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        huddleService.deletePost(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Post deleted successfully"));
    }

    @PostMapping({"/posts/{id}/upvote", "/posts/{id}/react"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upvote or react to a clinical post")
    public ResponseEntity<ApiResponse<HuddlePostDto>> upvotePost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        HuddlePostDto post = huddleService.upvotePost(principal, id);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    @PostMapping("/posts/{id}/pin")
    @Operation(summary = "Toggle pin status on a clinical post")
    public ResponseEntity<ApiResponse<HuddlePostDto>> togglePinPost(
            @PathVariable UUID id) {
        HuddlePostDto post = huddleService.togglePinPost(id);
        return ResponseEntity.ok(ApiResponse.success(post.getIsPinned() ? "Post pinned to community top" : "Post unpinned", post));
    }

    @GetMapping("/posts/{id}/comments")
    @Operation(summary = "Get comments on a clinical post")
    public ResponseEntity<ApiResponse<List<HuddleCommentDto>>> getComments(
            @PathVariable UUID id) {
        List<HuddleCommentDto> comments = huddleService.getComments(id);
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @PostMapping("/posts/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a comment on a clinical post (PHI filtered)")
    public ResponseEntity<ApiResponse<HuddleCommentDto>> addComment(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateHuddleCommentRequest request) {
        HuddleCommentDto comment = huddleService.addComment(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Comment added", comment));
    }

    @PostMapping("/posts/{id}/flag")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Flag a post for moderation / clinical privacy violation")
    public ResponseEntity<ApiResponse<Void>> flagPost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody FlagHuddlePostRequest request) {
        huddleService.flagPost(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Post flagged for moderation review"));
    }

    @GetMapping("/questions")
    @Operation(summary = "Get mentor AMA and career questions")
    public ResponseEntity<ApiResponse<List<HuddleQuestionDto>>> getQuestions() {
        List<HuddleQuestionDto> questions = huddleService.getQuestions();
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @PostMapping("/questions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ask a question to clinical mentors (PHI filtered)")
    public ResponseEntity<ApiResponse<HuddleQuestionDto>> createQuestion(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateHuddleQuestionRequest request) {
        HuddleQuestionDto question = huddleService.createQuestion(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Question submitted to mentors", question));
    }

    @PostMapping("/questions/{id}/answers")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Answer a mentor question (PHI filtered)")
    public ResponseEntity<ApiResponse<HuddleAnswerDto>> answerQuestion(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateHuddleAnswerRequest request) {
        HuddleAnswerDto answer = huddleService.answerQuestion(principal, id, request);
        return ResponseEntity.ok(ApiResponse.success("Answer posted", answer));
    }

    @DeleteMapping("/questions/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete an asked question")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        huddleService.deleteQuestion(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Question deleted successfully"));
    }

    @GetMapping("/mentor/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get candidate mentor status and eligibility")
    public ResponseEntity<ApiResponse<MentorApplicationDto>> getMentorStatus(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        MentorApplicationDto status = huddleService.getCandidateMentorStatus(principal);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/mentor/apply")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Apply for clinical mentor seal (requires 3+ years clinical experience)")
    public ResponseEntity<ApiResponse<MentorApplicationDto>> applyForMentor(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        MentorApplicationDto app = huddleService.applyForMentor(principal);
        return ResponseEntity.ok(ApiResponse.success("Mentor application submitted for verification", app));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Get clinical masterclasses, CMEs, and mentorship workshops")
    public ResponseEntity<ApiResponse<List<HuddleSessionDto>>> getSessions(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        List<HuddleSessionDto> sessions = huddleService.getSessions(principal);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @PostMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Conduct and schedule a clinical mentorship session (Accredited Mentors only)")
    public ResponseEntity<ApiResponse<HuddleSessionDto>> createSession(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateHuddleSessionRequest request) {
        HuddleSessionDto session = huddleService.createSession(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Clinical session scheduled successfully", session));
    }

    @PostMapping("/sessions/{id}/book")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Book a free seat for a mentorship/CME session")
    public ResponseEntity<ApiResponse<HuddleSessionDto>> bookSession(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        HuddleSessionDto session = huddleService.bookSession(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Seat booked successfully", session));
    }

    @PostMapping("/sessions/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel booking for a mentorship session")
    public ResponseEntity<ApiResponse<HuddleSessionDto>> cancelBooking(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        HuddleSessionDto session = huddleService.cancelSessionBooking(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", session));
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel / Delete a mentorship session (Host or Admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {
        huddleService.deleteSession(principal, id);
        return ResponseEntity.ok(ApiResponse.success("Session cancelled and removed"));
    }
}
