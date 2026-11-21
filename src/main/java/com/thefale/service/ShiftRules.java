package com.thefale.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.CandidateCertificationDto;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ShiftRules {
    private ShiftRules() {}
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<CandidateCertificationDto>> CERTIFICATION_LIST = new TypeReference<>() {};
    public static final Set<String> RESERVED = Set.of("CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "TIMESHEET_SUBMITTED", "COMPLETED", "DISPUTED");
    public static boolean reservesSlot(ShiftClaim claim) { return RESERVED.contains(String.valueOf(claim.getStatus()).toUpperCase()); }

    public static void validateDetails(ShiftDetails d) {
        if (d == null || blank(d.getProfession()) || blank(d.getCity()) || blank(d.getReportTo()) || blank(d.getReportLocation()))
            throw new BadRequestException("Profession, city, reporting person and reporting location are required.");
        if (d.getProfession().length() > 100 || d.getCity().length() > 100 || d.getReportTo().length() > 255 || d.getReportLocation().length() > 255
                || (d.getDressRequirement() != null && d.getDressRequirement().length() > 255)
                || (d.getReportingNotes() != null && d.getReportingNotes().length() > 2000)
                || (d.getItemsToBring() != null && d.getItemsToBring().length() > 1000))
            throw new BadRequestException("Shift arrival details are too long.");
        if (d.getMinimumExperienceYears() != null && (d.getMinimumExperienceYears() < 0 || d.getMinimumExperienceYears() > 60))
            throw new BadRequestException("Minimum experience must be between 0 and 60 years.");
    }

    public static void checkEligibility(CandidateProfile candidate, ShiftDetails d, List<CandidateDocument> documents) {
        if (d == null) return; // Historical posts have no structured requirements.
        if (!blank(d.getProfession()) && !profession(d.getProfession()).equals(profession(candidate.getProfession())))
            throw new BadRequestException("This shift requires profession: " + d.getProfession());
        if (d.isRegistrationRequired() && !"VERIFIED".equalsIgnoreCase(candidate.getKycStatus()))
            throw new BadRequestException("Verified registration is required.");
        if (d.isBlsRequired() && !certificate(candidate, documents, "BLS")) throw new BadRequestException("A platform-verified BLS certificate is required.");
        if (d.isAclsRequired() && !certificate(candidate, documents, "ACLS")) throw new BadRequestException("A platform-verified ACLS certificate is required.");
        String skills = String.valueOf(candidate.getSkills()) + " " + String.valueOf(candidate.getSpecialty());
        if (d.isIcuExperienceRequired() && !(skills.toLowerCase().contains("icu") || skills.toLowerCase().contains("critical care")))
            throw new BadRequestException("ICU experience must be present in your profile.");
        if (d.getMinimumExperienceYears() != null && d.getMinimumExperienceYears() > 0) {
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(String.valueOf(candidate.getExperienceYears()));
            String experience = String.valueOf(candidate.getExperienceYears()).toLowerCase();
            double years = number.find() ? Double.parseDouble(number.group()) : -1;
            if (!experience.contains("year") && !experience.contains("yr") && experience.matches(".*[0-9][ ]*(mo|month).*")) years /= 12;
            if (experience.contains("less") || experience.contains("under") || experience.contains("<")) years = -1;
            if (years < d.getMinimumExperienceYears())
                throw new BadRequestException("This shift requires at least " + d.getMinimumExperienceYears() + " years of experience.");
        }
    }
    private static String profession(String value) {
        String text = String.valueOf(value).toLowerCase().trim();
        if (text.contains("nurse") || text.contains("nursing")) return "nurse";
        if (text.equals("doctor") || text.contains("medical officer") || text.contains("physician") || text.contains("surgeon")) return "doctor";
        return text.replaceAll("[^a-z0-9]", "");
    }
    private static boolean certificate(CandidateProfile candidate, List<CandidateDocument> docs, String name) {
        if (docs == null) return false;
        Set<UUID> verifiedDocumentIds = docs.stream()
                .filter(CandidateDocument::isVerified)
                .map(CandidateDocument::getId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.regex.Pattern certificateName = java.util.regex.Pattern.compile("(?i)\\b" + name + "\\b");
        boolean matchedDocument = docs.stream().anyMatch(document -> document.isVerified()
                && (name.equalsIgnoreCase(document.getDocumentType())
                || certificateName.matcher(String.valueOf(document.getOriginalFileName())).find()));
        if (matchedDocument) return true;

        // Candidate certificates store their display name separately from the uploaded file name.
        // Honor that metadata only when its linked document has been verified by the platform.
        try {
            List<CandidateCertificationDto> certifications = OBJECT_MAPPER.readValue(candidate.getCertifications(), CERTIFICATION_LIST);
            return certifications != null && certifications.stream().anyMatch(certification -> {
                if (certification.getName() == null || certification.getDocumentId() == null
                        || !certificateName.matcher(certification.getName()).find()) return false;
                try {
                    return verifiedDocumentIds.contains(UUID.fromString(certification.getDocumentId()));
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            });
        } catch (Exception ignored) {
            return false;
        }
    }
    private static boolean blank(String text) { return text == null || text.isBlank(); }
}
