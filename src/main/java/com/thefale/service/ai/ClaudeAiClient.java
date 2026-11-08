package com.thefale.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ClaudeAiClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiClient.class);
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ClaudeAiClient(
            @Value("${fale.ai.claude.api-key:${CLAUDE_API_KEY:${ANTHROPIC_API_KEY:}}}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("placeholder");
    }

    @Override
    public String generateContent(String systemPrompt, String userPrompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Claude API key is not configured.");
        }

        String url = "https://api.anthropic.com/v1/messages";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "claude-3-5-haiku-20241022");
        requestBody.put("max_tokens", 1024);
        
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBody.put("system", systemPrompt);
        }

        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)
        ));

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText("");
            }
            return "";
        } else {
            log.error("Claude API error ({}): {}", response.statusCode(), response.body());
            throw new RuntimeException("Claude API request failed with status: " + response.statusCode());
        }
    }
}

