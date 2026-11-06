package com.thefale.repository;

import com.thefale.entity.ShiftClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftClaimRepository extends JpaRepository<ShiftClaim, UUID> {

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
