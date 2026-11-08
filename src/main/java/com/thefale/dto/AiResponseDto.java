package com.thefale.dto;

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
public class AiResponseDto {
    private boolean success;
    private String task;
    private String provider;
    private String response;
    private Map<String, Object> structuredData;
    private List<String> suggestions;
    private String action;
    private Map<String, Object> actionData;
}

