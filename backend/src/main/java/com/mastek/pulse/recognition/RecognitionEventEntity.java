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
 * File: RecognitionEventEntity.java
 * Journey: Transactional Recognition Ledger (Proposal Pages 16, 17 & Story CF-005).
 * Why it is designed this way:
 *   Maps to `recognition_events` in `V3__campfire_challenges_and_recognition.sql`.
 *   1. Immutable Ledger: Points are never edited in place. When a published story
 *      is withdrawn, a compensating negative `pointsDelta` row is inserted with
 *      `reversalOf = originalEvent.id` (`UNIQUE`).
 *   2. Idempotent Award Identity (`sourceEventId`): Guarantees that replayed
 *      publication requests or repeated consumer deliveries never duplicate points!
 * ============================================================================
 */
@Entity
@Table(name = "recognition_events")
public class RecognitionEventEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "challenge_id", length = 64)
    private String challengeId;

    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    @Column(name = "week_start", nullable = false, length = 16)
    private String weekStart;

    @Column(name = "source_event_id", nullable = false, unique = true, length = 160)
    private String sourceEventId;

    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason; // PUBLISHED_CONTRIBUTION, GIVE_APPRECIATION, RECEIVE_APPRECIATION, REVERSAL_WITHDRAWN

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    @Column(name = "counterparty_user_id", length = 64)
    private String counterpartyUserId;

    @Column(name = "counterparty_team_snapshot", length = 64)
    private String counterpartyTeamSnapshot;

    @Column(name = "reversal_of", unique = true, length = 64)
    private String reversalOf;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }

    public String getAudienceId() { return audienceId; }
    public void setAudienceId(String audienceId) { this.audienceId = audienceId; }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }

    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getPointsDelta() { return pointsDelta; }
    public void setPointsDelta(int pointsDelta) { this.pointsDelta = pointsDelta; }

    public String getCounterpartyUserId() { return counterpartyUserId; }
    public void setCounterpartyUserId(String counterpartyUserId) { this.counterpartyUserId = counterpartyUserId; }

    public String getCounterpartyTeamSnapshot() { return counterpartyTeamSnapshot; }
    public void setCounterpartyTeamSnapshot(String counterpartyTeamSnapshot) { this.counterpartyTeamSnapshot = counterpartyTeamSnapshot; }

    public String getReversalOf() { return reversalOf; }
    public void setReversalOf(String reversalOf) { this.reversalOf = reversalOf; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
