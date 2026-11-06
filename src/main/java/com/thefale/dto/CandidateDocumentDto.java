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
public class CandidateDocumentDto {
    private UUID id;
    private String documentType;
    private String originalFileName;
    private String fileUrl;
    private Long fileSizeBytes;
    private String mimeType;
    private boolean verified;
    private Instant createdAt;
}
