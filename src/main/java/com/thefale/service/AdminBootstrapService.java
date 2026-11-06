package com.thefale.service;

import com.thefale.entity.User;
import com.thefale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBootstrapService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(String... args) {
        String adminEmail = environment.getProperty("ADMIN_INITIAL_EMAIL");
        if (adminEmail == null || adminEmail.isBlank()) {
            boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            if (isDev) {
                adminEmail = "admin@thefale.com";
            }
        }

        String adminPassword = environment.getProperty("ADMIN_INITIAL_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = System.getenv("ADMIN_INITIAL_PASSWORD");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
            if (isDev) {
                adminPassword = "admin123";
            }
        }

        if (adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
            String cleanEmail = adminEmail.toLowerCase().trim();
            if (userRepository.findByEmail(cleanEmail).isEmpty()) {
                User rootAdmin = User.builder()
                        .email(cleanEmail)
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .role("ROLE_ADMIN")
                        .mfaEnabled(false)
                        .status("ACTIVE")
                        .isOnboarded(true)
                        .build();
                userRepository.save(rootAdmin);
                log.info("[ADMIN BOOTSTRAP] Initial administrator account created securely for: {}", cleanEmail);
            } else {
                log.info("[ADMIN BOOTSTRAP] Administrator account already exists for: {}", cleanEmail);
            }
        }
    }
}

