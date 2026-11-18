package com.mastek.pulse.recognition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AppreciationRepository.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Spring Data JPA repository interface for querying and saving `AppreciationEntity`
 *    records in the `appreciations` table.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Story CF-006 (Idempotent colleague appreciation, weekly giving/receiving
 *      caps, *Connection Maker* and *Community Favourite* badges, and deterministic
 *      cap promotion when an earlier appreciated story is withdrawn).
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Deterministic Tie-Breaking Order (`OrderByOccurredAtAscIdAsc`):
 *      Two appreciations in a test or high-concurrency burst might share the exact
 *      same millisecond `occurred_at` timestamp. Adding `IdAsc` as a secondary sort
 *      guarantees that PostgreSQL always returns rows in a strict, repeatable total
 *      order when recomputing weekly caps!
 * ============================================================================
 */
@Repository // Registers this interface as a Spring Data JPA repository bean.
public interface AppreciationRepository extends JpaRepository<AppreciationEntity, String> {

    /**
     * Checks whether `actorId` has already appreciated `contentId`.
     * Backed by `CONSTRAINT uq_appreciations_actor_content UNIQUE (actor_id, content_id)`.
     *
     * Generated SQL:
     *   `SELECT * FROM appreciations WHERE actor_id = ? AND content_id = ?`
     *
     * @param actorId   user ID of the colleague clicking "Appreciate"
     * @param contentId story ID being appreciated
     * @return `Optional` containing the existing appreciation row if already recorded
     */
    Optional<AppreciationEntity> findByActorIdAndContentId(String actorId, String contentId);

    /**
     * Returns all appreciations recorded during `weekStart`, ordered chronologically
     * by `occurred_at ASC` and tie-broken by `id ASC`.
     * Used by `RecognitionService.reconcileCappedAppreciationsForWeek` when a story
     * withdrawal frees up a giver's 3-story cap or a receiver's 5-colleague cap.
     *
     * @param weekStart Monday 00:00 UTC date key (e.g., `"2026-11-16"`)
     * @return chronologically ordered appreciations for that week
     */
    List<AppreciationEntity> findByWeekStartOrderByOccurredAtAscIdAsc(String weekStart);

    /**
     * Returns all appreciations given by `actorId` during `weekStart` in deterministic
     * chronological order. Used to count `givenCount` and `distinctTeamsSupported`
     * (for the *Connection Maker* badge).
     *
     * @param actorId   giver's user ID
     * @param weekStart Monday 00:00 UTC date key
     * @return list of appreciations given by `actorId` in `weekStart`
     */
    List<AppreciationEntity> findByActorIdAndWeekStartOrderByOccurredAtAscIdAsc(String actorId, String weekStart);

    /**
     * Returns all appreciations received by `recipientId` during `weekStart` in
     * deterministic chronological order. Used to count `receivedDistinctCount`
     * (for the *Community Favourite* badge, which requires 5 distinct colleagues).
     *
     * @param recipientId story author's user ID
     * @param weekStart   Monday 00:00 UTC date key
     * @return list of appreciations received by `recipientId` in `weekStart`
     */
    List<AppreciationEntity> findByRecipientIdAndWeekStartOrderByOccurredAtAscIdAsc(String recipientId, String weekStart);
}
