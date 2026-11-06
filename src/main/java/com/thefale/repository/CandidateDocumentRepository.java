package com.thefale.repository;

import com.thefale.entity.CandidateDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, UUID> {
    List<CandidateDocument> findByCandidateId(UUID candidateId);
}
