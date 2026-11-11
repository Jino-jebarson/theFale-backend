package com.thefale.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.AiRequestDto;
import com.thefale.dto.AiResponseDto;
import com.thefale.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceTest {

    private static class StubGeminiClient extends GeminiAiClient {
        private final boolean configured;
        private final String output;
        private final RuntimeException failure;
        String capturedSystemPrompt;
        String capturedUserPrompt;

        StubGeminiClient(boolean configured, String output, RuntimeException failure) {
            super("", new ObjectMapper());
            this.configured = configured;
            this.output = output;
            this.failure = failure;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String generateContent(String systemPrompt, String userPrompt) {
            this.capturedSystemPrompt = systemPrompt;
            this.capturedUserPrompt = userPrompt;
            if (failure != null) throw failure;
            return output;
        }

        @Override
        public String streamContent(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onChunk) {
            this.capturedSystemPrompt = systemPrompt;
            this.capturedUserPrompt = userPrompt;
            if (failure != null) throw failure;
            if (output != null && onChunk != null) {
                int mid = Math.max(1, output.length() / 2);
                onChunk.accept(output.substring(0, mid));
                onChunk.accept(output.substring(mid));
            }
            return output;
        }
    }

    private static class StubClaudeClient extends ClaudeAiClient {
        private final boolean configured;
        private final String output;
        private final RuntimeException failure;

        StubClaudeClient(boolean configured, String output, RuntimeException failure) {
            super("", new ObjectMapper());
            this.configured = configured;
            this.output = output;
            this.failure = failure;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String generateContent(String systemPrompt, String userPrompt) {
            if (failure != null) throw failure;
            return output;
        }
    }

    @Test
    void throwsServiceUnavailableWhenNoLiveProviderConfigured() {
        AiService service = new AiService(
                new StubGeminiClient(false, null, null),
                new StubClaudeClient(false, null, null),
                "gemini"
        );

        AiRequestDto req = AiRequestDto.builder()
                .task("daily_brief")
                .prompt("Brief me")
                .build();

        assertThatThrownBy(() -> service.executeTask(req))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("no provider API key");
        assertThat(service.getConfiguredProviders()).isEmpty();
    }

    @Test
    void throwsServiceUnavailableInsteadOfStaticFallbackWhenLiveProviderFails() {
        AiService service = new AiService(
                new StubGeminiClient(true, null, new RuntimeException("Gemini 503")),
                new StubClaudeClient(false, null, null),
                "gemini"
        );

        AiRequestDto req = AiRequestDto.builder()
                .task("kyc_precheck")
                .prompt("Check TN6380")
                .build();

        assertThatThrownBy(() -> service.executeTask(req))
                .isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void failsOverToConfiguredSecondaryLiveProvider() {
        AiService service = new AiService(
                new StubGeminiClient(true, null, new RuntimeException("Transient failure")),
                new StubClaudeClient(true, "{\"status\":\"VERIFIED\",\"isVerified\":true,\"confidence\":94,\"assessment\":\"CEA registration conforms to Tamil Nadu standards.\",\"recommendation\":\"Approve facility.\"}", null),
                "gemini"
        );

        AiRequestDto req = AiRequestDto.builder()
                .task("facility_audit")
                .prompt("Audit Apollo Hospital")
                .build();

        AiResponseDto res = service.executeTask(req);
        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getProvider()).isEqualTo("claude");
        assertThat(res.getResponse()).isEqualTo("CEA registration conforms to Tamil Nadu standards.");
        assertThat(res.getStructuredData()).containsEntry("status", "VERIFIED");
        assertThat(res.getStructuredData()).containsEntry("confidence", 94);
    }

    @Test
    void extractsStructuredJsonAndReadableDraftForAllyHandoffAndStripsMarkdownForBrief() {
        StubGeminiClient geminiJson = new StubGeminiClient(
                true,
                "```json\n{\"ticketSummary\":\"Escrow UTR inquiry\",\"whatsappDraft\":\"Hi Dr. Ravi, your payout is under priority bank trace.\"}\n```",
                null
        );
        AiService jsonService = new AiService(geminiJson, new StubClaudeClient(false, null, null), "gemini");

        AiResponseDto handoffRes = jsonService.executeTask(
                AiRequestDto.builder().task("ally_handoff").prompt("Pending UTR payout").build()
        );
        assertThat(handoffRes.isSuccess()).isTrue();
        assertThat(handoffRes.getProvider()).isEqualTo("gemini");
        assertThat(handoffRes.getResponse()).isEqualTo("Hi Dr. Ravi, your payout is under priority bank trace.");
        assertThat(handoffRes.getStructuredData()).containsEntry("ticketSummary", "Escrow UTR inquiry");

        StubGeminiClient geminiBrief = new StubGeminiClient(
                true,
                "• **1 Candidate KYC** awaiting check.\n• **0 Escrow Disputes** active.",
                null
        );
        AiService briefService = new AiService(geminiBrief, new StubClaudeClient(false, null, null), "gemini");

        AiResponseDto briefRes = briefService.executeTask(
                AiRequestDto.builder()
                        .task("daily_brief")
                        .prompt("Metrics")
                        .metadata(Map.of("pendingKyc", 1, "disputes", 0))
                        .build()
        );
        assertThat(briefRes.getResponse()).doesNotContain("**");
        assertThat(briefRes.getResponse()).contains("1 Candidate KYC");
        assertThat(geminiBrief.capturedUserPrompt).contains("pendingKyc=1");
    }

    @Test
    void chatIncludesHistoryAndExtractsStructuredReplyActionAndSuggestions() {
        StubGeminiClient geminiChat = new StubGeminiClient(
                true,
                "{\"reply\":\"You have 2 pending hospital KYC audits. Please inspect them in Hospital Verification.\",\"action\":\"verify_hospitals\",\"actionLabel\":\"Open Hospital Queue\",\"suggestions\":[\"Check Candidate KYC\",\"Check Open Tickets\"]}",
                null
        );
        AiChatContextService contextService = new AiChatContextService(
                null, null, null, null, null, null, null, null, null
        );
        AiService chatService = new AiService(geminiChat, new StubClaudeClient(false, null, null), "gemini", contextService);

        AiResponseDto res = chatService.executeTask(
                AiRequestDto.builder()
                        .task("chat")
                        .role("admin")
                        .language("kn")
                        .prompt("Which queue needs my review?")
                        .history(List.of(Map.of("role", "user", "text", "Hello Ally")))
                        .build()
        );

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getResponse()).contains("2 pending hospital KYC audits");
        assertThat(res.getAction()).isEqualTo("verify_hospitals");
        assertThat(res.getActionData()).containsEntry("label", "Open Hospital Queue");
        assertThat(res.getSuggestions()).containsExactly("Check Candidate KYC", "Check Open Tickets");
        assertThat(geminiChat.capturedUserPrompt).contains("User: Hello Ally");
        assertThat(geminiChat.capturedUserPrompt).contains("Current User Message: Which queue needs my review?");
        assertThat(geminiChat.capturedSystemPrompt).contains("HUMAN-IN-THE-LOOP");
        assertThat(geminiChat.capturedSystemPrompt).contains("Kannada");

        // Cross-role action sanitization: admin must not receive clinician open_to_work action
        assertThat(contextService.sanitizeActionForRole("admin", "open_to_work")).isNull();
        assertThat(contextService.sanitizeActionForRole("hospital", "post_shift")).isEqualTo("post_shift");
        assertThat(contextService.sanitizeActionForRole("student", "find_internships")).isEqualTo("find_internships");
    }

    @Test
    void streamChatSplitsTextChunksFromTrailingMetaBlock() {
        StubGeminiClient streamingGemini = new StubGeminiClient(
                true,
                "You have 3 matching shifts in Bengaluru.\n[FALE_META]{\"action\":\"browse_shifts\",\"actionLabel\":\"Browse Locum Shifts\",\"suggestions\":[\"Check My KYC\",\"Open Shifts\"]}[/FALE_META]",
                null
        );
        AiChatContextService contextService = new AiChatContextService(
                null, null, null, null, null, null, null, null, null
        );
        AiService service = new AiService(streamingGemini, new StubClaudeClient(false, null, null), "gemini", contextService);

        StringBuilder streamedReply = new StringBuilder();
        final AiResponseDto[] finalHolder = new AiResponseDto[1];

        AiService.PreparedChatRequest prepared = service.prepareChatStreamContext(
                AiRequestDto.builder().task("chat").role("clinician").language("en").prompt("Any shifts?").build()
        );
        service.streamPreparedChat(prepared, streamedReply::append, dto -> finalHolder[0] = dto);

        assertThat(streamedReply.toString()).isEqualTo("You have 3 matching shifts in Bengaluru.");
        assertThat(streamedReply.toString()).doesNotContain("[FALE_META]");
        assertThat(finalHolder[0]).isNotNull();
        assertThat(finalHolder[0].getAction()).isEqualTo("browse_shifts");
        assertThat(finalHolder[0].getActionData()).containsEntry("label", "Browse Locum Shifts");
        assertThat(finalHolder[0].getSuggestions()).containsExactly("Check My KYC", "Open Shifts");
    }
}

