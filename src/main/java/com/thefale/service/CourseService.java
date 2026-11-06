package com.thefale.service;

import com.thefale.dto.CourseDto;
import com.thefale.entity.Course;
import com.thefale.entity.CourseEnrollment;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.CourseEnrollmentRepository;
import com.thefale.repository.CourseRepository;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final AuditLogService auditLogService;

    @jakarta.annotation.PostConstruct
    @Transactional
    public void initDefaultCoursesIfEmpty() {
        if (courseRepository.count() < 20) {
            log.info("Populating accredited CME courses in database...");
            List<Course> defaults = List.of(
                Course.builder().id("cme-ecg-101").title("Acute Coronary Syndromes & 12-Lead ECG Mastery").category("Critical Care").duration("6 Hours").cmePoints(5).summary("Stanford Medicine CME aligned: Systematic interpretation of STEMI, reciprocal ST depressions, subtle Wellens syndrome, and lethal ventricular arrhythmias.").forStudents(false).build(),
                Course.builder().id("cme-ards-102").title("Mechanical Ventilation in ARDS & COVID-19").category("Critical Care").duration("8 Hours").cmePoints(8).summary("Harvard Medical School protocol: Lung-protective ventilation, driving pressure titration, prone positioning, and weaning criteria in severe ARDS.").forStudents(false).build(),
                Course.builder().id("cme-sepsis-103").title("Emergency Sepsis Resuscitation Protocols").category("Emergency & Trauma").duration("5 Hours").cmePoints(6).summary("Surviving Sepsis Campaign 2024 / Johns Hopkins: 1-hour resuscitation bundle, dynamic lactate clearance, and norepinephrine titration.").forStudents(false).build(),
                Course.builder().id("cme-ams-104").title("Antimicrobial Stewardship in Hospital Inpatients").category("Clinical Pharmacology").duration("6 Hours").cmePoints(5).summary("AIIMS New Delhi / ICMR: Rational empiric prescribing, de-escalation strategies, and combating carbapenem-resistant enterobacteriaceae (CRE).").forStudents(false).build(),
                Course.builder().id("cme-acls-201").title("AHA Advanced Cardiovascular Life Support (ACLS) Core").category("Nursing Essentials").duration("8 Hours").cmePoints(8).summary("AHA & Indian Resuscitation Council: High-quality resuscitation, shockable rhythm defibrillation, post-cardiac arrest ROSC care, and Code Blue team dynamics.").forStudents(false).build(),
                Course.builder().id("cme-hemo-202").title("ICU Hemodynamic Monitoring & Arterial Line Care").category("Critical Care").duration("6 Hours").cmePoints(6).summary("King’s College London Critical Care: Arterial line cannulation maintenance, damping coefficient tests, CVP wave analysis, and vasoactive titration.").forStudents(false).build(),
                Course.builder().id("cme-clabsi-203").title("NABH Central Line (CLABSI) Infection Prevention").category("Nursing Essentials").duration("4 Hours").cmePoints(4).summary("NABH 5th Edition & CDC Guidelines: Maximum sterile barrier precautions, chlorhexidine dressing protocols, and daily central line necessity audits.").forStudents(false).build(),
                Course.builder().id("cme-pals-204").title("Pediatric Advanced Life Support (PALS) Triage").category("Emergency & Trauma").duration("7 Hours").cmePoints(7).summary("Stanford Children’s Health: Pediatric Glasgow Coma Scale, rapid decompensation detection, weight-based fluid resuscitation (Broselow tape), and septic shock.").forStudents(false).build(),
                Course.builder().id("cme-lasa-301").title("High-Alert Medication Safety & LASA Dispensing").category("Clinical Pharmacology").duration("4 Hours").cmePoints(4).summary("ISMP International Standards: Tall Man lettering, independent double checks, and mitigation of Look-Alike Sound-Alike drug dispensing errors.").forStudents(false).build(),
                Course.builder().id("cme-chemo-302").title("Chemotherapy Admixture & Cytotoxic Safety").category("Clinical Pharmacology").duration("6 Hours").cmePoints(6).summary("Tata Memorial Centre & WHO: Class II Type B2 Biological Safety Cabinets (BSC), hazardous drug spill kits, and extravasation management.").forStudents(false).build(),
                Course.builder().id("cme-tdm-303").title("Clinical Pharmacokinetics & Therapeutic Drug Monitoring").category("Clinical Pharmacology").duration("5 Hours").cmePoints(5).summary("Manipal Pharmacy / AIIMS: Therapeutic drug monitoring of Vancomycin, Aminoglycosides, and Digoxin, targeting AUC/MIC and peak-trough kinetics.").forStudents(false).build(),
                Course.builder().id("cme-fistula-401").title("Hemodialysis Vascular Access & Fistula Complications").category("Dialysis & Renal").duration("5 Hours").cmePoints(5).summary("Johns Hopkins Nephrology: Cannulation buttonhole technique, stenosis detection, thrombosis prevention, and management of AV fistula venous rupture.").forStudents(false).build(),
                Course.builder().id("cme-ro-402").title("Water Treatment & Endotoxin Standards in Hemodialysis").category("Dialysis & Renal").duration("4 Hours").cmePoints(4).summary("AAMI & ISO 23500 Standards: Reverse Osmosis (RO) maintenance, chlorine breakthrough testing, microbial colony counting, and endotoxin LAL testing.").forStudents(false).build(),
                Course.builder().id("cme-crrt-403").title("CRRT Machine Priming, Anticoagulation & Troubleshooting").category("Dialysis & Renal").duration("6 Hours").cmePoints(6).summary("Cleveland Clinic Renal: Continuous Veno-Venous Hemodiafiltration (CVVHDF), regional citrate anticoagulation (RCA), and trans-membrane pressure (TMP) alarms.").forStudents(false).build(),
                Course.builder().id("cme-icu-rehab-501").title("Early ICU Mobilization & Post-Extubation Pulmonary Rehab").category("Physiotherapy & Rehab").duration("6 Hours").cmePoints(6).summary("Johns Hopkins Physical Medicine: ABCDEF ICU liberation bundle, passive cycling, tilt-table mobilization, and lung expansion inspiratory muscle training.").forStudents(false).build(),
                Course.builder().id("cme-tkr-502").title("Post-Operative TKR & THR Accelerated Mobilization").category("Physiotherapy & Rehab").duration("5 Hours").cmePoints(5).summary("Oxford NHS Trust: Day-0 knee flexion/extension milestones, gait retraining, quad lag correction, and DVT prophylaxis mechanical protocols.").forStudents(false).build(),
                Course.builder().id("cme-stroke-503").title("Neuro-Developmental Therapy in Acute Stroke Rehab").category("Physiotherapy & Rehab").duration("7 Hours").cmePoints(7).summary("NIMHANS Bengaluru: Bobath concept, trunk postural stability, constraint-induced movement therapy (CIMT), and spasticity inhibition techniques.").forStudents(false).build(),
                Course.builder().id("cme-trauma-ct-601").title("Multi-Slice CT Trauma Protocols & Contrast Safety").category("Radiology & Imaging").duration("5 Hours").cmePoints(5).summary("RSNA Radiology: Whole-body polytrauma CT protocols, IV contrast extravasation protocols, and anaphylactoid reaction emergency management.").forStudents(false).build(),
                Course.builder().id("cme-aerb-602").title("Radiation Safety & AERB Compliance in Cath Labs").category("Radiology & Imaging").duration("4 Hours").cmePoints(4).summary("Atomic Energy Regulatory Board (AERB) & CMC Vellore: ALARA principles, lead apron shielding inspection, collimation control, and TLD badge dosimetry.").forStudents(false).build(),
                Course.builder().id("cme-bls-701").title("Foundational BLS & High-Quality Chest Compressions").category("Student Essentials").duration("3 Hours").cmePoints(3).summary("AHA Standardized Basic Life Support: Adult and pediatric CPR, AED pad placement, relief of choking, and clinical logbook certified.").forStudents(true).build(),
                Course.builder().id("cme-bmw-702").title("Hospital Hand Hygiene & Biomedical Waste Management").category("Quality & NABH").duration("4 Hours").cmePoints(4).summary("WHO 5 Moments of Hand Hygiene & NABH BMW Rules 2016: Color-coded bin segregation (Yellow, Red, White, Blue), sharp disposal, and audit logging.").forStudents(true).build(),
                Course.builder().id("cme-sbar-703").title("Clinical Empathy & SBAR Emergency Handover Protocols").category("Student Essentials").duration("3 Hours").cmePoints(3).summary("Harvard Macy Institute: Situation-Background-Assessment-Recommendation (SBAR) handover, bedside communication with anxious families, and escalation.").forStudents(true).build()
            );
            courseRepository.saveAll(defaults);
            log.info("Successfully populated {} accredited CME courses.", defaults.size());
        }
    }

    @Transactional(readOnly = true)
    public List<CourseDto> getCourses(CustomUserPrincipal principal) {
        List<Course> allCourses = courseRepository.findAll();

        Map<String, CourseEnrollment> userEnrollments = (principal != null)
                ? enrollmentRepository.findByUserId(principal.getId()).stream()
                .collect(Collectors.toMap(CourseEnrollment::getCourseId, e -> e))
                : Map.of();

        return allCourses.stream()
                .map(course -> {
                    CourseEnrollment enrollment = userEnrollments.get(course.getId());
                    return CourseDto.builder()
                            .id(course.getId())
                            .title(course.getTitle())
                            .category(course.getCategory())
                            .duration(course.getDuration())
                            .cmePoints(course.getCmePoints())
                            .summary(course.getSummary())
                            .forStudents(course.isForStudents())
                            .enrolled(enrollment != null)
                            .progressPercent(enrollment != null ? enrollment.getProgressPercent() : 0)
                            .completed(enrollment != null && enrollment.isCompleted())
                            .completedAt(enrollment != null ? enrollment.getCompletedAt() : null)
                            .certificateRef(enrollment != null ? enrollment.getCertificateRef() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CourseDto enroll(CustomUserPrincipal principal, String courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        CourseEnrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(principal.getId(), courseId)
                .orElseGet(() -> {
                    CourseEnrollment newEnrollment = CourseEnrollment.builder()
                            .userId(principal.getId())
                            .courseId(courseId)
                            .progressPercent(0)
                            .completed(false)
                            .build();
                    return enrollmentRepository.save(newEnrollment);
                });

        auditLogService.logEvent(principal.getId(), "COURSE_ENROLLED", "CourseEnrollment", enrollment.getId().toString(), null);

        return CourseDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .category(course.getCategory())
                .duration(course.getDuration())
                .cmePoints(course.getCmePoints())
                .summary(course.getSummary())
                .forStudents(course.isForStudents())
                .enrolled(true)
                .progressPercent(enrollment.getProgressPercent())
                .completed(enrollment.isCompleted())
                .completedAt(enrollment.getCompletedAt())
                .certificateRef(enrollment.getCertificateRef())
                .build();
    }

    @Transactional
    public CourseDto updateProgress(CustomUserPrincipal principal, String courseId, int progressPercent) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        CourseEnrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(principal.getId(), courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found. Please enroll first."));

        enrollment.setProgressPercent(progressPercent);
        if (progressPercent >= 100 && !enrollment.isCompleted()) {
            enrollment.setCompleted(true);
            enrollment.setCompletedAt(Instant.now());
            enrollment.setCertificateRef("CME-CERT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            auditLogService.logEvent(principal.getId(), "COURSE_COMPLETED", "CourseEnrollment", enrollment.getId().toString(), null);
        }

        CourseEnrollment saved = enrollmentRepository.save(enrollment);

        return CourseDto.builder()
                .id(course.getId())
                .title(course.getTitle())
                .category(course.getCategory())
                .duration(course.getDuration())
                .cmePoints(course.getCmePoints())
                .summary(course.getSummary())
                .forStudents(course.isForStudents())
                .enrolled(true)
                .progressPercent(saved.getProgressPercent())
                .completed(saved.isCompleted())
                .completedAt(saved.getCompletedAt())
                .certificateRef(saved.getCertificateRef())
                .build();
    }
}
