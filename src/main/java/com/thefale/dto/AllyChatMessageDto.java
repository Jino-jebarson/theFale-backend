package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllyChatMessageDto {
    private UUID id;
    private String sender;
    private String text;
    private String action;
    private String actionLabel;
    private List<String> suggestions;
    private String ticketId;
    private Instant createdAt;
}

