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
 * Journey: Foundation for all Mastek Pulse / Campfire clients:
 *   1. Mobile App (Android & iOS via React Native / Expo)
 *   2. Employee Web Experience (React 18 + TypeScript + Vite)
 *   3. Role-Based Publishing & Moderation Portal (React 18 + TypeScript + Vite)
 *
 * Architectural Pattern ("Three clients. One core." — Proposal Page 14):
 *   Mastek Campfire is built as a **Spring Boot 3.3+ Modular Monolith** powered by
 *   Java 21 Virtual Threads (`spring.threads.virtual.enabled=true` in `application.yml`).
 *   Instead of splitting into fragile microservices during the pilot phase, all domain
 *   modules (`common`, `content`, `listen`, `connection`, `recognition`, `saathi`,
 *   `ai`, `outbox`) run inside a single deployable JVM process with strict package
 *   boundaries. This gives us transactional ACID guarantees across database tables
 *   while scaling to 10,000+ concurrent employees.
 *
 * Key Annotations Explained for Interns:
 *   - `@SpringBootApplication`: A meta-annotation that combines three core Spring features:
 *       1. `@Configuration`: Marks this class as a source of Spring Bean definitions.
 *       2. `@EnableAutoConfiguration`: Tells Spring Boot to automatically configure
 *          DataSource, Hibernate/JPA, Flyway migrations, Jackson JSON serialization,
 *          and embedded Tomcat based on the JAR dependencies in `pom.xml`.
 *       3. `@ComponentScan`: Instructs Spring to scan `com.mastek.pulse` and all of its
 *          sub-packages (`ai`, `common`, `content`, `listen`, `connection`, `recognition`,
 *          `saathi`, `outbox`) to discover `@Service`, `@RestController`, `@Repository`,
 *          and `@Component` classes.
 *   - `@EnableScheduling`: Activates Spring's background task scheduler so methods
 *     annotated with `@Scheduled` (such as our Transactional Outbox poller in the
 *     `outbox` package) run periodically on background threads to publish scheduled
 *     stories and dispatch notifications reliably.
 *   - `@EnableCaching`: Activates Spring's caching abstraction (`@Cacheable`, `@CacheEvict`)
 *     so high-read commute endpoints (such as Mastek Radio queues and Culture Cast
 *     episodes) can be served from memory (or Redis at 10k scale) without hitting
 *     PostgreSQL on every request.
 * ============================================================================
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PulseApplication {

    /**
     * Application entry point invoked by the Java Virtual Machine (JVM) at startup.
     *
     * How it works step-by-step:
     *   1. `SpringApplication.run(...)` bootstraps the Spring IoC (Inversion of Control)
     *      ApplicationContext.
     *   2. Before any HTTP traffic is accepted, Flyway automatically executes our
     *      versioned SQL migrations (`V1__init_schema.sql`, `V2__seed_pilot_data.sql`,
     *      `V3__campfire_recognition_and_saathi.sql`) to ensure the database schema
     *      and pilot seed data are up to date.
     *   3. Embedded Tomcat starts listening on port 8080 (or `SERVER_PORT` environment
     *      variable) and routes incoming REST API calls under `/api/v1/**` to our controllers.
     *
     * @param args Command-line arguments passed to the JVM (e.g., `--spring.profiles.active=postgres`).
     */
    public static void main(String[] args) {
        // Bootstraps the Spring Boot application context, runs Flyway migrations,
        // and starts the embedded Tomcat HTTP server.
        SpringApplication.run(PulseApplication.class, args);
    }
}
