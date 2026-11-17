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
 * Journey: Three Meaningful Milestones (Proposal Page 17 & Story CF-006):
 *   1. `FIRST_SPARK` ("First Spark"): publish your first challenge story.
 *   2. `CONNECTION_MAKER` ("Connection Maker"): appreciate stories from three teams.
 *   3. `COMMUNITY_FAVOURITE` ("Community Favourite"): receive appreciation from
 *      five distinct colleagues.
 * ============================================================================
 */
@Entity
@Table(name = "weekly_badges")
public class WeeklyBadgeEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "week_start", nullable = false, length = 16)
    private String weekStart;

    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    @Column(name = "badge_code", nullable = false, length = 64)
    private String badgeCode;

    @Column(name = "badge_label", nullable = false, length = 80)
    private String badgeLabel;

    @Column(name = "qualifying_evidence", nullable = false, length = 255)
    private String qualifyingEvidence;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

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
