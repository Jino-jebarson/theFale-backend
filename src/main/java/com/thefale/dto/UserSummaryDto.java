package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private UUID id;
    private String phone;
    private String email;
    private String username;
    private String role;
    private String candidateLevel;
    @JsonProperty("onboarded")
    private boolean isOnboarded;
    private String status;

    @JsonProperty("onboarded")
    public boolean isOnboarded() {
        return isOnboarded;
    }
}
