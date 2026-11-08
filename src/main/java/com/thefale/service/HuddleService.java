package com.thefale.service;

import com.thefale.dto.*;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ForbiddenException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HuddleService {

    private final HuddlePostRepository huddlePostRepository;
    private final HuddleCommentRepository huddleCommentRepository;
    private final HuddleQuestionRepository huddleQuestionRepository;
    private final HuddleAnswerRepository huddleAnswerRepository;
    private final HuddleFlagRepository huddleFlagRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final UserRepository userRepository;
    private final MentorApplicationRepository mentorApplicationRepository;
    private final HuddleSessionRepository huddleSessionRepository;
    private final HuddleSessionBookingRepository huddleSessionBookingRepository;
    private final NotificationRepository notificationRepository;
    private final PhiProtectionService phiProtectionService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<HuddlePostDto> getPosts(String field) {
        String filterField = (field == null || field.isBlank() || "all".equalsIgnoreCase(field)) ? "All" : field;
        return huddlePostRepository.findLivePostsByField(filterField).stream()
                .map(this::mapPostToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public HuddlePostDto createPost(CustomUserPrincipal principal, CreateHuddlePostRequest request) {
        // Enforce PHI / HIPAA / DISA Privacy Standards
        phiProtectionService.validateNoPhi(request.getTitle());
        phiProtectionService.validateNoPhi(request.getBody());

        boolean isReview = "Case".equalsIgnoreCase(request.getType())
                || (request.getImageUrl() != null && !request.getImageUrl().isBlank());
        boolean isAnon = Boolean.TRUE.equals(request.getIsAnonymous());

        HuddlePost post = HuddlePost.builder()
                .authorId(principal.getId())
                .title(request.getTitle().trim())
                .body(request.getBody().trim())
                .type(request.getType().trim())
                .field(request.getField().trim())
                .imageUrl(request.getImageUrl())
                .audioUrl(request.getAudioUrl())
                .audioDuration(request.getAudioDuration())
                .isAnonymous(isAnon)
                .status(isReview ? "REVIEW" : "LIVE")
                .upvotes(0)
                .flagCount(0)
                .build();

        HuddlePost saved = huddlePostRepository.save(post);
        auditLogService.logEvent(principal.getId(), "HUDDLE_POST_CREATED", "HuddlePost", saved.getId().toString(), null);

        return mapPostToDto(saved);
    }

    @Transactional
    public HuddlePostDto updatePost(CustomUserPrincipal principal, UUID postId, UpdateHuddlePostRequest request) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!post.getAuthorId().equals(principal.getId()) && !isAdmin) {
            throw new ForbiddenException("You can only edit your own posts.");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            phiProtectionService.validateNoPhi(request.getTitle());
            post.setTitle(request.getTitle().trim());
        }
        if (request.getBody() != null && !request.getBody().isBlank()) {
            phiProtectionService.validateNoPhi(request.getBody());
            post.setBody(request.getBody().trim());
        }
        if (request.getType() != null && !request.getType().isBlank()) {
            post.setType(request.getType().trim());
        }
        if (request.getField() != null && !request.getField().isBlank()) {
            post.setField(request.getField().trim());
        }
        if (request.getImageUrl() != null) {
            post.setImageUrl(request.getImageUrl().trim().isEmpty() ? null : request.getImageUrl().trim());
        }
        if (request.getAudioUrl() != null) {
            post.setAudioUrl(request.getAudioUrl().trim().isEmpty() ? null : request.getAudioUrl().trim());
        }
        if (request.getAudioDuration() != null) {
            post.setAudioDuration(request.getAudioDuration());
        }
        if (request.getIsAnonymous() != null) {
            post.setIsAnonymous(request.getIsAnonymous());
        }

        boolean isReview = "Case".equalsIgnoreCase(post.getType())
                || (post.getImageUrl() != null && !post.getImageUrl().isBlank());
        if (!"REMOVED".equalsIgnoreCase(post.getStatus())) {
            post.setStatus(isReview ? "REVIEW" : "LIVE");
        }

        HuddlePost saved = huddlePostRepository.save(post);
        auditLogService.logEvent(principal.getId(), "HUDDLE_POST_UPDATED", "HuddlePost", saved.getId().toString(), null);
        return mapPostToDto(saved);
    }

    @Transactional
    public void deletePost(CustomUserPrincipal principal, UUID postId) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!post.getAuthorId().equals(principal.getId()) && !isAdmin) {
            throw new ForbiddenException("You can only delete your own posts.");
        }

        post.setStatus("REMOVED");
        post.setDeletedAt(Instant.now());
        huddlePostRepository.save(post);
        auditLogService.logEvent(principal.getId(), "HUDDLE_POST_DELETED", "HuddlePost", postId.toString(), null);
    }

    @Transactional
    public void deleteQuestion(CustomUserPrincipal principal, UUID questionId) {
        HuddleQuestion question = huddleQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!question.getAuthorId().equals(principal.getId()) && !isAdmin) {
            throw new ForbiddenException("You can only delete your own questions.");
        }

        huddleQuestionRepository.delete(question);
        auditLogService.logEvent(principal.getId(), "HUDDLE_QUESTION_DELETED", "HuddleQuestion", questionId.toString(), null);
    }

    @Transactional
    public HuddlePostDto upvotePost(CustomUserPrincipal principal, UUID postId) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));

        post.setUpvotes(post.getUpvotes() + 1);
        HuddlePost saved = huddlePostRepository.save(post);

        // Notify post author if liked by someone else
        if (!post.getAuthorId().equals(principal.getId())) {
            String likerName = resolveAuthorName(principal.getId());
            Notification notif = Notification.builder()
                    .userId(post.getAuthorId())
                    .title("New Like on Your Clinical Post")
                    .message(likerName + " liked your clinical post: \"" + post.getTitle() + "\".")
                    .type("HUDDLE")
                    .actionUrl("/huddle")
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        }

        return mapPostToDto(saved);
    }

    @Transactional
    public HuddleCommentDto addComment(CustomUserPrincipal principal, UUID postId, CreateHuddleCommentRequest request) {
        phiProtectionService.validateNoPhi(request.getContent());

        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));

        if ("Question".equalsIgnoreCase(post.getType()) && !isAccreditedMentor(principal)) {
            throw new ForbiddenException("Only accredited clinical mentors can answer peer questions.");
        }

        HuddleComment comment = HuddleComment.builder()
                .postId(postId)
                .authorId(principal.getId())
                .content(request.getContent().trim())
                .build();

        HuddleComment saved = huddleCommentRepository.save(comment);
        auditLogService.logEvent(principal.getId(), "HUDDLE_COMMENT_ADDED", "HuddleComment", saved.getId().toString(), null);

        String authorName = resolveAuthorName(principal.getId());

        // Notify post author if replied by someone else
        if (!post.getAuthorId().equals(principal.getId())) {
            Notification notif = Notification.builder()
                    .userId(post.getAuthorId())
                    .title("New Reply on Your Clinical Discussion")
                    .message(authorName + " replied to your post: \"" + post.getTitle() + "\".")
                    .type("HUDDLE")
                    .actionUrl("/huddle")
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        }

        return HuddleCommentDto.builder()
                .id(saved.getId())
                .postId(saved.getPostId())
                .authorId(saved.getAuthorId())
                .authorName(authorName)
                .content(saved.getContent())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<HuddleCommentDto> getComments(UUID postId) {
        return huddleCommentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> HuddleCommentDto.builder()
                        .id(c.getId())
                        .postId(c.getPostId())
                        .authorId(c.getAuthorId())
                        .authorName(resolveAuthorName(c.getAuthorId()))
                        .content(c.getContent())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void flagPost(CustomUserPrincipal principal, UUID postId, FlagHuddlePostRequest request) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found"));

        if (huddleFlagRepository.existsByPostIdAndReportedBy(postId, principal.getId())) {
            throw new ConflictException("You have already reported this post for moderation");
        }

        HuddleFlag flag = HuddleFlag.builder()
                .postId(postId)
                .reportedBy(principal.getId())
                .reason(request.getReason().trim())
                .resolved(false)
                .build();
        huddleFlagRepository.save(flag);

        post.setFlagCount(post.getFlagCount() + 1);
        post.setStatus("FLAGGED");
        huddlePostRepository.save(post);

        auditLogService.logEvent(principal.getId(), "HUDDLE_POST_FLAGGED", "HuddlePost", postId.toString(), null);
    }

    @Transactional(readOnly = true)
    public List<HuddleQuestionDto> getQuestions() {
        return huddleQuestionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(q -> {
                    List<HuddleAnswerDto> answers = huddleAnswerRepository.findByQuestionIdOrderByCreatedAtAsc(q.getId()).stream()
                            .map(a -> HuddleAnswerDto.builder()
                                    .id(a.getId())
                                    .questionId(a.getQuestionId())
                                    .mentorId(a.getMentorId())
                                    .mentorName(resolveAuthorName(a.getMentorId()))
                                    .mentorRole(resolveAuthorRole(a.getMentorId()))
                                    .mentorSpecialty(resolveMentorSpecialty(a.getMentorId()))
                                    .mentorYrs(resolveMentorYears(a.getMentorId()))
                                    .answerText(a.getAnswerText())
                                    .createdAt(a.getCreatedAt())
                                    .build())
                            .collect(Collectors.toList());

                    return HuddleQuestionDto.builder()
                            .id(q.getId())
                            .authorId(q.getAuthorId())
                            .authorName(resolveAuthorName(q.getAuthorId()))
                            .field(q.getField())
                            .questionText(q.getQuestionText())
                            .answersCount(answers.size())
                            .answers(answers)
                            .createdAt(q.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public HuddleQuestionDto createQuestion(CustomUserPrincipal principal, CreateHuddleQuestionRequest request) {
        phiProtectionService.validateNoPhi(request.getQuestionText());

        HuddleQuestion question = HuddleQuestion.builder()
                .authorId(principal.getId())
                .field(request.getField().trim())
                .questionText(request.getQuestionText().trim())
                .answersCount(0)
                .build();

        HuddleQuestion saved = huddleQuestionRepository.save(question);
        auditLogService.logEvent(principal.getId(), "HUDDLE_QUESTION_CREATED", "HuddleQuestion", saved.getId().toString(), null);

        return HuddleQuestionDto.builder()
                .id(saved.getId())
                .authorId(saved.getAuthorId())
                .authorName(resolveAuthorName(saved.getAuthorId()))
                .field(saved.getField())
                .questionText(saved.getQuestionText())
                .answersCount(0)
                .answers(List.of())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public boolean isAccreditedMentor(CustomUserPrincipal principal) {
        if (principal == null) return false;
        if (principal.getAuthorities() != null && principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        return isAccreditedMentorByUserId(principal.getId());
    }

    public boolean isAccreditedMentorByUserId(UUID userId) {
        if (userId == null) return false;
        CandidateProfile profile = candidateProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null && Boolean.TRUE.equals(profile.getIsMentor())) {
            return true;
        }
        MentorApplication app = profile != null
                ? mentorApplicationRepository.findByCandidateId(profile.getId()).orElse(null)
                : mentorApplicationRepository.findByUserId(userId).orElse(null);
        return app != null && "APPROVED".equalsIgnoreCase(app.getStatus());
    }

    @Transactional
    public HuddleAnswerDto answerQuestion(CustomUserPrincipal principal, UUID questionId, CreateHuddleAnswerRequest request) {
        phiProtectionService.validateNoPhi(request.getAnswerText());

        if (!isAccreditedMentor(principal)) {
            throw new ForbiddenException("Only accredited clinical mentors with verified credentials can answer questions.");
        }

        HuddleQuestion question = huddleQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        HuddleAnswer answer = HuddleAnswer.builder()
                .questionId(questionId)
                .mentorId(principal.getId())
                .answerText(request.getAnswerText().trim())
                .build();

        HuddleAnswer saved = huddleAnswerRepository.save(answer);
        question.setAnswersCount(question.getAnswersCount() + 1);
        huddleQuestionRepository.save(question);

        auditLogService.logEvent(principal.getId(), "HUDDLE_ANSWER_ADDED", "HuddleAnswer", saved.getId().toString(), null);

        // Notify question author when an accredited mentor answers
        if (!question.getAuthorId().equals(principal.getId())) {
            String mentorName = resolveAuthorName(principal.getId());
            Notification notif = Notification.builder()
                    .userId(question.getAuthorId())
                    .title("Mentor Answered Your Question")
                    .message(mentorName + " answered your question: \"" + question.getQuestionText() + "\".")
                    .type("HUDDLE")
                    .actionUrl("/huddle")
                    .read(false)
                    .build();
            notificationRepository.save(notif);
        }

        return HuddleAnswerDto.builder()
                .id(saved.getId())
                .questionId(saved.getQuestionId())
                .mentorId(saved.getMentorId())
                .mentorName(resolveAuthorName(saved.getMentorId()))
                .mentorRole(resolveAuthorRole(saved.getMentorId()))
                .mentorSpecialty(resolveMentorSpecialty(saved.getMentorId()))
                .mentorYrs(resolveMentorYears(saved.getMentorId()))
                .answerText(saved.getAnswerText())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public HuddlePostDto mapPostToDto(HuddlePost p) {
        int comments = huddleCommentRepository.countByPostId(p.getId());
        boolean isAnon = Boolean.TRUE.equals(p.getIsAnonymous());
        String authorName = isAnon ? "Anonymous Clinician" : resolveAuthorName(p.getAuthorId());
        String authorRole = isAnon ? "Verified Clinician" : resolveAuthorRole(p.getAuthorId());
        boolean isMentor = isAccreditedMentorByUserId(p.getAuthorId());
        Integer mentorYrs = isMentor ? resolveMentorYears(p.getAuthorId()) : null;
        boolean isHospital = facilityProfileRepository.findByUserId(p.getAuthorId()).isPresent()
                || "Update".equalsIgnoreCase(p.getType())
                || "Event".equalsIgnoreCase(p.getType());

        return HuddlePostDto.builder()
                .id(p.getId())
                .authorId(p.getAuthorId())
                .authorName(authorName)
                .authorRole(authorRole)
                .title(p.getTitle())
                .body(p.getBody())
                .type(p.getType())
                .field(p.getField())
                .imageUrl(p.getImageUrl())
                .audioUrl(p.getAudioUrl())
                .audioDuration(p.getAudioDuration())
                .isAnonymous(isAnon)
                .status(p.getStatus())
                .upvotes(p.getUpvotes())
                .flagCount(p.getFlagCount())
                .isPinned(p.getIsPinned() != null && p.getIsPinned())
                .commentsCount(comments)
                .createdAt(p.getCreatedAt())
                .isMentor(isMentor)
                .mentorYrs(mentorYrs)
                .isHospital(isHospital)
                .build();
    }

    @Transactional
    public HuddlePostDto togglePinPost(UUID postId) {
        HuddlePost post = huddlePostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Huddle post not found with id: " + postId));
        boolean current = post.getIsPinned() != null && post.getIsPinned();
        post.setIsPinned(!current);
        HuddlePost saved = huddlePostRepository.save(post);
        return mapPostToDto(saved);
    }

    @Transactional(readOnly = true)
    public MentorApplicationDto getCandidateMentorStatus(CustomUserPrincipal principal) {
        CandidateProfile profile = candidateProfileRepository.findByUserId(principal.getId())
                .orElse(null);
        if (profile == null) {
            return MentorApplicationDto.builder()
                    .status("NONE")
                    .verified(false)
                    .yearsExperience(0)
                    .build();
        }

        int years = parseExperienceYears(profile.getExperienceYears());
        MentorApplication app = mentorApplicationRepository.findByCandidateId(profile.getId()).orElse(null);

        boolean isVerified = Boolean.TRUE.equals(profile.getIsMentor()) || (app != null && "APPROVED".equalsIgnoreCase(app.getStatus()));
        String status = isVerified ? "APPROVED" : (app != null ? app.getStatus() : (profile.getMentorStatus() != null ? profile.getMentorStatus() : "NONE"));

        return MentorApplicationDto.builder()
                .id(app != null ? app.getId() : null)
                .candidateId(profile.getId())
                .userId(principal.getId())
                .name(profile.getName())
                .profession(profile.getProfession())
                .specialty(profile.getSpecialty())
                .yearsExperience(years)
                .status(status)
                .verified(isVerified)
                .appliedAt(app != null ? app.getAppliedAt() : null)
                .reviewedAt(app != null ? app.getReviewedAt() : null)
                .adminNotes(app != null ? app.getAdminNotes() : null)
                .build();
    }

    @Transactional
    public MentorApplicationDto applyForMentor(CustomUserPrincipal principal) {
        CandidateProfile profile = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Only registered healthcare practitioners can apply for Mentor certification"));

        int years = parseExperienceYears(profile.getExperienceYears());
        if (years < 3) {
            throw new com.thefale.exception.BadRequestException("Clinical mentor status requires at least 3 years of clinical practice. Your profile currently indicates: " + (profile.getExperienceYears() != null && !profile.getExperienceYears().isBlank() ? profile.getExperienceYears() : "less than 3 years") + ".");
        }

        MentorApplication app = mentorApplicationRepository.findByCandidateId(profile.getId())
                .orElse(MentorApplication.builder()
                        .candidateId(profile.getId())
                        .userId(principal.getId())
                        .build());

        app.setName(profile.getName());
        app.setProfession(profile.getProfession());
        app.setSpecialty(profile.getSpecialty() != null ? profile.getSpecialty() : profile.getProfession());
        app.setYearsExperience(years);
        app.setStatus("PENDING");
        app.setAppliedAt(Instant.now());
        MentorApplication saved = mentorApplicationRepository.save(app);

        profile.setMentorStatus("PENDING");
        candidateProfileRepository.save(profile);

        auditLogService.logEvent(principal.getId(), "MENTOR_APPLICATION_SUBMITTED", "MentorApplication", saved.getId().toString(), null);

        return MentorApplicationDto.builder()
                .id(saved.getId())
                .candidateId(profile.getId())
                .userId(principal.getId())
                .name(profile.getName())
                .profession(profile.getProfession())
                .specialty(profile.getSpecialty())
                .yearsExperience(years)
                .status("PENDING")
                .verified(false)
                .appliedAt(saved.getAppliedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<HuddleSessionDto> getSessions(CustomUserPrincipal principal) {
        UUID currentUserId = principal != null ? principal.getId() : null;
        return huddleSessionRepository.findAllByOrderBySessionWhenAsc().stream()
                .map(s -> mapSessionToDto(s, currentUserId))
                .collect(Collectors.toList());
    }

    @Transactional
    public HuddleSessionDto createSession(CustomUserPrincipal principal, CreateHuddleSessionRequest request) {
        boolean isHospital = facilityProfileRepository.findByUserId(principal.getId()).isPresent();
        if (!isHospital && !isAccreditedMentor(principal)) {
            throw new ForbiddenException("Only accredited clinical mentors or registered hospitals can conduct sessions.");
        }

        // PHI and Content Safety Protection
        phiProtectionService.validateNoPhi(request.getTitle());
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            phiProtectionService.validateNoPhi(request.getDescription());
        }

        String hostName = resolveAuthorName(principal.getId());
        String hostSub = isHospital
                ? "Accredited Healthcare Institution · Clinical CME"
                : "Accredited Clinical Mentor · " + resolveMentorSpecialty(principal.getId()) + " (" + (resolveMentorYears(principal.getId()) > 0 ? resolveMentorYears(principal.getId()) + "+ yrs" : "3+ yrs") + " exp)";

        String forRolesStr = (request.getForRoles() != null && !request.getForRoles().isEmpty())
                ? String.join(",", request.getForRoles())
                : "Nursing,Doctors,Interns";

        HuddleSession session = HuddleSession.builder()
                .title(request.getTitle().trim())
                .category(request.getCategory().trim())
                .hostType(isHospital ? "hospital" : "mentor")
                .hostId(principal.getId())
                .hostName(hostName)
                .hostSub(hostSub)
                .sessionWhen(request.getWhen())
                .sessionWhere(request.getWhere() != null && !request.getWhere().isBlank() ? request.getWhere().trim() : "Live Interactive (Google Meet)")
                .seats(request.getSeats() != null && request.getSeats() > 0 ? request.getSeats() : 30)
                .bookedCount(0)
                .description(request.getDescription() != null ? request.getDescription().trim() : "")
                .forRoles(forRolesStr)
                .build();

        HuddleSession saved = huddleSessionRepository.save(session);
        auditLogService.logEvent(principal.getId(), "HUDDLE_SESSION_CREATED", "HuddleSession", saved.getId().toString(), null);

        return mapSessionToDto(saved, principal.getId());
    }

    @Transactional
    public HuddleSessionDto bookSession(CustomUserPrincipal principal, UUID sessionId) {
        HuddleSession session = huddleSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (huddleSessionBookingRepository.existsBySessionIdAndUserId(sessionId, principal.getId())) {
            throw new BadRequestException("You have already booked a seat for this session.");
        }

        if (session.getBookedCount() >= session.getSeats()) {
            throw new BadRequestException("This session is already full.");
        }

        HuddleSessionBooking booking = HuddleSessionBooking.builder()
                .sessionId(sessionId)
                .userId(principal.getId())
                .build();
        huddleSessionBookingRepository.save(booking);

        session.setBookedCount(session.getBookedCount() + 1);
        HuddleSession updated = huddleSessionRepository.save(session);

        auditLogService.logEvent(principal.getId(), "HUDDLE_SESSION_BOOKED", "HuddleSession", sessionId.toString(), null);

        return mapSessionToDto(updated, principal.getId());
    }

    @Transactional
    public HuddleSessionDto cancelSessionBooking(CustomUserPrincipal principal, UUID sessionId) {
        HuddleSession session = huddleSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (huddleSessionBookingRepository.existsBySessionIdAndUserId(sessionId, principal.getId())) {
            huddleSessionBookingRepository.deleteBySessionIdAndUserId(sessionId, principal.getId());
            if (session.getBookedCount() > 0) {
                session.setBookedCount(session.getBookedCount() - 1);
                session = huddleSessionRepository.save(session);
            }
            auditLogService.logEvent(principal.getId(), "HUDDLE_SESSION_BOOKING_CANCELLED", "HuddleSession", sessionId.toString(), null);
        }

        return mapSessionToDto(session, principal.getId());
    }

    @Transactional
    public void deleteSession(CustomUserPrincipal principal, UUID sessionId) {
        HuddleSession session = huddleSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        boolean isAdmin = principal.getAuthorities() != null && principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!session.getHostId().equals(principal.getId()) && !isAdmin) {
            throw new ForbiddenException("You are not authorized to cancel this session.");
        }

        huddleSessionRepository.delete(session);
        auditLogService.logEvent(principal.getId(), "HUDDLE_SESSION_DELETED", "HuddleSession", sessionId.toString(), null);
    }

    public HuddleSessionDto mapSessionToDto(HuddleSession s, UUID currentUserId) {
        boolean isBooked = currentUserId != null && huddleSessionBookingRepository.existsBySessionIdAndUserId(s.getId(), currentUserId);
        boolean isHost = currentUserId != null && s.getHostId().equals(currentUserId);
        List<String> roles = (s.getForRoles() != null && !s.getForRoles().isBlank())
                ? java.util.Arrays.stream(s.getForRoles().split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .collect(Collectors.toList())
                : List.of("Nursing", "Doctors", "Interns");

        return HuddleSessionDto.builder()
                .id(s.getId())
                .title(s.getTitle())
                .category(s.getCategory())
                .hostType(s.getHostType())
                .hostId(s.getHostId())
                .hostName(s.getHostName())
                .hostSub(s.getHostSub())
                .when(s.getSessionWhen())
                .where(s.getSessionWhere())
                .seats(s.getSeats())
                .bookedCount(s.getBookedCount())
                .isBooked(isBooked)
                .isHost(isHost)
                .description(s.getDescription())
                .forRoles(roles)
                .createdAt(s.getCreatedAt())
                .build();
    }

    public static int parseExperienceYears(String exp) {
        if (exp == null || exp.isBlank()) return 0;
        String clean = exp.trim().toLowerCase();
        if (clean.contains("fresher") || clean.contains("student") || clean.contains("intern")) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(clean);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private String resolveAuthorName(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .map(CandidateProfile::getName)
                .orElseGet(() -> facilityProfileRepository.findByUserId(userId)
                        .map(FacilityProfile::getHospitalName)
                        .orElse("Verified Clinician"));
    }

    private String resolveAuthorRole(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .map(c -> c.getSpecialty() != null ? c.getSpecialty() : c.getProfession())
                .orElseGet(() -> facilityProfileRepository.findByUserId(userId)
                        .map(FacilityProfile::getFacilityType)
                        .orElse("Healthcare Practitioner"));
    }

    private Integer resolveMentorYears(UUID userId) {
        CandidateProfile cp = candidateProfileRepository.findByUserId(userId).orElse(null);
        if (cp != null) {
            int yrs = parseExperienceYears(cp.getExperienceYears());
            if (yrs > 0) return yrs;
        }
        MentorApplication app = mentorApplicationRepository.findByUserId(userId).orElse(null);
        if (app != null && app.getYearsExperience() > 0) {
            return app.getYearsExperience();
        }
        return 3;
    }

    private String resolveMentorSpecialty(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .map(c -> c.getSpecialty() != null && !c.getSpecialty().isBlank() ? c.getSpecialty() : c.getProfession())
                .orElseGet(() -> mentorApplicationRepository.findByUserId(userId)
                        .map(MentorApplication::getSpecialty)
                        .orElse("Clinical Specialist"));
    }
}
