package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSupportTicketStatusRequest {
    private String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED
    private String adminNotes;
}

