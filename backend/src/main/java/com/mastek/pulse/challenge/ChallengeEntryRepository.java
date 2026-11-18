package com.mastek.pulse.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeEntryRepository.java
 * Module: `challenge/` (Weekly Campfire Connection Challenges)
 *
 * 1. WHAT THIS FILE DOES:
 *    Spring Data JPA repository interface for querying and persisting
 *    `ChallengeEntryEntity` rows in the `challenge_entries` table
 *    (`V3__campfire_challenges_and_recognition.sql`).
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 16 ("One challenge story per person each week").
 *    - Implementation Stories CF-002, CF-004 & Slices D3 / D4.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - `findByChallengeIdAndUserId`: Enforces the "one challenge story per
 *      person each week" rule and allows `GET /api/v1/challenges/current`
 *      to attach the caller's existing submission state (`myEntry`).
 *    - `findByUserIdAndIdempotencyKey`: Supports safe network retries on
 *      `POST /api/v1/challenges/{id}/entries` (`Idempotency-Key` header).
 *    - `findByContentId`: Lets `RecognitionService.awardOnPublication` resolve
 *      which weekly challenge a newly published story belongs to.
 * ============================================================================
 */
@Repository
public interface ChallengeEntryRepository extends JpaRepository<ChallengeEntryEntity, String> {

    /**
     * Looks up a user's entry for a specific weekly challenge (`UNIQUE(challenge_id, user_id)`).
     */
    Optional<ChallengeEntryEntity> findByChallengeIdAndUserId(String challengeId, String userId);

    /**
     * Looks up a prior entry by user ID and caller-supplied `Idempotency-Key`
     * (`UNIQUE(user_id, idempotency_key)`).
     */
    Optional<ChallengeEntryEntity> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    /**
     * Looks up the challenge entry linked to a given `content_items.id` (`UNIQUE(content_id)`).
     */
    Optional<ChallengeEntryEntity> findByContentId(String contentId);
}
