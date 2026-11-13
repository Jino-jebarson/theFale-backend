package com.thefale.controller;

import com.thefale.config.AppProperties;
import com.thefale.config.SecurityConfig;
import com.thefale.dto.AiResponseDto;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.security.IdempotencyFilter;
import com.thefale.security.JwtAuthenticationFilter;
import com.thefale.security.JwtTokenProvider;
import com.thefale.security.RateLimitFilter;
import com.thefale.security.RateLimitService;
import com.thefale.service.IdempotencyService;
import com.thefale.service.ai.AiChatContextService;
import com.thefale.service.ai.AiService;
import com.thefale.service.ai.AllyChatHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Real HTTP chunk framing with the production security chain, without a DB or LLM. */
@SpringBootTest(classes = AllyStreamTransportTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ally-transport-test")
class AllyStreamTransportTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("ally-transport-test")
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class, AiController.class})
    static class App {
        @Bean AppProperties properties() { return new AppProperties(); }
        @Bean AiService aiService() { return mock(AiService.class); }
        @Bean AiChatContextService contextService() { return mock(AiChatContextService.class); }
        @Bean AllyChatHistoryService historyService() { return mock(AllyChatHistoryService.class); }
        @Bean JwtTokenProvider tokens() { return mock(JwtTokenProvider.class); }
        @Bean JwtAuthenticationFilter jwt(JwtTokenProvider tokens) { return new JwtAuthenticationFilter(tokens); }
        @Bean RateLimitService rateLimitService() { return mock(RateLimitService.class); }
        @Bean RateLimitFilter rateLimit(RateLimitService service, ObjectMapper mapper) { return new RateLimitFilter(service, mapper); }
        @Bean IdempotencyFilter idempotency(ObjectMapper mapper) { return new IdempotencyFilter(mock(IdempotencyService.class), mapper); }
    }

    @LocalServerPort int port;
    @Autowired AiService ai;
    @Autowired JwtTokenProvider tokens;
    @Autowired RateLimitService limits;
    final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void configure() {
        reset(ai, tokens, limits);
        when(tokens.validateToken("transport-test-token")).thenReturn(true);
        when(tokens.getPrincipalFromToken("transport-test-token")).thenReturn(CustomUserPrincipal.builder()
                .id(UUID.fromString("11111111-1111-4111-8111-111111111111"))
                .role("ROLE_CANDIDATE").enabled(true).build());
        when(limits.tryConsumeGlobal(anyString())).thenReturn(true);
        when(ai.prepareChatStreamContext(any())).thenAnswer(call -> new AiService.PreparedChatRequest(
                call.getArgument(0), UUID.randomUUID(), "clinician", "test", "test"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void authenticatedStreamCompletesWithValidChunkFraming() throws Exception {
        doAnswer(call -> {
            ((Consumer<String>) call.getArgument(1)).accept("Hello ");
            Thread.sleep(20);
            ((Consumer<AiResponseDto>) call.getArgument(2)).accept(AiResponseDto.builder()
                    .success(true).task("chat").provider("test").response("Hello QA").build());
            return null;
        }).when(ai).streamPreparedChat(any(), any(), any());
        HttpResponse<String> response = send(true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/event-stream");
        assertThat(response.body()).contains("event:chunk", "Hello", "event:done", "Hello QA");
    }

    @SuppressWarnings("unchecked")
    @Test
    void providerFailureClosesWithAnErrorEventInsteadOfBrokenChunks() throws Exception {
        doAnswer(call -> {
            ((Consumer<String>) call.getArgument(1)).accept("Partial reply");
            throw new IllegalStateException("Controlled provider failure");
        }).when(ai).streamPreparedChat(any(), any(), any());
        HttpResponse<String> response = send(true);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("event:error").doesNotContain("event:done");
    }

    @Test
    void initialRequestStillRequiresAuthentication() throws Exception {
        assertThat(send(false).statusCode()).isEqualTo(401);
        verify(ai, never()).prepareChatStreamContext(any());
    }

    private HttpResponse<String> send(boolean authenticated) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/ai/chat/stream"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("Origin", "http://localhost:5173").header("Accept", "text/event-stream");
        if (authenticated) builder.header("Authorization", "Bearer transport-test-token");
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString("{\"task\":\"chat\",\"prompt\":\"Hello\"}")).build(), HttpResponse.BodyHandlers.ofString());
    }
}
