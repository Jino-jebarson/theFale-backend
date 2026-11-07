package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityOnboardingRequest {

    @NotBlank(message = "Hospital name is required")
    private String hospitalName;

    @NotBlank(message = "Facility type is required")
    private String facilityType;

    private String size;
    private String about;
    private String website;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    private String pincode;
    private String addressLine;

    @Min(value = 1, message = "Facility must have at least 1 bed")
    private int beds;

    private int icuBeds;

    @NotBlank(message = "Clinical Establishment License number is required")
    private String licenseNumber;

    private String gst;
    private String nabhNumber;
    private String accreditationLevel;

    @NotBlank(message = "Contact person name is required")
    private String contactPerson;

    private String designation;

    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^\\+91[6-9]\\d{9}$", message = "Phone must be a valid E.164 Indian mobile number")
    private String contactPhone;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    private java.util.List<String> departments;
    private java.util.List<String> hiringNeeds;
    private String logoUrl;
    private java.util.List<String> facilityPhotos;

    // Verification documents directly on onboarding request (exact candidate pattern)
    private String facilityRegistrationUrl;
    private String facilityRegistrationFileName;

    private String gstCertificateUrl;
    private String gstCertificateFileName;

    private String representativeAuthorizationUrl;
    private String representativeAuthorizationFileName;

    private String nabhCertificateUrl;
    private String nabhCertificateFileName;

    private String fireSafetyClearanceUrl;
    private String fireSafetyClearanceFileName;

    private String pollutionControlClearanceUrl;
    private String pollutionControlClearanceFileName;

    private String biomedicalWasteClearanceUrl;
    private String biomedicalWasteClearanceFileName;

    private java.util.List<FacilityDocumentUploadRequest> documents;

    @JsonSetter("departments")
    public void setDepartments(JsonNode node) {
        this.departments = parseStringOrArray(node);
    }

    @JsonSetter("hiringNeeds")
    public void setHiringNeeds(JsonNode node) {
        this.hiringNeeds = parseStringOrArray(node);
    }

    @JsonSetter("facilityPhotos")
    public void setFacilityPhotos(JsonNode node) {
        this.facilityPhotos = parseStringOrArray(node);
    }

    private List<String> parseStringOrArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> list = new ArrayList<>();
            node.forEach(item -> list.add(item.asText()));
            return list;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return Collections.emptyList();
    }
}
