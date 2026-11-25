package com.thefale.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class FacilityTeamInviteDto {
    UUID id;
    String name;
    String phone;
    String status;
    Instant createdAt;
}
