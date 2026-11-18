package com.mastek.pulse.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: UserEntity.java
 * Journey: Shared Identity, Interests, and Scoped Roles across Mobile, Web & Portal.
 *
 * Why it is designed this way:
 *   Maps to the `users` table in `V1__init_schema.sql`. Notice how we store only
 *   minimum opt-in profile fields (region, function, explicit interests, roles,
 *   and host availability) in strict alignment with Proposal Page 4 ("Minimum
 *   collection: no private-message ingestion or employee appraisal scores").
 *   Roles (`roles_csv`) and topic interests (`interests_csv`) are stored as compact
 *   comma-separated strings in SQL and parsed into clean immutable Java `List<String>`
 *   collections via `getRolesList()` and `getInterestsList()`.
 *
 * JPA Annotations Explained for Interns:
 *   - `@Entity`: Marks this class as a persistent JPA entity managed by Hibernate.
 *   - `@Table(name = "users")`: Maps this class to the `users` table in PostgreSQL.
 *   - `@Id`: Designates `id` (`users.id`) as the primary key.
 *   - `@Column(...)`: Maps each Java field to its database column with `nullable`,
 *     `unique`, and `length` constraints matching `V1__init_schema.sql`.
 * ============================================================================
 */
@Entity
@Table(name = "users")
public class UserEntity {

    /**
     * Unique user identifier (e.g., `"usr_asha"`, `"usr_daniel"`, `"usr_priya"`).
     * Mapped to `id VARCHAR(64) NOT NULL PRIMARY KEY`.
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * Full display name of the employee (e.g., `"Asha Patel"`).
     * Mapped to `display_name VARCHAR(120) NOT NULL`.
     */
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * Unique work email address of the employee (e.g., `"asha.patel@mastek.com"`).
     * Mapped to `email VARCHAR(160) NOT NULL UNIQUE`.
     */
    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    /**
     * Geographic region code used for audience scoping and Saathi buddy matching
     * (e.g., `"UK"`, `"IN"`, `"US"`).
     * Mapped to `region VARCHAR(32) NOT NULL`.
     */
    @Column(name = "region", nullable = false, length = 32)
    private String region;

    /**
     * Organizational function or practice name (e.g., `"Engineering"`, `"Delivery"`,
     * `"People & Culture"`).
     * Mapped to `function_name VARCHAR(64) NOT NULL`.
     */
    @Column(name = "function_name", nullable = false, length = 64)
    private String functionName;

    /**
     * Comma-separated list of role codes assigned to this user (e.g.,
     * `"EMPLOYEE,CREATOR"` or `"EMPLOYEE,REVIEWER,PUBLISHER,ADMIN"`).
     * Mapped to `roles_csv VARCHAR(255) NOT NULL`.
     */
    @Column(name = "roles_csv", nullable = false, length = 255)
    private String rolesCsv;

    /**
     * Comma-separated list of opt-in topic interests chosen by the employee
     * (e.g., `"Cloud Architecture,AI Delivery,Mentoring"`). Used to rank stories
     * AFTER filtering by `audience_id`.
     * Mapped to `interests_csv VARCHAR(500) NOT NULL`.
     */
    @Column(name = "interests_csv", nullable = false, length = 500)
    private String interestsCsv;

    /**
     * Employee's preferred notification cadence (`"DAILY_DIGEST"`, `"WEEKLY_DIGEST"`,
     * or `"IMMEDIATE"`). Defaults to `"DAILY_DIGEST"` to prevent notification fatigue.
     * Mapped to `notification_frequency VARCHAR(32) NOT NULL`.
     */
    @Column(name = "notification_frequency", nullable = false, length = 32)
    private String notificationFrequency = "DAILY_DIGEST";

    /**
     * ISO language preference code (defaults to `"en"`).
     * Mapped to `preferred_language VARCHAR(16) NOT NULL`.
     */
    @Column(name = "preferred_language", nullable = false, length = 16)
    private String preferredLanguage = "en";

    /**
     * Whether this employee has paused their availability to host 15-minute
     * topic-based conversations (Proposal Page 12: "let hosts pause availability").
     * When `true`, new connection requests to this host's invitations are blocked with `409 Conflict`.
     * Mapped to `host_paused BOOLEAN NOT NULL`.
     */
    @Column(name = "host_paused", nullable = false)
    private boolean hostPaused = false;

    /**
     * Target weekly time budget (in minutes) the host is willing to spend on peer
     * conversations (defaults to `25` minutes per Proposal Page 5 workload sizing).
     * Mapped to `weekly_host_minutes INT NOT NULL`.
     */
    @Column(name = "weekly_host_minutes", nullable = false)
    private int weeklyHostMinutes = 25;

    /**
     * Parses `rolesCsv` into an immutable `List<String>` of trimmed, non-empty role codes.
     *
     * @return List of role strings (e.g., `["EMPLOYEE", "REVIEWER"]`), or empty list if blank.
     */
    public List<String> getRolesList() {
        // Return an empty list if rolesCsv is null or whitespace-only.
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return List.of();
        }
        // Split on commas, trim whitespace around each token, discard empty strings, and collect.
        return Arrays.stream(rolesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Convenience helper that checks whether this user holds the specified role code.
     *
     * @param role Role name to check (e.g., `"REVIEWER"`, `"PUBLISHER"`, `"MODERATOR"`, `"ADMIN"`).
     * @return `true` if `role` is present in `getRolesList()`, `false` otherwise.
     */
    public boolean hasRole(String role) {
        return getRolesList().contains(role);
    }

    /**
     * Parses `interestsCsv` into an immutable `List<String>` of trimmed, non-empty topic tags.
     *
     * @return List of explicit interest tags used for feed ranking after audience filtering.
     */
    public List<String> getInterestsList() {
        // Return an empty list if interestsCsv is null or whitespace-only.
        if (interestsCsv == null || interestsCsv.isBlank()) {
            return List.of();
        }
        // Split on commas, trim whitespace, filter out empty entries, and return an unmodifiable list.
        return Arrays.stream(interestsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // Standard Getters & Setters
    /** @return Unique user ID (`users.id`). */
    public String getId() { return id; }
    /** @param id Unique user ID to set. */
    public void setId(String id) { this.id = id; }

    /** @return Employee's display name. */
    public String getDisplayName() { return displayName; }
    /** @param displayName Employee's display name to set. */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /** @return Employee's unique email address. */
    public String getEmail() { return email; }
    /** @param email Employee's email address to set. */
    public void setEmail(String email) { this.email = email; }

    /** @return Geographic region code (`"UK"`, `"IN"`, etc.). */
    public String getRegion() { return region; }
    /** @param region Geographic region code to set. */
    public void setRegion(String region) { this.region = region; }

    /** @return Organizational function name. */
    public String getFunctionName() { return functionName; }
    /** @param functionName Organizational function name to set. */
    public void setFunctionName(String functionName) { this.functionName = functionName; }

    /** @return Raw comma-separated roles string. */
    public String getRolesCsv() { return rolesCsv; }
    /** @param rolesCsv Raw comma-separated roles string to set. */
    public void setRolesCsv(String rolesCsv) { this.rolesCsv = rolesCsv; }

    /** @return Raw comma-separated interests string. */
    public String getInterestsCsv() { return interestsCsv; }
    /** @param interestsCsv Raw comma-separated interests string to set. */
    public void setInterestsCsv(String interestsCsv) { this.interestsCsv = interestsCsv; }

    /** @return Notification digest frequency (`"DAILY_DIGEST"`, `"WEEKLY_DIGEST"`, `"IMMEDIATE"`). */
    public String getNotificationFrequency() { return notificationFrequency; }
    /** @param notificationFrequency Notification digest frequency to set. */
    public void setNotificationFrequency(String notificationFrequency) { this.notificationFrequency = notificationFrequency; }

    /** @return Preferred ISO language code. */
    public String getPreferredLanguage() { return preferredLanguage; }
    /** @param preferredLanguage Preferred ISO language code to set. */
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    /** @return `true` if the user has paused 15-minute host availability. */
    public boolean isHostPaused() { return hostPaused; }
    /** @param hostPaused `true` to pause incoming 15-minute connection requests. */
    public void setHostPaused(boolean hostPaused) { this.hostPaused = hostPaused; }

    /** @return Weekly host time budget in minutes. */
    public int getWeeklyHostMinutes() { return weeklyHostMinutes; }
    /** @param weeklyHostMinutes Weekly host time budget in minutes to set. */
    public void setWeeklyHostMinutes(int weeklyHostMinutes) { this.weeklyHostMinutes = weeklyHostMinutes; }
}
