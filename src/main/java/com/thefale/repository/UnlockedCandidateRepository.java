package com.thefale.repository;

import com.thefale.entity.UnlockedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnlockedCandidateRepository extends JpaRepository<UnlockedCandidate, UUID> {

    boolean existsByFacilityIdAndCandidateId(UUID facilityId, UUID candidateId);

    Optional<UnlockedCandidate> findByFacilityIdAndCandidateId(UUID facilityId, UUID candidateId);

    List<UnlockedCandidate> findByFacilityId(UUID facilityId);
}
