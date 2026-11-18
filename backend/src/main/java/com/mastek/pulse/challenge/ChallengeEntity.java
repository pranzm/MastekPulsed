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
 * Module: `challenge/` (Weekly Campfire Connection Challenges)
 *
 * 1. WHAT THIS FILE DOES:
 *    This JPA `@Entity` class maps a row in the PostgreSQL `challenges` table
 *    (defined in `V3__campfire_challenges_and_recognition.sql`) into a Java object.
 *    Each row represents a time-boxed weekly Campfire prompt (such as
 *    "Beyond My Job Title").
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 16 ("Beyond My Job Title" Weekly Challenge).
 *    - Implementation Story CF-002 & Slice D3/D4.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Server-Owned Time Window (`startsAt`, `endsAt`, `timezone`):
 *      Clients cannot submit to a challenge before `startsAt` or after `endsAt`.
 *      The helper method `isOpenAt(Instant now)` uses the server's injected
 *      `PulseClock` so automated tests can fast-forward time deterministically.
 *    - Audience Scoping (`audienceId`):
 *      Every challenge belongs to a specific `audience_id` (e.g., `aud_pilot`).
 *      Following Guardrail #1 ("Filter Audience BEFORE Ranking/Returning"),
 *      employees only see challenges whose `audience_id` they are members of.
 *    - Scalar Foreign Key (`String audienceId` instead of `@ManyToOne AudienceEntity`):
 *      Following our Modular Monolith boundary rules (`03-architecture-and-guardrails.md`),
 *      cross-module foreign keys are stored as plain String IDs so modules stay
 *      loosely coupled and ready for future microservice extraction.
 * ============================================================================
 */
@Entity // Tells Hibernate/JPA that this class is backed by a relational database table.
@Table(name = "challenges") // Binds this entity to the `challenges` table in PostgreSQL.
public class ChallengeEntity {

    /**
     * Primary key of the challenge (e.g., `"ch_beyond_01"`).
     * Stored as a deterministic human-readable `VARCHAR(64)` ID in seed data.
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * Short display title of the weekly challenge (e.g., `"Beyond My Job Title"`).
     */
    @Column(name = "title", nullable = false, length = 160)
    private String title;

    /**
     * Detailed prompt text explaining what employees are invited to share
     * (e.g., a skill, passion, or lesson learned outside their formal role).
     */
    @Column(name = "prompt", nullable = false, length = 1000)
    private String prompt;

    /**
     * UTC timestamp when the challenge opens for employee story submissions.
     * Enforced by a database constraint `chk_challenges_window CHECK (ends_at > starts_at)`.
     */
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /**
     * UTC timestamp when the challenge closes for new submissions (exclusive upper bound).
     */
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    /**
     * Canonical IANA timezone identifier used for weekly scoring boundaries.
     * Defaults to `"UTC"` (Monday 00:00 UTC week rollover).
     */
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    /**
     * Foreign key referencing `audiences.id` (e.g., `"aud_pilot"`).
     * Only members of this audience may view or submit to this challenge.
     */
    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    /**
     * Administrative lifecycle state of the challenge (`"ACTIVE"` or `"CLOSED"`).
     * Even if `status` is `"ACTIVE"`, `isOpenAt(now)` also verifies the timestamp window.
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    /**
     * Audit timestamp recording when this challenge row was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Evaluates whether this challenge is open for story submissions at the given instant.
     *
     * Intern Note:
     *   Why pass `Instant now` as a parameter instead of calling `Instant.now()` inside?
     *   Because passing `pulseClock.now()` lets integration tests travel forward or
     *   backward in time to test expired challenges (`409 CHALLENGE_CLOSED`) without
     *   mutating database fixtures or sleeping!
     *
     * @param now current server timestamp supplied by `PulseClock`
     * @return `true` if `status == "ACTIVE"` and `startsAt <= now < endsAt`
     */
    public boolean isOpenAt(Instant now) {
        // Check 1: Administrative status must be "ACTIVE".
        // Check 2: `now` must not be before `startsAt` (i.e., `now >= startsAt`).
        // Check 3: `now` must be strictly before `endsAt` (i.e., `now < endsAt`).
        return "ACTIVE".equals(status) && !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    // =========================================================================
    // Standard JPA Getters & Setters
    // =========================================================================

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
