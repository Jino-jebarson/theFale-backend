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
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class GeminiAiClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);
    private static final List<String> CANDIDATE_MODELS = List.of(
            "gemini-flash-latest",
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest"
    );

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiAiClient(
            @Value("${fale.ai.gemini.api-key:${GEMINI_API_KEY:${AI_API_KEY:}}}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.apiKey = AiKeyResolver.resolveKey(apiKey, "GEMINI_API_KEY", "AI_API_KEY");
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        if (isConfigured()) {
            log.info("Gemini AI Client configured with live API key.");
        } else {
            log.warn("Gemini AI Client has no API key configured.");
        }
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return AiKeyResolver.isValidKey(apiKey);
    }

    @Override
    public String generateContent(String systemPrompt, String userPrompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        String jsonPayload = buildJsonPayload(systemPrompt, userPrompt);

        Exception lastException = null;
        for (String model : CANDIDATE_MODELS) {
            try {
                String result = callModel(model, jsonPayload);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            } catch (NonRetryableGeminiException ex) {
                throw ex;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Gemini model {} failed, trying next candidate: {}", model, ex.getMessage());
            }
        }
        throw lastException != null ? lastException : new RuntimeException("All Gemini candidate models failed.");
    }

    public String streamContent(String systemPrompt, String userPrompt, Consumer<String> onChunk) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        String jsonPayload = buildJsonPayload(systemPrompt, userPrompt);
        Exception lastException = null;
        for (String model : CANDIDATE_MODELS) {
            try {
                String fullText = streamModel(model, jsonPayload, onChunk);
                if (fullText != null && !fullText.isBlank()) {
                    return fullText;
                }
            } catch (NonRetryableGeminiException ex) {
                throw ex;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Gemini stream model {} failed, trying next candidate: {}", model, ex.getMessage());
            }
        }
        throw lastException != null ? lastException : new RuntimeException("All Gemini streaming models failed.");
    }

    private String buildJsonPayload(String systemPrompt, String userPrompt) throws Exception {
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
                "temperature", 0.4
        ));
        return objectMapper.writeValueAsString(requestBody);
    }

    private String extractCandidateText(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText("");
            }
        }
        return "";
    }

    private String callModel(String model, String jsonPayload) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return extractCandidateText(response.body());
        }

        String msg = "Gemini HTTP " + response.statusCode() + " on " + model + ": " + response.body();
        if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
            throw new NonRetryableGeminiException(msg);
        }
        throw new RuntimeException(msg);
    }

    private String streamModel(String model, String jsonPayload, Consumer<String> onChunk) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errBody;
            try (Stream<String> lines = response.body()) {
                errBody = lines.limit(20).reduce("", (a, b) -> a + b);
            }
            String msg = "Gemini stream HTTP " + response.statusCode() + " on " + model + ": " + errBody;
            if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
                throw new NonRetryableGeminiException(msg);
            }
            throw new RuntimeException(msg);
        }

        StringBuilder full = new StringBuilder();
        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (line == null || !line.startsWith("data:")) return;
                String json = line.substring(5).trim();
                if (json.isEmpty() || "[DONE]".equals(json)) return;
                try {
                    String piece = extractCandidateText(json);
                    if (piece != null && !piece.isEmpty()) {
                        full.append(piece);
                        if (onChunk != null) {
                            onChunk.accept(piece);
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore partial or heartbeat lines
                }
            });
        }
        return full.toString();
    }

    private static final class NonRetryableGeminiException extends RuntimeException {
        NonRetryableGeminiException(String message) {
            super(message);
        }
    }
}

