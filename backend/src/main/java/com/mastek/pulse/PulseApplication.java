package com.mastek.pulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PulseApplication.java
 * Journey: Foundation for all Mastek Pulse clients (Android, iOS, Employee Web,
 *          and Role-Based Publishing Portal).
 * Why it is designed this way:
 *   1. Modular Monolith ("Three clients. One core." - Proposal Page 14):
 *      All domain modules (content, listen, connection, ai, outbox) share one
 *      Spring Boot deployment for simplicity in the POC while maintaining strict
 *      package boundaries so it scales cleanly to 10,000+ employees.
 *   2. @EnableScheduling powers our durable Transactional Outbox worker (which
 *      processes scheduled publications and notifications asynchronously).
 *   3. @EnableCaching activates Caffeine (or Redis in 10k-scale prod) for
 *      high-read commute endpoints like Mastek Radio and Culture Cast.
 * ============================================================================
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseApplication.class, args);
    }
}
