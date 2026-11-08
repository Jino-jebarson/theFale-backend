package com.thefale.service;

import com.thefale.dto.CreateSupportTicketRequest;
import com.thefale.dto.SupportTicketDto;
import com.thefale.dto.UpdateSupportTicketStatusRequest;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.Notification;
import com.thefale.entity.SupportTicket;
import com.thefale.entity.User;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.NotificationRepository;
import com.thefale.repository.SupportTicketRepository;
import com.thefale.repository.UserRepository;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    private SupportTicketDto mapToDto(SupportTicket t) {
        String userName = "User";
        String userEmail = null;
        String userRole = "User";

        User user = userRepository.findById(t.getUserId()).orElse(null);
        if (user != null) {
            userEmail = user.getEmail();
            userRole = user.getRole() != null ? user.getRole().replace("ROLE_", "") : "USER";
        }

        CandidateProfile candidate = candidateProfileRepository.findByUserId(t.getUserId()).orElse(null);
        if (candidate != null && candidate.getName() != null) {
            userName = candidate.getName();
            userRole = "Clinician (" + (candidate.getProfession() != null ? candidate.getProfession() : "Candidate") + ")";
        } else {
            FacilityProfile facility = facilityProfileRepository.findByUserId(t.getUserId()).orElse(null);
            if (facility != null && facility.getHospitalName() != null) {
                userName = facility.getHospitalName();
                userRole = "Hospital (" + (facility.getFacilityType() != null ? facility.getFacilityType() : "Facility") + ")";
            }
        }

        return SupportTicketDto.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .userName(userName)
                .userEmail(userEmail)
                .userRole(userRole)
                .subject(t.getSubject())
                .description(t.getDescription())
                .category(t.getCategory())
                .status(t.getStatus())
                .adminNotes(t.getAdminNotes())
                .createdAt(t.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getMyTickets(UUID userId) {
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getAllTickets(String statusFilter) {
        return supportTicketRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase("ALL") || t.getStatus().equalsIgnoreCase(statusFilter))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketDto createTicket(UUID userId, CreateSupportTicketRequest request) {
        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .subject(request.getSubject().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory().trim())
                .status("OPEN")
                .build();

        SupportTicket saved = supportTicketRepository.save(ticket);
        auditLogService.logEvent(userId, "SUPPORT_TICKET_CREATED", "SupportTicket", saved.getId().toString(), null);

        return mapToDto(saved);
    }

    @Transactional
    public SupportTicketDto updateTicketStatus(CustomUserPrincipal principal, UUID ticketId, UpdateSupportTicketStatusRequest request) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found"));

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            ticket.setStatus(request.getStatus().toUpperCase());
        }
        if (request.getAdminNotes() != null) {
            ticket.setAdminNotes(request.getAdminNotes().trim());
        }

        SupportTicket saved = supportTicketRepository.save(ticket);
        auditLogService.logEvent(principal.getId(), "SUPPORT_TICKET_UPDATED", "SupportTicket", saved.getId().toString(), java.util.Map.of("status", saved.getStatus()));

        // Notify user about support ticket update
        Notification notif = Notification.builder()
                .userId(ticket.getUserId())
                .title("Support Inquiry " + ticket.getStatus())
                .message(ticket.getAdminNotes() != null && !ticket.getAdminNotes().isBlank()
                        ? "FALE Support: " + ticket.getAdminNotes()
                        : "Your inquiry regarding \"" + ticket.getSubject() + "\" has been marked as " + ticket.getStatus() + ".")
                .type("SUPPORT")
                .actionUrl("/support")
                .read(false)
                .build();
        notificationRepository.save(notif);

        return mapToDto(saved);
    }
}
