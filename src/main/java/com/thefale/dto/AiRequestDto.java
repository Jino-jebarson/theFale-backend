package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRequestDto {
    private String task;
    private String prompt;
    private String context;
    private String language;
    private String role;
    private Map<String, Object> metadata;
    private List<Map<String, String>> history;
}

