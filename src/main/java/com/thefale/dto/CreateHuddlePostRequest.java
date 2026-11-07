package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHuddlePostRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Body is required")
    private String body;

    @NotBlank(message = "Post type is required (Thought, Experience, Case, Question, Update, Event)")
    private String type;

    @NotBlank(message = "Clinical field is required (Nursing, Physio, Lab, Radiology, Pharmacy, Medicine, All)")
    private String field;

    private String imageUrl;
    private String audioUrl;
    private Integer audioDuration;

    private Boolean isAnonymous;
}
