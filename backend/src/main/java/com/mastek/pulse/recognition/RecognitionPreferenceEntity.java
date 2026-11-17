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
 * Journey: Community Champions Leaderboard Privacy / Opt-Out (Proposal Page 17 & CF-007).
 * Why it is designed this way:
 *   Maps to `recognition_preferences` (`user_id` PK, `show_on_leaderboard`).
 *   Employees can hide their public ranking (`showOnLeaderboard = false`) while
 *   still retaining full access to their private progress on `/api/v1/recognition/me`.
 * ============================================================================
 */
@Entity
@Table(name = "recognition_preferences")
public class RecognitionPreferenceEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "show_on_leaderboard", nullable = false)
    private boolean showOnLeaderboard = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isShowOnLeaderboard() { return showOnLeaderboard; }
    public void setShowOnLeaderboard(boolean showOnLeaderboard) { this.showOnLeaderboard = showOnLeaderboard; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
