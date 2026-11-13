package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "hospital_name", nullable = false, length = 150)
    private String hospitalName;

    @Column(name = "facility_type", nullable = false, length = 50)
    private String facilityType;

    @Column(length = 50)
    private String size;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(length = 255)
    private String website;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false, length = 50)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(name = "address_line", columnDefinition = "TEXT")
    private String addressLine;

    @Column(nullable = false)
    private int beds;

    @Builder.Default
    @Column(name = "icu_beds")
    private int icuBeds = 0;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Column(length = 30)
    private String gst;

    @Column(name = "nabh_number", length = 50)
    private String nabhNumber;

    @Column(name = "accreditation_level", length = 50)
    private String accreditationLevel;

    @Column(name = "contact_person", nullable = false, length = 100)
    private String contactPerson;

    @Column(length = 100)
    private String designation;

    @Column(name = "contact_phone", nullable = false, length = 15)
    private String contactPhone;

    @Column(name = "contact_email", nullable = false, length = 150)
    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    private String departments;

    @Column(name = "hiring_needs", columnDefinition = "TEXT")
    private String hiringNeeds;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "facility_photos", columnDefinition = "TEXT")
    private String facilityPhotos;

    @Builder.Default
    @Column(name = "kyc_status", nullable = false, length = 30)
    private String kycStatus = "PENDING"; // PENDING, VERIFIED, REJECTED

    @Column(name = "kyc_notes", columnDefinition = "TEXT")
    private String kycNotes;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "first_shift_approved", nullable = false)
    private boolean firstShiftApproved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
