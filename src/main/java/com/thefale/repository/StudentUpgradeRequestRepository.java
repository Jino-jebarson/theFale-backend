package com.thefale.repository;

import com.thefale.entity.StudentUpgradeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentUpgradeRequestRepository extends JpaRepository<StudentUpgradeRequest, UUID> {
    Optional<StudentUpgradeRequest> findTopByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
    List<StudentUpgradeRequest> findByStatus(String status);
}
