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
public class HuddleSessionDto {

    private UUID id;
    private String title;
    private String category;
    private String hostType;
    private UUID hostId;
    private String hostName;
    private String hostSub;
    private Instant when;
    private String where;
    private int seats;
    private int bookedCount;
    private boolean isBooked;
    private boolean isHost;
    private String description;
    private List<String> forRoles;
    private Instant createdAt;
}

