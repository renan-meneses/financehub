package com.financehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinanceHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceHubApplication.class, args);
    }
}
