package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FacilityProfileDto {
    private UUID id;
    private UUID userId;
    private String hospitalName;
    private String facilityType;
    private String size;
    private String about;
    private String website;
    private String city;
    private String state;
    private String pincode;
    private String addressLine;
    private int beds;
    private int icuBeds;
    private String licenseNumber;
    private String gst;
    private String nabhNumber;
    private String accreditationLevel;
    private String contactPerson;
    private String designation;
    private String contactPhone;
    private String contactEmail;
    private java.util.List<String> departments;
    private java.util.List<String> hiringNeeds;
    private String logoUrl;
    private java.util.List<String> facilityPhotos;
    private String kycStatus;
    private String kycNotes;
    private Instant verifiedAt;
    private Instant createdAt;
}
