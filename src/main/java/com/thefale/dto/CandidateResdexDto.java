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
public class CandidateResdexDto {
    private UUID candidateId;

    @JsonProperty("id")
    public UUID getId() {
        return candidateId;
    }

    public void setId(UUID id) {
        this.candidateId = id;
    }

    private String name;
    private String city;
    private String candidateLevel;
    private String profession;
    private String specialty;
    private String qualification;
    private String council;
    private String regNumber;
    private String experienceYears;
    private int matchScore;

    @JsonProperty("isContactUnlocked")
    private boolean contactUnlocked;

    @JsonProperty("isUnlocked")
    public boolean isUnlocked() {
        return contactUnlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.contactUnlocked = unlocked;
    }

    private String phone;
    private String email;

    private String phoneMasked;
    private String emailMasked;
    private String regNumberMasked;
    private String unlockedPhone;
    private String unlockedEmail;
    private String resumeUrl;
    private String resumeFileName;
    private boolean verified;

    @Builder.Default
    private Boolean openToWork = true;
}
