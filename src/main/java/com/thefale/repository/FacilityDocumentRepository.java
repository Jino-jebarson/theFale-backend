package com.thefale.repository;

import com.thefale.entity.FacilityDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacilityDocumentRepository extends JpaRepository<FacilityDocument, UUID> {
    List<FacilityDocument> findByFacilityId(UUID facilityId);
    List<FacilityDocument> findByFacilityIdAndDocumentType(UUID facilityId, String documentType);
    void deleteByFacilityIdAndDocumentType(UUID facilityId, String documentType);
}

