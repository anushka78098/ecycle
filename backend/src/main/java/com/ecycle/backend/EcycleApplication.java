package com.ecycle.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.ecycle.backend.repository")
@EnableMongoRepositories(basePackages = "com.ecycle.backend.mongo")
public class EcycleApplication {
    public static void main(String[] args) { SpringApplication.run(EcycleApplication.class, args); }
}
