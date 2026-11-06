package com.thefale.security;

import com.thefale.dto.ApiResponse;
import com.thefale.service.IdempotencyService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class SecurityFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deny-by-default: Unauthenticated request to protected endpoint returns 401 JSON ApiError")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/candidate/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/candidate/profile"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("OWASP security headers are present in all responses")
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/v1/candidate/profile").secure(true))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'"));
    }

    @Test
    @DisplayName("CORS preflight request from allowed origin returns 200 with proper headers")
    void corsPreflightHandlesAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/otp/send")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization,Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Rate limiting rejects after quota is exceeded")
    void rateLimiterEnforcesQuota() {
        String testIp = "192.168.1.100";
        // OTP send endpoint allows 3 requests per 15 minutes
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimitService.tryConsumeOtpSend(testIp)).isTrue();
        }
        // 4th attempt should be rejected
        assertThat(rateLimitService.tryConsumeOtpSend(testIp)).isFalse();
    }

    @Test
    @DisplayName("Idempotency service caches and returns recorded responses")
    void idempotencyStoresAndReturnsCachedResponse() {
        String key = UUID.randomUUID().toString();
        String uri = "/api/v1/facility/postings";
        UUID userId = UUID.randomUUID();

        // Seed a valid user for foreign key constraint
        jdbcTemplate.update(
                "INSERT INTO users (id, phone, role, status) VALUES (?, ?, ?, ?)",
                userId, "+919999988888", "ROLE_FACILITY", "ACTIVE"
        );

        // Initially no cached response
        assertThat(idempotencyService.getCachedResponse(key, userId)).isEmpty();

        // Record a response
        idempotencyService.saveResponse(key, userId, uri, 201, "{\"success\":true,\"message\":\"Job posted\"}");

        // Subsequent lookup returns cached response
        var cached = idempotencyService.getCachedResponse(key, userId);
        assertThat(cached).isPresent();
        assertThat(cached.get().getResponseCode()).isEqualTo(201);
        assertThat(cached.get().getResponseBody()).contains("Job posted");
    }
}
