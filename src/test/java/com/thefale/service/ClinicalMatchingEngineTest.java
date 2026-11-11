package com.thefale.service;

import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.JobPosting;
import com.thefale.entity.LocumShift;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalMatchingEngineTest {

    @Test
    void exactSpecialtyCityQualifiedVerifiedCandidateScoresHigh() {
        CandidateProfile doc = CandidateProfile.builder()
                .profession("Intensivist")
                .specialty("Critical Care")
                .qualification("MD")
                .skills("[\"Ventilator\", \"ACLS\", \"Central Line\"]")
                .experienceYears("4 Years")
                .city("Bengaluru")
                .kycStatus("VERIFIED")
                .build();

        JobPosting job = JobPosting.builder()
                .title("Critical Care Intensivist")
                .department("Critical Care")
                .qualificationRequired("MD")
                .experienceRequired("3-5 Years")
                .city("Bengaluru")
                .description("Manage ICU patients with ventilator, ACLS, and central line procedures.")
                .internship(false)
                .build();

        int score = ClinicalMatchingEngine.calculateJobMatch(doc, false, job);
        assertThat(score).isGreaterThanOrEqualTo(90);
    }

    @Test
    void enforcesCrossCadreSafetyBarrierBelowTwentyEight() {
        CandidateProfile nurse = CandidateProfile.builder()
                .profession("Staff Nurse")
                .specialty("Critical Care")
                .qualification("B.Sc Nursing")
                .experienceYears("3 Years")
                .city("Bengaluru")
                .kycStatus("VERIFIED")
                .build();

        JobPosting surgeonJob = JobPosting.builder()
                .title("Senior Cardiac Surgeon")
                .department("Cardiology")
                .qualificationRequired("MCh")
                .experienceRequired("5+ Years")
                .city("Bengaluru")
                .internship(false)
                .build();

        int score = ClinicalMatchingEngine.calculateJobMatch(nurse, false, surgeonJob);
        assertThat(score).isLessThanOrEqualTo(28);
    }

    @Test
    void wordBoundaryPreventsSubstringFalsePositivesAndScoresExperienceAndShiftFacilityCity() {
        // Ensure "fresher" and "protocol" do NOT trigger "er" (EMERGENCY) or "ot" (SURGERY_OT)
        assertThat(ClinicalMatchingEngine.findSpecialtyDomains("Fresher protocol bedside monitoring"))
                .doesNotContain("EMERGENCY", "SURGERY_OT");

        CandidateProfile nurse = CandidateProfile.builder()
                .profession("Staff Nurse")
                .specialty("Critical Care")
                .qualification("B.Sc Nursing")
                .experienceYears("Fresher")
                .city("Chennai")
                .relocate(false)
                .kycStatus("VERIFIED")
                .build();

        JobPosting seniorIcuNurseJob = JobPosting.builder()
                .title("Senior ICU Staff Nurse")
                .department("Critical Care")
                .qualificationRequired("B.Sc Nursing")
                .experienceRequired("5+ Years")
                .city("Bengaluru")
                .internship(false)
                .build();

        CandidateProfile seniorNurse = CandidateProfile.builder()
                .profession("Staff Nurse")
                .specialty("Critical Care")
                .qualification("B.Sc Nursing")
                .experienceYears("6 Years")
                .city("Bengaluru")
                .kycStatus("VERIFIED")
                .build();

        int fresherOppositeCityScore = ClinicalMatchingEngine.calculateJobMatch(nurse, false, seniorIcuNurseJob);
        int seniorSameCityScore = ClinicalMatchingEngine.calculateJobMatch(seniorNurse, false, seniorIcuNurseJob);
        assertThat(seniorSameCityScore).isGreaterThan(fresherOppositeCityScore + 10);

        LocumShift shift = LocumShift.builder().title("ICU Night Duty Nurse").department("Critical Care").build();
        FacilityProfile localFacility = FacilityProfile.builder().city("Bengaluru").build();
        FacilityProfile remoteFacility = FacilityProfile.builder().city("Chennai").build();

        int localShiftScore = ClinicalMatchingEngine.calculateShiftMatch(seniorNurse, false, shift, localFacility);
        int remoteShiftScore = ClinicalMatchingEngine.calculateShiftMatch(seniorNurse, false, shift, remoteFacility);
        assertThat(localShiftScore).isGreaterThan(remoteShiftScore);
    }
}

