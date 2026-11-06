package com.thefale.repository;

import com.thefale.entity.HuddleSessionBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HuddleSessionBookingRepository extends JpaRepository<HuddleSessionBooking, UUID> {

    boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<HuddleSessionBooking> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    void deleteBySessionIdAndUserId(UUID sessionId, UUID userId);

    int countBySessionId(UUID sessionId);

    List<HuddleSessionBooking> findAllByUserId(UUID userId);
}

