package com.mastek.pulse.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeEntity.java
 * Journey: Campfire Weekly Connection Challenge ("Beyond My Job Title" -
 *          Proposal Page 16 & Story CF-002).
 * Why it is designed this way:
 *   Maps to the `challenges` table in `V3__campfire_challenges_and_recognition.sql`.
 *   Stores the active window (`startsAt`, `endsAt`), `timezone` (`UTC`), and
 *   permitted `audienceId` (`aud_pilot`).
 * ============================================================================
 */
@Entity
@Table(name = "challenges")
public class ChallengeEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "prompt", nullable = false, length = 1000)
    private String prompt;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isOpenAt(Instant now) {
        return "ACTIVE".equals(status) && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getAudienceId() { return audienceId; }
    public void setAudienceId(String audienceId) { this.audienceId = audienceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
