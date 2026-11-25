package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PreferredLanguageUpdateRequest {
    @NotBlank(message = "Choose a language")
    @Pattern(
            regexp = "^(en|hi|ta|te|ml|kn|bn|mr|gu|pa|or|as|ur)$",
            message = "This language is not supported"
    )
    private String preferredLanguage;
}
