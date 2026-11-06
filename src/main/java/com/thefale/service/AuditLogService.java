package com.thefale.service;

import com.thefale.entity.AuditEvent;
import com.thefale.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditEventRepository auditEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(UUID actorId, String action, String resourceType, String resourceId, Map<String, Object> details) {
        logEvent(actorId, action, resourceType, resourceId, null, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(UUID actorId, String action, String resourceType, String resourceId, String ipAddress, Map<String, Object> details) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .actorId(actorId)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(ipAddress)
                    .details(details)
                    .createdAt(Instant.now())
                    .build();

            auditEventRepository.save(event);
            log.info("AUDIT EVENT: action={}, resourceType={}, resourceId={}, actor={}", action, resourceType, resourceId, actorId);
        } catch (Exception e) {
            log.error("Failed to persist audit event for action {}: {}", action, e.getMessage(), e);
        }
    }
}
