package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHuddlePostRequest {
    private String title;
    private String body;
    private String type;
    private String field;
    private String imageUrl;
    private Boolean isAnonymous;
}

