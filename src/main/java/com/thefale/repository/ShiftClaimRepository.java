package com.thefale.repository;

import com.thefale.entity.ShiftClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftClaimRepository extends JpaRepository<ShiftClaim, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ShiftClaim c WHERE c.id = :id")
    Optional<ShiftClaim> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT COUNT(c) FROM ShiftClaim c WHERE c.candidateId = :candidateId AND c.status NOT IN ('CANCELLED', 'WITHDRAWN', 'REJECTED', 'SLOT_FILLED', 'NO_SHOW')")
    long countValidFeeClaimsByCandidate(@Param("candidateId") UUID candidateId);

    @Query("SELECT COUNT(c) FROM ShiftClaim c WHERE c.candidateId = :candidateId AND c.status = 'COMPLETED' AND c.id <> :excludeClaimId")
    long countCompletedClaimsExcluding(@Param("candidateId") UUID candidateId, @Param("excludeClaimId") UUID excludeClaimId);

    @Query("SELECT c FROM ShiftClaim c WHERE c.candidateId = :candidateId AND c.status IN ('CLAIMED', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'TIMESHEET_SUBMITTED', 'CLAIM_PENDING_ADMIN')")
    List<ShiftClaim> findActiveClaimsByCandidate(@Param("candidateId") UUID candidateId);

    @Query("SELECT c FROM ShiftClaim c WHERE c.candidateId = :candidateId AND c.hospitalRating IS NOT NULL")
    List<ShiftClaim> findRatedClaimsByCandidate(@Param("candidateId") UUID candidateId);

    List<ShiftClaim> findByCandidateIdOrderByClaimedAtDesc(UUID candidateId);

    List<ShiftClaim> findByShiftId(UUID shiftId);

    List<ShiftClaim> findByShiftIdIn(java.util.Collection<UUID> shiftIds);

    List<ShiftClaim> findByPayoutStatus(String payoutStatus);

    long countByPayoutStatus(String payoutStatus);

    Optional<ShiftClaim> findByShiftIdAndCandidateId(UUID shiftId, UUID candidateId);

    boolean existsByShiftIdAndCandidateId(UUID shiftId, UUID candidateId);

    List<ShiftClaim> findByStatus(String status);

    List<ShiftClaim> findByStatusOrderByClaimedAtDesc(String status);
}
