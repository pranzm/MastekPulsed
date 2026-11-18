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
 * File: RecognitionPreferenceEntity.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Maps a row in the `recognition_preferences` table
 *    (`V3__campfire_challenges_and_recognition.sql`) into a Java object.
 *    Stores an employee's privacy preference (`showOnLeaderboard`) controlling
 *    whether they appear on the weekly Community Champions leaderboard.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 17 ("Participants can opt out of public ranking while keeping
 *      personal progress").
 *    - Implementation Story CF-007 & Slice D5 (`PUT /api/v1/recognition/preferences`).
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - 1-to-1 Primary Key (`userId`):
 *      Uses `user_id` directly as the `@Id` primary key so each employee has at
 *      most one preference row, defaulting to `showOnLeaderboard = true`.
 *    - Privacy Without Penalty:
 *      Setting `showOnLeaderboard = false` only hides the employee from
 *      `GET /api/v1/recognition/leaderboard`; their points and badges continue
 *      to accumulate normally in `GET /api/v1/recognition/me`.
 * ============================================================================
 */
@Entity // Declares this class as a JPA persistent entity.
@Table(name = "recognition_preferences") // Maps to the `recognition_preferences` table in PostgreSQL.
public class RecognitionPreferenceEntity {

    /**
     * Primary key AND foreign key referencing `users.id` (e.g., `"usr_asha"`).
     */
    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Whether this employee wants their name, points, and badges displayed on the
     * optional Top-5 Community Champions leaderboard (`true` by default).
     */
    @Column(name = "show_on_leaderboard", nullable = false)
    private boolean showOnLeaderboard = true;

    /**
     * UTC timestamp when the employee last toggled their leaderboard preference.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // =========================================================================
    // Standard JPA Getters & Setters
    // =========================================================================

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isShowOnLeaderboard() { return showOnLeaderboard; }
    public void setShowOnLeaderboard(boolean showOnLeaderboard) { this.showOnLeaderboard = showOnLeaderboard; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
