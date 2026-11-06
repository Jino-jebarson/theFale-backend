package com.thefale;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify that all 28 production tables are created cleanly by Flyway")
    void testAllTablesCreated() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class
        );

        assertThat(tables).contains(
                "audit_events",
                "candidate_documents",
                "candidate_level_history",
                "candidate_payout_methods",
                "candidate_profiles",
                "course_enrollments",
                "courses",
                "facility_profiles",
                "facility_wallets",
                "hospital_bank_accounts",
                "huddle_answers",
                "huddle_comments",
                "huddle_flags",
                "huddle_posts",
                "huddle_questions",
                "idempotency_keys",
                "job_applications",
                "job_postings",
                "locum_shifts",
                "minimum_role_rates",
                "notifications",
                "otp_verifications",
                "platform_settings",
                "refresh_tokens",
                "saved_jobs",
                "shift_claims",
                "student_upgrade_requests",
                "support_tickets",
                "unlocked_candidates",
                "users",
                "wallet_transactions"
        );

        // Verify seeded platform settings
        Integer settingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_settings",
                Integer.class
        );
        assertThat(settingCount).isGreaterThanOrEqualTo(3);

        // Verify seeded statutory minimum role rates
        Integer roleRatesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM minimum_role_rates",
                Integer.class
        );
        assertThat(roleRatesCount).isGreaterThanOrEqualTo(9);
    }
}
