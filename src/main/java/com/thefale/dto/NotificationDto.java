package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class NotificationDto {

    private UUID id;
    private String title;
    private String message;
    private String type;
    private String actionUrl;

    @JsonProperty("isRead")
    private boolean read;

    private Instant createdAt;
}
