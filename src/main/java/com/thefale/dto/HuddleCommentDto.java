package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
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
public class HuddleCommentDto {
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private String authorName;
    private String content;
    private Instant createdAt;
}
