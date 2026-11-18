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
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Maps a row in the PostgreSQL `appreciations` table
 *    (`V3__campfire_challenges_and_recognition.sql`) into a Java object.
 *    Each row records one colleague (`actorId`) appreciating a published story
 *    (`contentId`) authored by another colleague (`recipientId`).
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 17 ("Support a colleague's story: +5 points for the first
 *      three stories you appreciate each week" & "Story appreciated by colleagues:
 *      +5 points per distinct person, capped at +25 per week").
 *    - Implementation Story CF-006 & Slice D5.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Idempotent Per-Story Appreciation (`UNIQUE(actor_id, content_id)`):
 *      An employee can appreciate a given story at most once. Clicking the
 *      "Appreciate" button a second time returns `alreadyAppreciated: true`
 *      with `0` delta points instead of throwing an error or inflating scores.
 *    - Database-Level Self-Appreciation Prevention (`CHECK (actor_id <> recipient_id)`):
 *      Even if application validation were bypassed, PostgreSQL rejects any row
 *      where an author tries to appreciate their own story.
 *    - Point-in-Time Team Snapshots (`actorTeamSnapshot`, `recipientTeamSnapshot`):
 *      Why store the giver's and author's `functionName` on the appreciation row
 *      instead of joining `users.function_name` later?
 *      Because if an employee transfers to a different department next month,
 *      their historical *Connection Maker* badge (earned for appreciating stories
 *      across 3 distinct author teams) must NOT retroactively disappear or change!
 * ============================================================================
 */
@Entity // Declares this class as a JPA persistent entity.
@Table(name = "appreciations") // Binds to the `appreciations` table in PostgreSQL.
public class AppreciationEntity {

    /**
     * Primary key of the appreciation record (e.g., `"app_1a2b3c4d"`).
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * User ID (`users.id`) of the colleague who clicked "Appreciate" (the giver).
     * Combined with `contentId` in `UNIQUE(actor_id, content_id)`.
     */
    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    /**
     * Content item ID (`content_items.id`) of the published story being appreciated.
     */
    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    /**
     * User ID (`users.id`) of the story's author who receives the appreciation.
     * Enforced by DB constraint `chk_appreciations_no_self CHECK (actor_id <> recipient_id)`.
     */
    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    /**
     * ISO-8601 Monday date string (`"YYYY-MM-DD"`, Monday 00:00 UTC boundary)
     * identifying the scoring week in which this appreciation occurred.
     */
    @Column(name = "week_start", nullable = false, length = 16)
    private String weekStart;

    /**
     * Immutable snapshot of the giver's team (`users.function_name`) captured at the
     * exact moment the appreciation was recorded.
     */
    @Column(name = "actor_team_snapshot", nullable = false, length = 64)
    private String actorTeamSnapshot;

    /**
     * Immutable snapshot of the story author's team (`users.function_name`) captured
     * at the exact moment the appreciation was recorded. Used to evaluate the
     * *Connection Maker* badge (supporting stories from >= 3 distinct teams).
     */
    @Column(name = "recipient_team_snapshot", nullable = false, length = 64)
    private String recipientTeamSnapshot;

    /**
     * Exact UTC timestamp when the appreciation occurred. Used together with `id`
     * (`ORDER BY occurred_at ASC, id ASC`) to provide a 100% deterministic ordering
     * when promoting capped appreciations after a story withdrawal.
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    // =========================================================================
    // Standard JPA Getters & Setters
    // =========================================================================

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
