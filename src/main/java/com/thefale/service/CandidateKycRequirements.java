package com.thefale.service;

import com.thefale.entity.CandidateDocument;
import java.util.List;
import java.util.Set;

/** Shared candidate KYC document rules used by upload and admin review flows. */
final class CandidateKycRequirements {
    private static final Set<String> STUDY_PROOF_TYPES = Set.of("STUDENT_ID", "BONAFIDE");
    private CandidateKycRequirements() {}

    static boolean hasRequiredDocuments(boolean student, List<CandidateDocument> documents, String universityRegNo) {
        List<CandidateDocument> submitted = documents == null ? List.of() : documents;
        if (student) return hasStudyProof(submitted) || hasUniversityRegistrationNumber(universityRegNo);
        return hasUsable(submitted, "ID_PROOF") && hasUsable(submitted, "REGISTRATION_CERTIFICATE") && hasUsable(submitted, "DEGREE_CERTIFICATE");
    }

    static boolean requiredDocumentsApproved(boolean student, List<CandidateDocument> documents, String universityRegNo) {
        if (!hasRequiredDocuments(student, documents, universityRegNo)) return false;
        if (student) {
            // A registration number is submitted as profile data, so its verification is part of
            // the admin's overall KYC decision rather than a separate uploaded-document review.
            return hasUniversityRegistrationNumber(universityRegNo)
                    || documents.stream().anyMatch(doc -> STUDY_PROOF_TYPES.contains(type(doc))
                    && "VERIFIED".equalsIgnoreCase(doc.getReviewStatus()));
        }
        return hasApproved(documents, "ID_PROOF") && hasApproved(documents, "REGISTRATION_CERTIFICATE") && hasApproved(documents, "DEGREE_CERTIFICATE");
    }

    private static boolean hasStudyProof(List<CandidateDocument> documents) {
        return documents.stream().anyMatch(doc -> STUDY_PROOF_TYPES.contains(type(doc)) && notRejected(doc));
    }

    private static boolean hasUniversityRegistrationNumber(String value) {
        return value != null && value.trim().length() >= 5;
    }

    private static boolean hasUsable(List<CandidateDocument> docs, String type) { return docs.stream().anyMatch(doc -> type.equals(type(doc)) && notRejected(doc)); }
    private static boolean hasApproved(List<CandidateDocument> docs, String type) { return docs.stream().anyMatch(doc -> type.equals(type(doc)) && "VERIFIED".equalsIgnoreCase(doc.getReviewStatus())); }
    private static boolean notRejected(CandidateDocument doc) { return !"REJECTED".equalsIgnoreCase(doc.getReviewStatus()); }
    private static String type(CandidateDocument doc) { return doc.getDocumentType() == null ? "" : doc.getDocumentType().trim().toUpperCase(); }
}
