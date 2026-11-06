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
public class HuddleQuestionDto {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String field;
    private String questionText;
    private int answersCount;
    private List<HuddleAnswerDto> answers;
    private Instant createdAt;
}
