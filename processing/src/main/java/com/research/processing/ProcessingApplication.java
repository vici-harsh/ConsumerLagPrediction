package com.research.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableRedisHttpSession
public class ProcessingApplication {
    public static void main(String[] args) {
        System.out.println("Starting ProcessingApplication...");
        SpringApplication.run(ProcessingApplication.class, args);
    }
}