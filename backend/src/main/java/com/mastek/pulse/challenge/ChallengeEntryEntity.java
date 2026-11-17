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
 * File: ChallengeEntryEntity.java
 * Journey: Campfire Weekly Challenge Submission (Story CF-002 & D3/D4).
 * Why it is designed this way:
 *   Maps to `challenge_entries` in `V3__campfire_challenges_and_recognition.sql`.
 *   Links a user's challenge submission (`UNIQUE(challenge_id, user_id)`) directly
 *   to `content_items.id` so it flows through the exact same `DRAFT -> IN_REVIEW ->
 *   APPROVED -> PUBLISHED -> WITHDRAWN` editorial workflow!
 *   Also stores `idempotencyKey` and `payloadHash` so replaying the same key
 *   with the same payload returns the existing entry, while replaying the same
 *   key with a modified payload returns `409 Conflict (IDEMPOTENCY_CONFLICT)`.
 * ============================================================================
 */
@Entity
@Table(name = "challenge_entries")
public class ChallengeEntryEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "challenge_id", nullable = false, length = 64)
    private String challengeId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "content_id", nullable = false, unique = true, length = 64)
    private String contentId;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
