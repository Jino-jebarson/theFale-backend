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
public class GeminiAiClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);
    private static final List<String> CANDIDATE_MODELS = List.of(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-3.1-flash-lite"
    );

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile String activeModel = "gemini-2.5-flash";

    public GeminiAiClient(
            @Value("${fale.ai.gemini.api-key:${GEMINI_API_KEY:${AI_API_KEY:}}}") String apiKey,
            ObjectMapper objectMapper
    ) {
        String key = apiKey != null ? apiKey.trim() : "";
        if (key.isBlank() || key.equalsIgnoreCase("placeholder")) {
            key = resolveKeyFromDotEnv();
        }
        this.apiKey = key;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        if (isConfigured()) {
            log.info("Gemini AI Client successfully configured with live API key.");
        } else {
            log.warn("Gemini AI Client has no API key configured. Fallback engine will be active.");
        }
    }

    private static String resolveKeyFromDotEnv() {
        try {
            java.nio.file.Path[] searchPaths = new java.nio.file.Path[] {
                java.nio.file.Paths.get(".env"),
                java.nio.file.Paths.get("../.env"),
                java.nio.file.Paths.get("f:/theFaleOne-Backend/.env")
            };
            for (java.nio.file.Path p : searchPaths) {
                if (java.nio.file.Files.exists(p)) {
                    List<String> lines = java.nio.file.Files.readAllLines(p);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("GEMINI_API_KEY=")) {
                            String val = trimmed.substring("GEMINI_API_KEY=".length()).trim();
                            if (!val.isBlank()) return val;
                        } else if (trimmed.startsWith("AI_API_KEY=")) {
                            String val = trimmed.substring("AI_API_KEY=".length()).trim();
                            if (!val.isBlank()) return val;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve Gemini API key from .env: {}", e.getMessage());
        }
        return "";
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("placeholder");
    }

    @Override
    public String generateContent(String systemPrompt, String userPrompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        Map<String, Object> requestBody = new HashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            requestBody.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }
        requestBody.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", userPrompt)))
        ));
        requestBody.put("generationConfig", Map.of(
                "maxOutputTokens", 1024,
                "temperature", 0.7
        ));

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        Exception lastException = null;
        for (String model : CANDIDATE_MODELS) {
            try {
                String result = callModel(model, jsonPayload);
                if (result != null && !result.isBlank()) {
                    this.activeModel = model;
                    return result;
                }
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Gemini model {} failed (e.g. 503 spike), trying next candidate: {}", model, ex.getMessage());
            }
        }
        throw lastException != null ? lastException : new RuntimeException("All Gemini candidate models failed.");
    }

    private String callModel(String model, String jsonPayload) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
            return "";
        } else {
            throw new RuntimeException("Gemini HTTP " + response.statusCode() + " on " + model + ": " + response.body());
        }
    }
}

