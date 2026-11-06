package com.thefale.repository;

import com.thefale.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    List<JobApplication> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);
    List<JobApplication> findByJobId(UUID jobId);
    List<JobApplication> findByJobIdIn(List<UUID> jobIds);
    Optional<JobApplication> findByJobIdAndCandidateId(UUID jobId, UUID candidateId);
    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);
    List<JobApplication> findAllByOrderByAppliedAtDesc();
    List<JobApplication> findByStatusOrderByAppliedAtDesc(String status);
    long countByStatus(String status);
}
