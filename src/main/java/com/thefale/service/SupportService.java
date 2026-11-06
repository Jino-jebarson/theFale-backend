package com.thefale.service;

import com.thefale.dto.CreateSupportTicketRequest;
import com.thefale.dto.SupportTicketDto;
import com.thefale.entity.SupportTicket;
import com.thefale.repository.SupportTicketRepository;
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
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getMyTickets(UUID userId) {
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(t -> SupportTicketDto.builder()
                        .id(t.getId())
                        .subject(t.getSubject())
                        .description(t.getDescription())
                        .category(t.getCategory())
                        .status(t.getStatus())
                        .createdAt(t.getCreatedAt())
                        .build())
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

        return SupportTicketDto.builder()
                .id(saved.getId())
                .subject(saved.getSubject())
                .description(saved.getDescription())
                .category(saved.getCategory())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}
