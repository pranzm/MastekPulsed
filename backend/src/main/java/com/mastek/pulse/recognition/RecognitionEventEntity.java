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
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Maps a row in the append-only PostgreSQL `recognition_events` table
 *    (`V3__campfire_challenges_and_recognition.sql`) into a Java object.
 *    This table is the **Transactional Recognition Ledger**—every point awarded
 *    or reversed in Mastek Campfire is recorded here as an immutable accounting entry.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Pages 16 & 17 (Point System: +20 published contribution,
 *      +5 giving appreciation, +5 receiving appreciation, 60-point weekly cap).
 *    - Implementation Stories CF-004, CF-005, CF-006 & Slices D3 / D4 / D5.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Double-Entry / Immutable Accounting Ledger Pattern:
 *      Why don't we just store `int points` on the `users` table and do `points += 20`?
 *      Because a mutable counter on `users` loses history, cannot separate points
 *      by `weekStart` or `audienceId`, and cannot safely audit why a score changed!
 *      With an immutable ledger:
 *        * Awarding points inserts a positive row (`+20` or `+5`).
 *        * Withdrawing a story never deletes history; instead, it inserts a
 *          compensating negative row (`-20` or `-5`, `reason = "REVERSAL_WITHDRAWN"`)
 *          pointing to the original row via `reversalOf` (`UNIQUE`).
 *    - Idempotent Award Key (`sourceEventId UNIQUE`):
 *      Every award computes a deterministic key such as
 *      `"PUB:2026-11-16:usr_asha:story_ch_123"`. Even if a request is retried,
 *      the unique index on `source_event_id` guarantees points cannot be awarded twice.
 * ============================================================================
 */
@Entity // Marks this class as a JPA persistent entity.
@Table(name = "recognition_events") // Maps to the `recognition_events` table in PostgreSQL.
public class RecognitionEventEntity {

    /**
     * Primary key of the ledger entry (e.g., `"rec_9f8e7d6c"` or `"rec_rev_..."`).
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * User ID (`users.id`) of the employee whose weekly point balance is affected
     * by this ledger entry.
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Optional challenge ID (`challenges.id`, e.g., `"ch_beyond_01"`) associated
     * with this recognition event.
     */
    @Column(name = "challenge_id", length = 64)
    private String challengeId;

    /**
     * Audience ID (`audiences.id`, e.g., `"aud_pilot"` or `"aud_uk_restricted"`)
     * of the published story that generated this event.
     * Used to filter leaderboard and personal progress projections so restricted
     * audience activity never leaks into broader audiences.
     */
    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    /**
     * ISO-8601 Monday date string (`"YYYY-MM-DD"`, Monday 00:00 UTC) identifying
     * the scoring week this event belongs to.
     */
    @Column(name = "week_start", nullable = false, length = 16)
    private String weekStart;

    /**
     * Deterministic idempotency key for the domain event that produced this ledger row
     * (e.g., `"PUB:2026-11-16:usr_asha:story_01"`, `"GIVE:..."`, `"RECV:..."`, `"REV:..."`).
     * Enforced by a database `UNIQUE` constraint so duplicate awards are impossible.
     */
    @Column(name = "source_event_id", nullable = false, unique = true, length = 160)
    private String sourceEventId;

    /**
     * Foreign key referencing the `content_items.id` whose publication, appreciation,
     * or withdrawal triggered this ledger entry.
     */
    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    /**
     * Ledger reason code. Enforced by DB `CHECK` constraint to be one of:
     *   - `"PUBLISHED_CONTRIBUTION"` (+20)
     *   - `"GIVE_APPRECIATION"`      (+5)
     *   - `"RECEIVE_APPRECIATION"`   (+5)
     *   - `"REVERSAL_WITHDRAWN"`     (-20 or -5 compensating entry on withdrawal)
     */
    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    /**
     * Signed integer point delta (`+20`, `+5`, `-20`, or `-5`).
     * Enforced by DB `CHECK (points_delta BETWEEN -25 AND 25 AND points_delta <> 0)`.
     */
    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    /**
     * For appreciation events (`GIVE_APPRECIATION` / `RECEIVE_APPRECIATION`),
     * stores the other colleague's user ID:
     *   - On a `GIVE_APPRECIATION` row: `counterpartyUserId` is the story author (`recipientId`).
     *   - On a `RECEIVE_APPRECIATION` row: `counterpartyUserId` is the appreciator (`actorId`),
     *     which lets the service enforce the rule "only 1 reward per distinct appreciating colleague".
     */
    @Column(name = "counterparty_user_id", length = 64)
    private String counterpartyUserId;

    /**
     * Snapshot of the counterparty colleague's team (`functionName`) at the moment
     * of the event, preserved for historical badge auditing.
     */
    @Column(name = "counterparty_team_snapshot", length = 64)
    private String counterpartyTeamSnapshot;

    /**
     * When `reason == "REVERSAL_WITHDRAWN"`, this column stores the `id` of the
     * original positive `RecognitionEventEntity` being reversed.
     * Marked `unique = true` in the DB so an original award can be reversed at most once!
     */
    @Column(name = "reversal_of", unique = true, length = 64)
    private String reversalOf;

    /**
     * UTC timestamp when this ledger entry was written.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // =========================================================================
    // Standard JPA Getters & Setters
    // =========================================================================

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
