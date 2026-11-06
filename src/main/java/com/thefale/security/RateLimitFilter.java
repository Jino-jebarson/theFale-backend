package com.thefale.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = extractClientIp(request);

        boolean allowed = true;
        if (path.equals("/api/v1/auth/otp/send")) {
            allowed = rateLimitService.tryConsumeOtpSend(clientIp);
        } else if (path.equals("/api/v1/auth/otp/verify")) {
            allowed = rateLimitService.tryConsumeOtpVerify(clientIp);
        } else if (path.startsWith("/api/")) {
            allowed = rateLimitService.tryConsumeGlobal(clientIp);
        }

        if (!allowed) {
            log.warn("Rate limit exceeded for IP {} on endpoint {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");

            ApiError error = ApiError.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error("Too Many Requests")
                    .message("Rate limit exceeded. Please wait before retrying.")
                    .path(path)
                    .correlationId(UUID.randomUUID().toString())
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
