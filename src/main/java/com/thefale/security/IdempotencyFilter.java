package com.thefale.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.ApiError;
import com.thefale.entity.IdempotencyKey;
import com.thefale.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private static final Set<String> IDEMPOTENT_ENDPOINTS = Set.of(
            "/api/v1/employer/shifts",
            "/api/v1/employer/wallet/topup",
            "/api/v1/employer/credits/purchase"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean isIdempotentEndpoint = IDEMPOTENT_ENDPOINTS.contains(path)
                || (path.startsWith("/api/v1/employer/candidates/") && path.endsWith("/unlock"));

        if (!isIdempotentEndpoint || !request.getMethod().equalsIgnoreCase("POST")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (!StringUtils.hasText(idempotencyKey)) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiError error = ApiError.builder()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .error("Bad Request")
                    .message("The 'Idempotency-Key' HTTP header is required for this financial/mutation operation.")
                    .path(path)
                    .correlationId(UUID.randomUUID().toString())
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        Optional<CustomUserPrincipal> principalOpt = SecurityUtils.getCurrentPrincipal();
        if (principalOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId = principalOpt.get().getId();
        Optional<IdempotencyKey> cachedRecord = idempotencyService.getCachedResponse(idempotencyKey, userId);

        if (cachedRecord.isPresent()) {
            IdempotencyKey cached = cachedRecord.get();
            log.info("Replaying cached response for idempotency key {} on {}", idempotencyKey, path);
            response.setStatus(cached.getResponseCode());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Cache-Lookup", "HIT-IDEMPOTENT");
            response.getWriter().write(cached.getResponseBody());
            return;
        }

        // Wrap response to capture output body
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, responseWrapper);

        int status = responseWrapper.getStatus();
        if (status >= 200 && status < 300) {
            byte[] bodyBytes = responseWrapper.getContentAsByteArray();
            String responseBody = new String(bodyBytes, StandardCharsets.UTF_8);
            try {
                idempotencyService.saveResponse(idempotencyKey, userId, path, status, responseBody);
            } catch (Exception e) {
                log.warn("Could not save idempotency record: {}", e.getMessage());
            }
        }

        responseWrapper.copyBodyToResponse();
    }
}
