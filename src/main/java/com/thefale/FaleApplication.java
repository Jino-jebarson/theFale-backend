package com.thefale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaleApplication.class, args);
    }
}
