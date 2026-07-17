package com.penmate.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.penmate.backend.infrastructure.persistence")
public class PenmateBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PenmateBackendApplication.class, args);
    }
}

