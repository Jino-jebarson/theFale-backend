package com.thefale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

/** Structured requirements are public; arrival instructions are disclosed after confirmation. */
@Data
@Embeddable
public class ShiftDetails {
    @Column(name = "profession") private String profession;
    @Column(name = "shift_city") private String city;
    @Column(name = "registration_required") private boolean registrationRequired;
    @Column(name = "bls_required") private boolean blsRequired;
    @Column(name = "acls_required") private boolean aclsRequired;
    @Column(name = "icu_experience_required") private boolean icuExperienceRequired;
    @Column(name = "minimum_experience_years") private Integer minimumExperienceYears;
    @Column(name = "report_to") private String reportTo;
    @Column(name = "report_location") private String reportLocation;
    @Column(name = "dress_requirement") private String dressRequirement;
    @Column(name = "items_to_bring", columnDefinition = "TEXT") private String itemsToBring;
    @Column(name = "reporting_notes", columnDefinition = "TEXT") private String reportingNotes;

    public ShiftDetails publicRequirements() {
        ShiftDetails result = new ShiftDetails();
        result.profession = profession; result.city = city;
        result.registrationRequired = registrationRequired;
        result.blsRequired = blsRequired; result.aclsRequired = aclsRequired;
        result.icuExperienceRequired = icuExperienceRequired;
        result.minimumExperienceYears = minimumExperienceYears;
        return result;
    }
}
