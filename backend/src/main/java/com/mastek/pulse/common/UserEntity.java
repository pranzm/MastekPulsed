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
 * Why it is designed this way:
 *   Maps to the `users` table in `V1__init_schema.sql`. Notice how we store only
 *   minimum opt-in profile fields (region, function, explicit interests, roles,
 *   and host availability) in strict alignment with Proposal Page 4 ("Minimum
 *   collection: no private-message ingestion or employee appraisal scores").
 * ============================================================================
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "function_name", nullable = false, length = 64)
    private String functionName;

    @Column(name = "roles_csv", nullable = false, length = 255)
    private String rolesCsv;

    @Column(name = "interests_csv", nullable = false, length = 500)
    private String interestsCsv;

    @Column(name = "notification_frequency", nullable = false, length = 32)
    private String notificationFrequency = "DAILY_DIGEST";

    @Column(name = "preferred_language", nullable = false, length = 16)
    private String preferredLanguage = "en";

    @Column(name = "host_paused", nullable = false)
    private boolean hostPaused = false;

    @Column(name = "weekly_host_minutes", nullable = false)
    private int weeklyHostMinutes = 25;

    public List<String> getRolesList() {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rolesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean hasRole(String role) {
        return getRolesList().contains(role);
    }

    public List<String> getInterestsList() {
        if (interestsCsv == null || interestsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(interestsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // Standard Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }

    public String getRolesCsv() { return rolesCsv; }
    public void setRolesCsv(String rolesCsv) { this.rolesCsv = rolesCsv; }

    public String getInterestsCsv() { return interestsCsv; }
    public void setInterestsCsv(String interestsCsv) { this.interestsCsv = interestsCsv; }

    public String getNotificationFrequency() { return notificationFrequency; }
    public void setNotificationFrequency(String notificationFrequency) { this.notificationFrequency = notificationFrequency; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public boolean isHostPaused() { return hostPaused; }
    public void setHostPaused(boolean hostPaused) { this.hostPaused = hostPaused; }

    public int getWeeklyHostMinutes() { return weeklyHostMinutes; }
    public void setWeeklyHostMinutes(int weeklyHostMinutes) { this.weeklyHostMinutes = weeklyHostMinutes; }
}
