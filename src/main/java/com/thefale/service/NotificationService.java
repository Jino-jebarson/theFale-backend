package com.thefale.service;

import com.thefale.dto.NotificationDto;
import com.thefale.entity.Notification;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final HuddlePostRepository huddlePostRepository;
    private final ShiftClaimRepository shiftClaimRepository;

    private static final Map<UUID, Map<String, Long>> ADMIN_ACKNOWLEDGED_COUNTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> ADMIN_DISMISSED_TYPES = new ConcurrentHashMap<>();

    @Transactional
    public List<NotificationDto> getNotifications(UUID userId, String role) {
        if (userId != null && "ROLE_ADMIN".equalsIgnoreCase(role)) {
            syncAdminNotifications(userId);
        }
        return getNotifications(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> NotificationDto.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .message(n.getMessage())
                        .type(n.getType())
                        .actionUrl(n.getActionUrl())
                        .read(n.isRead())
                        .createdAt(n.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void syncAdminNotifications(UUID userId) {
        List<Notification> existing = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Long> ackCounts = ADMIN_ACKNOWLEDGED_COUNTS.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        Set<String> dismissed = ADMIN_DISMISSED_TYPES.getOrDefault(userId, Collections.emptySet());

        // 1. Candidate KYC Queue
        long pendingCandidates = candidateProfileRepository.countByKycStatus("PENDING");
        long prevCandAck = ackCounts.getOrDefault("ADMIN_KYC", 0L);
        boolean candInDb = existing.stream().anyMatch(n -> "ADMIN_KYC".equals(n.getType()));
        if (pendingCandidates > 0 && !candInDb) {
            if (pendingCandidates > prevCandAck && !dismissed.contains("ADMIN_KYC")) {
                notificationRepository.save(Notification.builder()
                        .userId(userId)
                        .title("Doctor & Clinician KYC: " + pendingCandidates + " Pending Review")
                        .message(pendingCandidates + " healthcare professional(s) submitted state medical council registrations and identity credentials awaiting compliance clearance.")
                        .type("ADMIN_KYC")
                        .actionUrl("/admin/candidates")
                        .read(false)
                        .build());
            }
        } else if (pendingCandidates > prevCandAck && candInDb) {
            existing.stream().filter(n -> "ADMIN_KYC".equals(n.getType())).findFirst().ifPresent(n -> {
                n.setTitle("Doctor & Clinician KYC: " + pendingCandidates + " Pending Review");
                n.setMessage(pendingCandidates + " healthcare professional(s) submitted state medical council registrations and identity credentials awaiting compliance clearance.");
                n.setRead(false);
                notificationRepository.save(n);
            });
        }

        // 2. Hospital / Facility Verification Queue
        long pendingFacilities = facilityProfileRepository.countByKycStatus("PENDING");
        long prevFacAck = ackCounts.getOrDefault("ADMIN_FACILITY", 0L);
        boolean facInDb = existing.stream().anyMatch(n -> "ADMIN_FACILITY".equals(n.getType()));
        if (pendingFacilities > 0 && !facInDb) {
            if (pendingFacilities > prevFacAck && !dismissed.contains("ADMIN_FACILITY")) {
                notificationRepository.save(Notification.builder()
                        .userId(userId)
                        .title("Hospital Verification: " + pendingFacilities + " Awaiting Review")
                        .message(pendingFacilities + " healthcare facility / clinical establishment(s) uploaded NABH & registration proof awaiting administrative approval.")
                        .type("ADMIN_FACILITY")
                        .actionUrl("/admin/facilities")
                        .read(false)
                        .build());
            }
        } else if (pendingFacilities > prevFacAck && facInDb) {
            existing.stream().filter(n -> "ADMIN_FACILITY".equals(n.getType())).findFirst().ifPresent(n -> {
                n.setTitle("Hospital Verification: " + pendingFacilities + " Awaiting Review");
                n.setMessage(pendingFacilities + " healthcare facility / clinical establishment(s) uploaded NABH & registration proof awaiting administrative approval.");
                n.setRead(false);
                notificationRepository.save(n);
            });
        }

        // 3. Job & Shift Moderation Queue
        long pendingPostings = jobPostingRepository.countByStatus("PENDING_APPROVAL");
        long prevPostAck = ackCounts.getOrDefault("ADMIN_POSTING", 0L);
        boolean postInDb = existing.stream().anyMatch(n -> "ADMIN_POSTING".equals(n.getType()));
        if (pendingPostings > 0 && !postInDb) {
            if (pendingPostings > prevPostAck && !dismissed.contains("ADMIN_POSTING")) {
                notificationRepository.save(Notification.builder()
                        .userId(userId)
                        .title("Job & Shift Moderation: " + pendingPostings + " Pending Approval")
                        .message(pendingPostings + " clinical job opening(s) / locum shift(s) submitted by hospitals pending rate card and salary compliance check.")
                        .type("ADMIN_POSTING")
                        .actionUrl("/admin/postings")
                        .read(false)
                        .build());
            }
        } else if (pendingPostings > prevPostAck && postInDb) {
            existing.stream().filter(n -> "ADMIN_POSTING".equals(n.getType())).findFirst().ifPresent(n -> {
                n.setTitle("Job & Shift Moderation: " + pendingPostings + " Pending Approval");
                n.setMessage(pendingPostings + " clinical job opening(s) / locum shift(s) submitted by hospitals pending rate card and salary compliance check.");
                n.setRead(false);
                notificationRepository.save(n);
            });
        }

        // 4. Open Support Desk Inquiries
        long openTickets = supportTicketRepository.countByStatus("OPEN");
        long prevTicketAck = ackCounts.getOrDefault("ADMIN_SUPPORT", 0L);
        boolean ticketInDb = existing.stream().anyMatch(n -> "ADMIN_SUPPORT".equals(n.getType()));
        if (openTickets > 0 && !ticketInDb) {
            if (openTickets > prevTicketAck && !dismissed.contains("ADMIN_SUPPORT")) {
                notificationRepository.save(Notification.builder()
                        .userId(userId)
                        .title("Urgent Support Desk: " + openTickets + " Open Inquiries")
                        .message(openTickets + " clinician / hospital support ticket(s) are awaiting admin triage, escrow assistance, or credential dispute handling.")
                        .type("ADMIN_SUPPORT")
                        .actionUrl("/admin/support")
                        .read(false)
                        .build());
            }
        } else if (openTickets > prevTicketAck && ticketInDb) {
            existing.stream().filter(n -> "ADMIN_SUPPORT".equals(n.getType())).findFirst().ifPresent(n -> {
                n.setTitle("Urgent Support Desk: " + openTickets + " Open Inquiries");
                n.setMessage(openTickets + " clinician / hospital support ticket(s) are awaiting admin triage, escrow assistance, or credential dispute handling.");
                n.setRead(false);
                notificationRepository.save(n);
            });
        }

        // 5. Flagged Huddle Posts
        long flaggedPosts = huddlePostRepository.findFlaggedPosts().size();
        long prevHuddleAck = ackCounts.getOrDefault("ADMIN_HUDDLE", 0L);
        boolean huddleInDb = existing.stream().anyMatch(n -> "ADMIN_HUDDLE".equals(n.getType()));
        if (flaggedPosts > 0 && !huddleInDb) {
            if (flaggedPosts > prevHuddleAck && !dismissed.contains("ADMIN_HUDDLE")) {
                notificationRepository.save(Notification.builder()
                        .userId(userId)
                        .title("Huddle Safety: " + flaggedPosts + " Post(s) Flagged")
                        .message(flaggedPosts + " clinical discussion post(s) flagged by peers requiring content moderation or removal.")
                        .type("ADMIN_HUDDLE")
                        .actionUrl("/admin/huddle")
                        .read(false)
                        .build());
            }
        } else if (flaggedPosts > prevHuddleAck && huddleInDb) {
            existing.stream().filter(n -> "ADMIN_HUDDLE".equals(n.getType())).findFirst().ifPresent(n -> {
                n.setTitle("Huddle Safety: " + flaggedPosts + " Post(s) Flagged");
                n.setMessage(flaggedPosts + " clinical discussion post(s) flagged by peers requiring content moderation or removal.");
                n.setRead(false);
                notificationRepository.save(n);
            });
        }

        // 6. Escrow Payout Settlements
        long pendingPayouts = shiftClaimRepository.countByPayoutStatus("PENDING_DISBURSEMENT");
        long prevPayoutAck = ackCounts.getOrDefault("ADMIN_PAYOUT", 0L);
        boolean payoutInDb = existing.stream().anyMatch(n -> "ADMIN_PAYOUT".equals(n.getType()));
        if (pendingPayouts > 0 && !payoutInDb) {
            if (pendingPayouts > prevPayoutAck && !dismissed.contains("ADMIN_PAYOUT")) {
                notificationRepository.save(Notification.builder()
                        .userId(userId)
                        .title("Escrow Settlements: " + pendingPayouts + " Payouts Ready")
                        .message(pendingPayouts + " verified clinical timesheet(s) confirmed by hospitals awaiting escrow batch release.")
                        .type("ADMIN_PAYOUT")
                        .actionUrl("/admin")
                        .read(false)
                        .build());
            }
        } else if (pendingPayouts > prevPayoutAck && payoutInDb) {
            existing.stream().filter(n -> "ADMIN_PAYOUT".equals(n.getType())).findFirst().ifPresent(n -> {
                n.setTitle("Escrow Settlements: " + pendingPayouts + " Payouts Ready");
                n.setMessage(pendingPayouts + " verified clinical timesheet(s) confirmed by hospitals awaiting escrow batch release.");
                n.setRead(false);
                notificationRepository.save(n);
            });
        }
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (notification.getUserId().equals(userId)) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId);
    }

    @Transactional
    public void deleteNotification(UUID userId, UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                ADMIN_DISMISSED_TYPES.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(n.getType());
                recordAdminCountForType(userId, n.getType());
            }
        });
        notificationRepository.deleteByIdAndUserId(notificationId, userId);
    }

    @Transactional
    public void clearAll(UUID userId) {
        recordCurrentAdminCountsAsAcknowledged(userId);
        notificationRepository.deleteAllByUserId(userId);
    }

    private void recordCurrentAdminCountsAsAcknowledged(UUID userId) {
        Map<String, Long> map = ADMIN_ACKNOWLEDGED_COUNTS.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        map.put("ADMIN_KYC", candidateProfileRepository.countByKycStatus("PENDING"));
        map.put("ADMIN_FACILITY", facilityProfileRepository.countByKycStatus("PENDING"));
        map.put("ADMIN_POSTING", jobPostingRepository.countByStatus("PENDING_APPROVAL"));
        map.put("ADMIN_SUPPORT", supportTicketRepository.countByStatus("OPEN"));
        map.put("ADMIN_HUDDLE", (long) huddlePostRepository.findFlaggedPosts().size());
        map.put("ADMIN_PAYOUT", shiftClaimRepository.countByPayoutStatus("PENDING_DISBURSEMENT"));
    }

    private void recordAdminCountForType(UUID userId, String type) {
        Map<String, Long> map = ADMIN_ACKNOWLEDGED_COUNTS.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        if ("ADMIN_KYC".equals(type)) map.put(type, candidateProfileRepository.countByKycStatus("PENDING"));
        else if ("ADMIN_FACILITY".equals(type)) map.put(type, facilityProfileRepository.countByKycStatus("PENDING"));
        else if ("ADMIN_POSTING".equals(type)) map.put(type, jobPostingRepository.countByStatus("PENDING_APPROVAL"));
        else if ("ADMIN_SUPPORT".equals(type)) map.put(type, supportTicketRepository.countByStatus("OPEN"));
        else if ("ADMIN_HUDDLE".equals(type)) map.put(type, (long) huddlePostRepository.findFlaggedPosts().size());
        else if ("ADMIN_PAYOUT".equals(type)) map.put(type, shiftClaimRepository.countByPayoutStatus("PENDING_DISBURSEMENT"));
    }
}
