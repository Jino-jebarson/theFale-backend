package com.thefale.repository;

import com.thefale.entity.CandidatePayoutMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidatePayoutMethodRepository extends JpaRepository<CandidatePayoutMethod, UUID> {
    Optional<CandidatePayoutMethod> findByCandidateId(UUID candidateId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from CandidatePayoutMethod p where p.candidateId = :id")
    Optional<CandidatePayoutMethod> findByCandidateIdWithLock(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from CandidatePayoutMethod p where p.id = :id")
    Optional<CandidatePayoutMethod> findByIdWithLock(UUID id);
}
