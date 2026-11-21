package com.thefale.repository;

import com.thefale.entity.CandidateReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CandidateReferralCodeRepository extends JpaRepository<CandidateReferralCode, UUID> {
    Optional<CandidateReferralCode> findByReferralCodeIgnoreCase(String referralCode);
}
