package com.thefale.repository;

import com.thefale.entity.CandidatePayoutMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidatePayoutMethodRepository extends JpaRepository<CandidatePayoutMethod, UUID> {
    Optional<CandidatePayoutMethod> findByCandidateId(UUID candidateId);
}
