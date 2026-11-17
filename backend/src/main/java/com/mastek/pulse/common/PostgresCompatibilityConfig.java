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
 * Why it is designed this way:
 *   In AI-DLC, we never rewrite already-committed Flyway migrations (`V1` and `V2`).
 *   `V2__seed_pilot_data.sql` uses `DATEADD('HOUR', -6, CURRENT_TIMESTAMP)` which
 *   is native in H2 (`MODE=PostgreSQL`), while native PostgreSQL 16 uses interval
 *   arithmetic. This strategy registers a tiny PL/pgSQL `dateadd` function on
 *   PostgreSQL connections right before Flyway runs, allowing `V1`, `V2`, and `V3`
 *   to execute identically on real PostgreSQL 16 without modifying `V1` or `V2`!
 * ============================================================================
 */
@Configuration
public class PostgresCompatibilityConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            try (Connection conn = dataSource.getConnection()) {
                String url = conn.getMetaData().getURL();
                if (url != null && url.startsWith("jdbc:postgresql:")) {
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
                // Non-PostgreSQL or already defined
            }
            flyway.migrate();
        };
    }
}
