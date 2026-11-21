package com.thefale.repository;

import com.thefale.entity.CandidateReferral;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateReferralRepository extends JpaRepository<CandidateReferral, UUID> {
    List<CandidateReferral> findByReferrerCandidateIdOrderByCreatedAtDesc(UUID referrerCandidateId);
    Optional<CandidateReferral> findByReferredCandidateId(UUID referredCandidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CandidateReferral r where r.referredCandidateId = :candidateId")
    Optional<CandidateReferral> findByReferredCandidateIdForUpdate(@Param("candidateId") UUID candidateId);

    long countByReferrerCandidateIdAndQualifiedAtGreaterThanEqualAndRewardStatusIn(
            UUID referrerCandidateId, Instant startOfMonth, List<String> statuses);

    List<CandidateReferral> findByRewardStatusOrderByQualifiedAtAsc(String rewardStatus);
}
