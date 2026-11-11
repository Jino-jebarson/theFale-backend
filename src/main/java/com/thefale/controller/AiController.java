package com.thefale.controller;

import com.thefale.dto.AiRequestDto;
import com.thefale.dto.AiResponseDto;
import com.thefale.dto.AllyChatMessageDto;
import com.thefale.dto.ApiResponse;
import com.thefale.security.SecurityUtils;
import com.thefale.service.ai.AiChatContextService;
import com.thefale.service.ai.AiService;
import com.thefale.service.ai.AllyChatHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Engine", description = "Live Ally AI endpoints powered by Gemini and Claude")
public class AiController {

    private final AiService aiService;
    private final AiChatContextService chatContextService;
    private final AllyChatHistoryService chatHistoryService;

    public AiController(
            AiService aiService,
            AiChatContextService chatContextService,
            AllyChatHistoryService chatHistoryService
    ) {
        this.aiService = aiService;
        this.chatContextService = chatContextService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute any FALE Ally AI task against a live LLM provider")
    public ResponseEntity<AiResponseDto> executeTask(@RequestBody AiRequestDto request) {
        AiResponseDto response = aiService.executeTask(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream role-aware Ally Chat tokens over Server-Sent Events")
    public SseEmitter streamChat(@RequestBody AiRequestDto request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        AiService.PreparedChatRequest prepared = aiService.prepareChatStreamContext(request);

        CompletableFuture.runAsync(() -> {
            try {
                aiService.streamPreparedChat(
                        prepared,
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("chunk").data(Map.of("text", chunk)));
                            } catch (Exception ignored) {}
                        },
                        finalDto -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(finalDto));
                                emitter.complete();
                            } catch (Exception ex) {
                                emitter.completeWithError(ex);
                            }
                        }
                );
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "message", ex.getMessage() != null ? ex.getMessage() : "Live Ally AI is temporarily unavailable."
                    )));
                    emitter.complete();
                } catch (Exception sendEx) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @GetMapping("/chat/history")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Fetch cross-device Ally Copilot chat history for the authenticated user")
    public ResponseEntity<ApiResponse<List<AllyChatMessageDto>>> getChatHistory(
            @RequestParam(required = false) String role
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String effectiveRole = chatContextService.resolveEffectiveRole(
                AiRequestDto.builder().role(role).build()
        );
        return ResponseEntity.ok(ApiResponse.success(chatHistoryService.getHistory(userId, effectiveRole)));
    }

    @DeleteMapping("/chat/history")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Clear cross-device Ally Copilot chat history for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> clearChatHistory(
            @RequestParam(required = false) String role
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String effectiveRole = chatContextService.resolveEffectiveRole(
                AiRequestDto.builder().role(role).build()
        );
        chatHistoryService.clearHistory(userId, effectiveRole);
        return ResponseEntity.ok(ApiResponse.success("Ally chat history cleared"));
    }

    @PatchMapping("/chat/messages/{messageId}/ticket")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Attach an escalated Support Ticket ID onto a persisted Ally message")
    public ResponseEntity<ApiResponse<Void>> attachTicketToMessage(
            @PathVariable UUID messageId,
            @RequestBody Map<String, String> body
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String ticketId = body != null ? body.get("ticketId") : null;
        chatHistoryService.attachTicketId(userId, messageId, ticketId);
        return ResponseEntity.ok(ApiResponse.success("Support ticket linked"));
    }

    @GetMapping("/status")
    @Operation(summary = "Get real-time live AI provider status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        var configuredProviders = aiService.getConfiguredProviders();
        boolean online = !configuredProviders.isEmpty();
        return ResponseEntity.ok(Map.of(
                "activeProvider", aiService.getActiveProvider(),
                "configuredProviders", configuredProviders,
                "status", online ? "ONLINE" : "UNCONFIGURED",
                "staticFallbackEnabled", false
        ));
    }
}

