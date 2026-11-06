package com.thefale.service;

import com.thefale.entity.IdempotencyKey;
import com.thefale.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> getCachedResponse(String key, UUID userId) {
        return repository.findByKeyAndUserId(key, userId);
    }

    @Transactional
    public void saveResponse(String key, UUID userId, String endpoint, int responseCode, String responseBody) {
        IdempotencyKey record = IdempotencyKey.builder()
                .key(key)
                .userId(userId)
                .endpoint(endpoint)
                .responseCode(responseCode)
                .responseBody(responseBody)
                .createdAt(Instant.now())
                .build();
        repository.save(record);
    }
}
