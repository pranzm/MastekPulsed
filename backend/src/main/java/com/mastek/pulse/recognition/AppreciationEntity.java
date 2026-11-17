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
 * File: AppreciationEntity.java
 * Journey: Colleague Appreciation & Badges (Proposal Page 17 & Story CF-006).
 * Why it is designed this way:
 *   Maps to `appreciations` in `V3__campfire_challenges_and_recognition.sql`.
 *   1. Enforces `UNIQUE(actor_id, content_id)` and `actor_id <> recipient_id`
 *      so repeated clicks or self-appreciation can never inflate points.
 *   2. Snapshots `actorTeamSnapshot` and `recipientTeamSnapshot` at event time
 *      so *Connection Maker* (3 author teams) is immune to later profile edits!
 * ============================================================================
 */
@Entity
@Table(name = "appreciations")
public class AppreciationEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Column(name = "week_start", nullable = false, length = 16)
    private String weekStart;

    @Column(name = "actor_team_snapshot", nullable = false, length = 64)
    private String actorTeamSnapshot;

    @Column(name = "recipient_team_snapshot", nullable = false, length = 64)
    private String recipientTeamSnapshot;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String weekStart) { this.weekStart = weekStart; }

    public String getActorTeamSnapshot() { return actorTeamSnapshot; }
    public void setActorTeamSnapshot(String actorTeamSnapshot) { this.actorTeamSnapshot = actorTeamSnapshot; }

    public String getRecipientTeamSnapshot() { return recipientTeamSnapshot; }
    public void setRecipientTeamSnapshot(String recipientTeamSnapshot) { this.recipientTeamSnapshot = recipientTeamSnapshot; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
