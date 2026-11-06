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
public class HuddleAnswerDto {
    private UUID id;
    private UUID questionId;
    private UUID mentorId;
    private String mentorName;
    private String mentorRole;
    private String mentorSpecialty;
    private Integer mentorYrs;
    private String answerText;
    private Instant createdAt;
}
