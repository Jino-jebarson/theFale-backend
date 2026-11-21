package com.thefale.repository;

import com.thefale.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(String contact);
    void deleteByContact(String contact);
}
