package com.thefale.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.AiResponseDto;
import com.thefale.dto.AllyChatMessageDto;
import com.thefale.entity.AllyChatMessage;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.AllyChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists and retrieves cross-device Ally Copilot chat threads per authenticated user and role scope.
 */
@Service
public class AllyChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AllyChatHistoryService.class);

    private final AllyChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public AllyChatHistoryService(AllyChatMessageRepository messageRepository, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AllyChatMessageDto> getHistory(UUID userId, String roleScope) {
        if (userId == null) return List.of();
        List<AllyChatMessage> latest = new ArrayList<>(
                messageRepository.findTop60ByUserIdAndRoleScopeOrderByCreatedAtDesc(userId, normalizeScope(roleScope))
        );
        Collections.reverse(latest);
        return latest.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getRecentPromptTurns(UUID userId, String roleScope, int limit) {
        List<AllyChatMessageDto> all = getHistory(userId, roleScope);
        int start = Math.max(0, all.size() - limit);
        List<Map<String, String>> turns = new ArrayList<>();
        for (int i = start; i < all.size(); i++) {
            AllyChatMessageDto msg = all.get(i);
            turns.add(Map.of(
                    "role", "user".equals(msg.getSender()) ? "user" : "assistant",
                    "text", msg.getText() != null ? msg.getText() : ""
            ));
        }
        return turns;
    }

    @Transactional
    public UUID appendTurn(UUID userId, String roleScope, String userPrompt, AiResponseDto assistantResponse) {
        if (userId == null || assistantResponse == null || !assistantResponse.isSuccess()) {
            return null;
        }
        String scope = normalizeScope(roleScope);
        Instant userTs = Instant.now();
        Instant botTs = userTs.plusMillis(1);

        if (userPrompt != null && !userPrompt.isBlank()) {
            messageRepository.save(AllyChatMessage.builder()
                    .userId(userId)
                    .roleScope(scope)
                    .sender("user")
                    .messageText(userPrompt.trim())
                    .createdAt(userTs)
                    .build());
        }

        String actionLabel = null;
        if (assistantResponse.getActionData() != null && assistantResponse.getActionData().get("label") != null) {
            actionLabel = assistantResponse.getActionData().get("label").toString();
        }

        String suggestionsJson = serializeSuggestions(assistantResponse.getSuggestions());
        AllyChatMessage savedBot = messageRepository.save(AllyChatMessage.builder()
                .userId(userId)
                .roleScope(scope)
                .sender("ally")
                .messageText(assistantResponse.getResponse() != null ? assistantResponse.getResponse() : "")
                .action(assistantResponse.getAction())
                .actionLabel(actionLabel)
                .suggestionsJson(suggestionsJson)
                .createdAt(botTs)
                .build());

        // Attach saved message ID cleanly onto actionData so the UI can link follow-up support tickets
        Map<String, Object> nextActionData = new LinkedHashMap<>();
        if (assistantResponse.getActionData() != null) {
            nextActionData.putAll(assistantResponse.getActionData());
        }
        nextActionData.put("messageId", savedBot.getId().toString());
        assistantResponse.setActionData(nextActionData);
        return savedBot.getId();
    }

    @Transactional
    public void clearHistory(UUID userId, String roleScope) {
        if (userId == null) return;
        messageRepository.deleteByUserIdAndRoleScope(userId, normalizeScope(roleScope));
    }

    @Transactional
    public void attachTicketId(UUID userId, UUID messageId, String ticketId) {
        if (userId == null || messageId == null || ticketId == null || ticketId.isBlank()) return;
        AllyChatMessage msg = messageRepository.findByIdAndUserId(messageId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat message not found"));
        msg.setTicketId(ticketId.trim());
        messageRepository.save(msg);
    }

    private AllyChatMessageDto toDto(AllyChatMessage entity) {
        return AllyChatMessageDto.builder()
                .id(entity.getId())
                .sender(entity.getSender())
                .text(entity.getMessageText())
                .action(entity.getAction())
                .actionLabel(entity.getActionLabel())
                .suggestions(deserializeSuggestions(entity.getSuggestionsJson()))
                .ticketId(entity.getTicketId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String serializeSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (Exception ex) {
            log.debug("Could not serialize Ally suggestions: {}", ex.getMessage());
            return null;
        }
    }

    private List<String> deserializeSuggestions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return "clinician";
        String clean = scope.trim().toLowerCase();
        if ("facility".equals(clean)) return "hospital";
        if ("professional".equals(clean)) return "clinician";
        return clean;
    }
}

