package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateCertificationDto {
    private String id;
    @NotBlank
    @Size(max = 120)
    private String name;
    @NotBlank
    @Size(max = 150)
    private String issuer;
    @Size(max = 100)
    private String credentialId;
    @Size(max = 10)
    private String issueDate;
    @Size(max = 10)
    private String expiryDate;
    @NotBlank
    private String documentId;
}
