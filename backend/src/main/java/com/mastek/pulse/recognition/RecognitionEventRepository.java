package com.mastek.pulse.recognition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: RecognitionEventRepository.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Spring Data JPA repository interface for the `recognition_events` ledger table.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Stories CF-004, CF-005, CF-006, CF-007 (Idempotent award checks,
 *      compensating withdrawal reversals, weekly cap calculations, and
 *      audience-scoped leaderboard projections).
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Every list query orders by `CreatedAtAscIdAsc` so ledger replay is 100%
 *      deterministic even when multiple events are inserted within the same millisecond.
 * ============================================================================
 */
@Repository // Registers this interface as a Spring Data JPA repository bean.
public interface RecognitionEventRepository extends JpaRepository<RecognitionEventEntity, String> {

    /**
     * Looks up a ledger event by its deterministic idempotency key (`source_event_id`).
     * Used before inserting a new award to guarantee at-most-once point creation.
     *
     * @param sourceEventId deterministic key (e.g., `"PUB:2026-11-16:usr_asha:story_01"`)
     * @return `Optional` containing the existing ledger event if already awarded
     */
    Optional<RecognitionEventEntity> findBySourceEventId(String sourceEventId);

    /**
     * Checks whether a positive ledger event (`reversalOf == originalEvent.id`)
     * has already been reversed by a `REVERSAL_WITHDRAWN` entry.
     *
     * @param reversalOf ID of the original positive `RecognitionEventEntity`
     * @return `Optional` containing the reversal row if already reversed
     */
    Optional<RecognitionEventEntity> findByReversalOf(String reversalOf);

    /**
     * Returns all ledger events for `userId` in `weekStart`, ordered chronologically
     * (`created_at ASC, id ASC`). Used to compute weekly net points and cap availability.
     *
     * @param userId    target employee's user ID
     * @param weekStart Monday 00:00 UTC date key (`"YYYY-MM-DD"`)
     * @return chronological list of ledger events for that user and week
     */
    List<RecognitionEventEntity> findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(String userId, String weekStart);

    /**
     * Returns all ledger events in `weekStart` restricted to the specified `audienceIds`.
     *
     * @param weekStart   Monday 00:00 UTC date key
     * @param audienceIds list of permitted audience IDs
     * @return chronological list of audience-filtered ledger events
     */
    List<RecognitionEventEntity> findByWeekStartAndAudienceIdInOrderByCreatedAtAscIdAsc(String weekStart, List<String> audienceIds);

    /**
     * Returns all ledger events associated with a specific `contentId` (both original
     * awards and any compensating reversals), ordered chronologically.
     * Used when withdrawing a story or computing net `pointsAwarded` on a challenge entry.
     *
     * @param contentId ID of the `content_items` row
     * @return chronological list of ledger events for that story
     */
    List<RecognitionEventEntity> findByContentIdOrderByCreatedAtAscIdAsc(String contentId);
}
