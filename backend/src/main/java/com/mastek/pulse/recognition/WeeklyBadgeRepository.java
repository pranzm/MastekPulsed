package com.mastek.pulse.recognition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: WeeklyBadgeRepository.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Spring Data JPA repository interface for querying and synchronizing
 *    `WeeklyBadgeEntity` rows in the `weekly_badges` table.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 17 & Story CF-006 (Three Meaningful Milestones:
 *      `First Spark`, `Connection Maker`, `Community Favourite`).
 * ============================================================================
 */
@Repository // Registers this interface as a Spring Data JPA repository bean.
public interface WeeklyBadgeRepository extends JpaRepository<WeeklyBadgeEntity, String> {

    /**
     * Retrieves all badges earned by `userId` during `weekStart` across all audiences.
     *
     * @param userId    employee's user ID
     * @param weekStart Monday 00:00 UTC week key (`"YYYY-MM-DD"`)
     * @return list of badges persisted for that user and week
     */
    List<WeeklyBadgeEntity> findByUserIdAndWeekStart(String userId, String weekStart);

    /**
     * Deletes badges for a specific user, week, and audience when recalculating
     * badge eligibility.
     *
     * @param userId     employee's user ID
     * @param weekStart  Monday 00:00 UTC week key
     * @param audienceId audience scope ID
     */
    void deleteByUserIdAndWeekStartAndAudienceId(String userId, String weekStart, String audienceId);
}
