package com.thefale.repository;

import com.thefale.entity.AllyChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AllyChatMessageRepository extends JpaRepository<AllyChatMessage, UUID> {
    List<AllyChatMessage> findTop60ByUserIdAndRoleScopeOrderByCreatedAtDesc(UUID userId, String roleScope);
    Optional<AllyChatMessage> findByIdAndUserId(UUID id, UUID userId);
    void deleteByUserIdAndRoleScope(UUID userId, String roleScope);
}

