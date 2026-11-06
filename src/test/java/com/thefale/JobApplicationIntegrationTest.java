package com.thefale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.CourseProgressUpdateRequest;
import com.thefale.dto.CreateSupportTicketRequest;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.JobPosting;
import com.thefale.entity.User;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.JobPostingRepository;
import com.thefale.repository.UserRepository;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.security.JwtTokenProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class JobApplicationIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private FacilityProfileRepository facilityProfileRepository;

        @Autowired
        private CandidateProfileRepository candidateProfileRepository;

        @Autowired
        private JobPostingRepository jobPostingRepository;

        @Autowired
        private com.thefale.repository.JobApplicationRepository jobApplicationRepository;

        @Autowired
        private com.thefale.repository.SavedJobRepository savedJobRepository;

        @Autowired
        private com.thefale.repository.CourseEnrollmentRepository courseEnrollmentRepository;

        @Autowired
        private com.thefale.repository.NotificationRepository notificationRepository;

        @Autowired
        private com.thefale.repository.SupportTicketRepository supportTicketRepository;

        @Autowired
        private com.thefale.repository.AuditEventRepository auditEventRepository;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private User studentUser;
        private User graduateUser;
        private CandidateProfile studentCandidate;
        private CandidateProfile graduateCandidate;
        private JobPosting permanentJob;
        private JobPosting internshipJob;
        private String studentToken;
        private String graduateToken;

        @BeforeEach
        void setUp() {
                jobApplicationRepository.deleteAll();
                savedJobRepository.deleteAll();
                courseEnrollmentRepository.deleteAll();
                notificationRepository.deleteAll();
                supportTicketRepository.deleteAll();
                auditEventRepository.deleteAll();
                jobPostingRepository.deleteAll();
                candidateProfileRepository.deleteAll();
                facilityProfileRepository.deleteAll();
                userRepository.deleteAll();

                String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

                // Create Facility
                User facilityUser = userRepository.save(User.builder()
                                .phone("+91" + salt.substring(0, 4) + "0001")
                                .role("ROLE_FACILITY")
                                .status("ACTIVE")
                                .isOnboarded(true)
                                .build());

                FacilityProfile facility = facilityProfileRepository.save(FacilityProfile.builder()
                                .userId(facilityUser.getId())
                                .hospitalName("Manipal Super Specialty Hospital")
                                .facilityType("Multi-Specialty")
                                .city("Bengaluru")
                                .state("Karnataka")
                                .beds(350)
                                .licenseNumber("KA-HOSP-2023-9912")
                                .contactPerson("Dr. Rajesh Kumar")
                                .contactPhone("+919876543210")
                                .contactEmail("admin@manipal.org")
                                .accreditationLevel("NABH Accredited")
                                .kycStatus("VERIFIED")
                                .build());

                // Create Permanent Job
                permanentJob = jobPostingRepository.save(JobPosting.builder()
                                .facilityId(facility.getId())
                                .title("Senior Consultant Cardiologist")
                                .department("Cardiology")
                                .employmentType("Full-time")
                                .internship(false)
                                .city("Bengaluru")
                                .openings(2)
                                .salaryMin(BigDecimal.valueOf(150000.00))
                                .salaryMax(BigDecimal.valueOf(250000.00))
                                .description("Looking for experienced DM/DNB cardiologist.")
                                .status("OPEN")
                                .build());

                // Create Internship Job
                internshipJob = jobPostingRepository.save(JobPosting.builder()
                                .facilityId(facility.getId())
                                .title("Clinical Internship — Emergency & General Ward")
                                .department("Emergency")
                                .employmentType("Internship")
                                .internship(true)
                                .city("Bengaluru")
                                .openings(5)
                                .salaryMin(BigDecimal.valueOf(25000.00))
                                .salaryMax(BigDecimal.valueOf(35000.00))
                                .description("Hands-on clinical internship under senior attending physicians.")
                                .status("OPEN")
                                .build());

                // Create Student Candidate
                studentUser = userRepository.save(User.builder()
                                .phone("+91" + salt.substring(0, 4) + "0002")
                                .role("ROLE_CANDIDATE")
                                .candidateLevel("STUDENT")
                                .status("ACTIVE")
                                .isOnboarded(true)
                                .build());

                studentCandidate = candidateProfileRepository.save(CandidateProfile.builder()
                                .userId(studentUser.getId())
                                .name("Karthik S (Student)")
                                .city("Bengaluru")
                                .college("Bangalore Medical College")
                                .course("MBBS")
                                .studyYear("4th Year")
                                .expectedGraduationYear(2027)
                                .build());

                studentToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                                .id(studentUser.getId())
                                .phone(studentUser.getPhone())
                                .role(studentUser.getRole())
                                .candidateLevel(studentUser.getCandidateLevel())
                                .enabled(true)
                                .build());

                // Create Graduate Candidate
                graduateUser = userRepository.save(User.builder()
                                .phone("+91" + salt.substring(0, 4) + "0003")
                                .role("ROLE_CANDIDATE")
                                .candidateLevel("PROFESSIONAL")
                                .status("ACTIVE")
                                .isOnboarded(true)
                                .build());

                graduateCandidate = candidateProfileRepository.save(CandidateProfile.builder()
                                .userId(graduateUser.getId())
                                .name("Dr. Meera Rao")
                                .city("Bengaluru")
                                .profession("Doctor / General Physician")
                                .specialty("Cardiology")
                                .qualification("MBBS, MD")
                                .council("Karnataka Medical Council")
                                .regNumber("KMC-2015-1102")
                                .build());

                graduateToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                                .id(graduateUser.getId())
                                .phone(graduateUser.getPhone())
                                .role(graduateUser.getRole())
                                .candidateLevel(graduateUser.getCandidateLevel())
                                .enabled(true)
                                .build());
        }

        @Test
        @DisplayName("Student Persona: Job feed filters to internships only")
        void testStudentFeedFilter() throws Exception {
                // Graduate sees both permanent and internship
                mockMvc.perform(get("/api/v1/jobs")
                                .header("Authorization", "Bearer " + graduateToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data", hasSize(2)));

                // Student sees ONLY internship postings
                mockMvc.perform(get("/api/v1/jobs")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data", hasSize(1)))
                                .andExpect(jsonPath("$.data[0].id").value(internshipJob.getId().toString()))
                                .andExpect(jsonPath("$.data[0].isInternship").value(true));
        }

        @Test
        @DisplayName("Student Guard: 403 Forbidden when student attempts to apply for permanent role")
        void testStudentBarredFromPermanentRole() throws Exception {
                mockMvc.perform(post("/api/v1/jobs/" + permanentJob.getId() + "/apply")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error").value("Forbidden"))
                                .andExpect(jsonPath("$.message").value(
                                                "Healthcare students cannot apply for permanent clinical roles. Please select internship postings."));
        }

        @Test
        @DisplayName("Graduate Application & Duplicate Prevention")
        void testGraduateApplicationWorkflow() throws Exception {
                // 1. Save Job
                mockMvc.perform(post("/api/v1/jobs/" + permanentJob.getId() + "/save")
                                .header("Authorization", "Bearer " + graduateToken))
                                .andExpect(status().isOk());

                // 2. Apply for Job
                mockMvc.perform(post("/api/v1/jobs/" + permanentJob.getId() + "/apply")
                                .header("Authorization", "Bearer " + graduateToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.jobTitle").value("Senior Consultant Cardiologist"))
                                .andExpect(jsonPath("$.data.status").value("UNDER_ADMIN_SCREENING"));

                // 3. Duplicate application returns 409 Conflict
                mockMvc.perform(post("/api/v1/jobs/" + permanentJob.getId() + "/apply")
                                .header("Authorization", "Bearer " + graduateToken))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").value("Conflict"));

                // 4. View candidate applications
                mockMvc.perform(get("/api/v1/candidate/applications")
                                .header("Authorization", "Bearer " + graduateToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data", hasSize(1)))
                                .andExpect(jsonPath("$.data[0].jobTitle").value("Senior Consultant Cardiologist"));
        }

        @Test
        @DisplayName("CME Learning, Progress Tracking, and Certificate Generation")
        void testCmeLearningWorkflow() throws Exception {
                String courseId = "cme-bls-101";

                // 1. Get courses
                mockMvc.perform(get("/api/v1/courses")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isNotEmpty());

                // 2. Enroll
                mockMvc.perform(post("/api/v1/courses/" + courseId + "/enroll")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.enrolled").value(true))
                                .andExpect(jsonPath("$.data.progressPercent").value(0));

                // 3. Update progress to 100% -> generates certificate
                CourseProgressUpdateRequest progressReq = CourseProgressUpdateRequest.builder()
                                .progressPercent(100)
                                .build();

                mockMvc.perform(post("/api/v1/courses/" + courseId + "/progress")
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(progressReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.completed").value(true))
                                .andExpect(jsonPath("$.data.certificateRef").isNotEmpty());
        }

        @Test
        @DisplayName("Support Tickets & Notifications")
        void testSupportAndNotifications() throws Exception {
                // 1. Submit Support Ticket
                CreateSupportTicketRequest ticketReq = CreateSupportTicketRequest.builder()
                                .subject("Query regarding KYC council verification timeline")
                                .category("Verification")
                                .description("Uploaded Telangana State Medical Council certificate yesterday. When will it be approved?")
                                .build();

                mockMvc.perform(post("/api/v1/support/tickets")
                                .header("Authorization", "Bearer " + graduateToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(ticketReq)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.subject")
                                                .value("Query regarding KYC council verification timeline"))
                                .andExpect(jsonPath("$.data.status").value("OPEN"));

                // 2. Get my tickets
                mockMvc.perform(get("/api/v1/support/tickets")
                                .header("Authorization", "Bearer " + graduateToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data", hasSize(1)));

                // 3. Get Notifications (application submission auto-generates notification)
                mockMvc.perform(post("/api/v1/jobs/" + internshipJob.getId() + "/apply")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/notifications")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data", hasSize(1)))
                                .andExpect(jsonPath("$.data[0].title")
                                                .value("Application Submitted — Screening in Progress"));

                // 4. Mark all notifications read
                mockMvc.perform(patch("/api/v1/notifications/read-all")
                                .header("Authorization", "Bearer " + studentToken))
                                .andExpect(status().isOk());
        }
}
