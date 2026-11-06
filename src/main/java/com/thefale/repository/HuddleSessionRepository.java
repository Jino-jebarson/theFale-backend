package com.thefale.repository;

import com.thefale.entity.HuddleSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HuddleSessionRepository extends JpaRepository<HuddleSession, UUID> {

    List<HuddleSession> findAllByOrderBySessionWhenAsc();

    List<HuddleSession> findByHostIdOrderBySessionWhenAsc(UUID hostId);
}

