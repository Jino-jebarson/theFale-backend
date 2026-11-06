package com.thefale.repository;

import com.thefale.entity.MentorApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorApplicationRepository extends JpaRepository<MentorApplication, UUID> {
    Optional<MentorApplication> findByCandidateId(UUID candidateId);
    Optional<MentorApplication> findByUserId(UUID userId);
    List<MentorApplication> findAllByOrderByAppliedAtDesc();
}

