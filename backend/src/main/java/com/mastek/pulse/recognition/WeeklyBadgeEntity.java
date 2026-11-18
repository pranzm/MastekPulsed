package com.mastek.pulse.recognition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: WeeklyBadgeEntity.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Maps a row in the PostgreSQL `weekly_badges` table
 *    (`V3__campfire_challenges_and_recognition.sql`) into a Java object.
 *    Each row represents one milestone badge earned by an employee during a
 *    specific week (`weekStart`) and within a specific `audienceId`.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 17 ("Three Meaningful Milestones"):
 *      1. `FIRST_SPARK` ("First Spark"): publish your first challenge story (+20).
 *      2. `CONNECTION_MAKER` ("Connection Maker"): appreciate published stories
 *         from at least 3 distinct colleague teams (`recipientTeamSnapshot`).
 *      3. `COMMUNITY_FAVOURITE` ("Community Favourite"): receive appreciation on
 *         your published story from at least 5 distinct colleagues.
 *    - Implementation Story CF-006 & Slice D5.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Composite Uniqueness (`UNIQUE(user_id, week_start, audience_id, badge_code)`):
 *      Guarantees an employee can never be awarded duplicate copies of the same
 *      badge in the same week and audience.
 *    - Synchronized on Reversals:
 *      If a story is withdrawn and the user no longer meets a badge's criteria,
 *      `RecognitionService.syncWeeklyBadges` removes the revoked badge row inside
 *      the same transaction so database state always matches live eligibility.
 * ============================================================================
 */
@Entity // Marks this class as a JPA persistent entity.
@Table(name = "weekly_badges") // Binds to the `weekly_badges` table in PostgreSQL.
public class WeeklyBadgeEntity {

    /**
     * Primary key of the badge record (e.g., `"bdg_a1b2c3d4"`).
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * User ID (`users.id`) of the employee who earned this badge.
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * ISO-8601 Monday date string (`"YYYY-MM-DD"`, Monday 00:00 UTC) identifying
     * the scoring week in which the badge was earned.
     */
    @Column(name = "week_start", nullable = false, length = 16)
    private String weekStart;

    /**
     * Audience ID (`audiences.id`, e.g., `"aud_pilot"`) in which the qualifying
     * activity took place.
     */
    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    /**
     * Machine-readable badge identifier (`"FIRST_SPARK"`, `"CONNECTION_MAKER"`,
     * or `"COMMUNITY_FAVOURITE"`).
     */
    @Column(name = "badge_code", nullable = false, length = 64)
    private String badgeCode;

    /**
     * Human-readable display label (`"First Spark"`, `"Connection Maker"`,
     * or `"Community Favourite"`).
     */
    @Column(name = "badge_label", nullable = false, length = 80)
    private String badgeLabel;

    /**
     * Audit explanation string describing why the badge was awarded
     * (e.g., `"Appreciated published stories across 3 teams"`).
     */
    @Column(name = "qualifying_evidence", nullable = false, length = 255)
    private String qualifyingEvidence;

    /**
     * UTC timestamp when the badge was awarded.
     */
    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

    // =========================================================================
    // Standard JPA Getters & Setters
    // =========================================================================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }

    public String getAudienceId() { return audienceId; }
    public void setAudienceId(String audienceId) { this.audienceId = audienceId; }

    public String getBadgeCode() { return badgeCode; }
    public void setBadgeCode(String badgeCode) { this.badgeCode = badgeCode; }

    public String getBadgeLabel() { return badgeLabel; }
    public void setBadgeLabel(String badgeLabel) { this.badgeLabel = badgeLabel; }

    public String getQualifyingEvidence() { return qualifyingEvidence; }
    public void setQualifyingEvidence(String qualifyingEvidence) { this.qualifyingEvidence = qualifyingEvidence; }

    public Instant getAwardedAt() { return awardedAt; }
    public void setAwardedAt(Instant awardedAt) { this.awardedAt = awardedAt; }
}
