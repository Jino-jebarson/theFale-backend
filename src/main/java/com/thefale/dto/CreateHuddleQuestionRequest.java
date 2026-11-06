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
public class CreateHuddleQuestionRequest {

    @NotBlank(message = "Field is required (e.g. Nursing, Medicine, Pharmacy)")
    private String field;

    @NotBlank(message = "Question text is required")
    private String questionText;
}
