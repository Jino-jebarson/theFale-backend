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
public class HuddlePostDto {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String authorRole;
    private String title;
    private String body;
    private String type;
    private String field;
    private String status;
    private String imageUrl;
    private Boolean isAnonymous;
    private int upvotes;
    private int flagCount;
    private int commentsCount;
    private Boolean isPinned;
    private Boolean isMentor;
    private Integer mentorYrs;
    private Boolean isHospital;
    private Instant createdAt;
}
