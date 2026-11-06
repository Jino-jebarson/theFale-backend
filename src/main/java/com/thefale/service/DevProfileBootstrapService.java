package com.thefale.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("dev")
public class DevProfileBootstrapService implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("[DEV PROFILE BOOTSTRAP] Sample data seeding disabled. Running in dynamic clean mode.");
    }
}
