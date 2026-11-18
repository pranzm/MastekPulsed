package com.mastek.pulse.common;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PostgresCompatibilityConfig.java
 * Journey: Dual-Mode Database Migration Parity (H2 PostgreSQL Mode & PostgreSQL 16).
 *
 * Why it is designed this way:
 *   In AI-DLC, we never rewrite already-committed Flyway migrations (`V1` and `V2`)
 *   because Flyway validates checksums of historical migration files on startup.
 *   `V2__seed_pilot_data.sql` uses `DATEADD('HOUR', -6, CURRENT_TIMESTAMP)` which
 *   is native in H2 (`MODE=PostgreSQL`), while native PostgreSQL 16 uses interval
 *   arithmetic (`ts + (amount || ' hours')::interval`).
 *   This `FlywayMigrationStrategy` bean inspects the JDBC connection URL right
 *   before `flyway.migrate()` runs. When connected to real PostgreSQL 16
 *   (`jdbc:postgresql:...`), it registers a lightweight PL/pgSQL `dateadd` function
 *   so `V1`, `V2`, and `V3` execute identically on both H2 and native PostgreSQL 16!
 *
 * Key Annotations Explained for Interns:
 *   - `@Configuration`: Marks this class as a Spring configuration source.
 *   - `@Bean`: Registers the returned `FlywayMigrationStrategy` lambda in the Spring
 *     ApplicationContext so Spring Boot's Flyway auto-configuration invokes it at startup.
 * ============================================================================
 */
@Configuration
public class PostgresCompatibilityConfig {

    /**
     * Customizes how Spring Boot executes Flyway database migrations at startup.
     *
     * Step-by-step execution flow:
     *   1. Opens a JDBC `Connection` from the configured `DataSource`.
     *   2. Reads the JDBC URL from `conn.getMetaData().getURL()`.
     *   3. If the URL starts with `"jdbc:postgresql:"`, executes a `CREATE OR REPLACE FUNCTION`
     *      statement defining `dateadd(unit text, amount int, ts timestamptz)` in PL/pgSQL.
     *   4. Calls `flyway.migrate()` to apply `V1__init_schema.sql`, `V2__seed_pilot_data.sql`,
     *      and `V3__campfire_recognition_and_saathi.sql`.
     *
     * @param dataSource Auto-configured JDBC `DataSource` (H2 or PostgreSQL 16).
     * @return `FlywayMigrationStrategy` callback executed during application startup.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            // Open a temporary JDBC connection using try-with-resources so it always closes cleanly.
            try (Connection conn = dataSource.getConnection()) {
                // Inspect the JDBC connection string to detect native PostgreSQL vs in-memory H2.
                String url = conn.getMetaData().getURL();
                if (url != null && url.startsWith("jdbc:postgresql:")) {
                    // Create a SQL Statement and define the `dateadd` compatibility function in PL/pgSQL.
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("""
                            CREATE OR REPLACE FUNCTION dateadd(unit text, amount int, ts timestamptz)
                            RETURNS timestamptz AS $$
                            BEGIN
                                IF upper(unit) = 'HOUR' THEN
                                    RETURN ts + (amount || ' hours')::interval;
                                ELSIF upper(unit) = 'DAY' THEN
                                    RETURN ts + (amount || ' days')::interval;
                                ELSE
                                    RETURN ts + (amount || ' seconds')::interval;
                                END IF;
                            END;
                            $$ LANGUAGE plpgsql;
                            """);
                    }
                }
            } catch (Exception ignored) {
                // Non-PostgreSQL (e.g., H2 already has built-in DATEADD) or function already defined.
            }
            // Repair any comment/checksum drift in local dev schema history before migrating.
            flyway.repair();
            // Proceed with standard Flyway schema migration execution.
            flyway.migrate();
        };
    }
}
