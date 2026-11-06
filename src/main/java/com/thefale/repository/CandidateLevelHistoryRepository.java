package com.thefale.repository;

import com.thefale.entity.CandidateLevelHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateLevelHistoryRepository extends JpaRepository<CandidateLevelHistory, UUID> {

    List<CandidateLevelHistory> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
