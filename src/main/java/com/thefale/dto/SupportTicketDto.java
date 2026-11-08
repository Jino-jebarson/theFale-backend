package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketDto {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userRole;
    private String subject;
    private String description;
    private String category;
    private String status;
    private String adminNotes;
    private Instant createdAt;
}
