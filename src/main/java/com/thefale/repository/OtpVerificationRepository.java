package com.thefale.repository;

import com.thefale.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    Optional<OtpVerification> findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(String contact);
    void deleteByContact(String contact);
}
