package com.thefale.service.ai;

public interface AiProviderClient {
    String getProviderName();
    boolean isConfigured();
    String generateContent(String systemPrompt, String userPrompt) throws Exception;
}

