package com.mastek.pulse.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeRepository.java
 * Module: `challenge/` (Weekly Campfire Connection Challenges)
 *
 * 1. WHAT THIS FILE DOES:
 *    Spring Data JPA repository interface for the `challenges` table.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Story CF-002 (`GET /api/v1/challenges/current`).
 *    - Guardrail #1 ("Filter Audience BEFORE Ranking or Returning Data").
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Instead of calling `findAll()` and filtering audiences in Java memory,
 *      `findByAudienceIdInOrderByStartsAtDesc` pushes the `WHERE audience_id IN (...)`
 *      predicate directly down into PostgreSQL so challenges for restricted
 *      audiences never leave the database.
 * ============================================================================
 */
@Repository // Marks this interface as a Spring-managed JPA repository bean.
public interface ChallengeRepository extends JpaRepository<ChallengeEntity, String> {

    /**
     * Retrieves all challenges whose `audience_id` is in the caller's list of
     * permitted audiences, ordered newest-first by `starts_at`.
     *
     * Generated SQL:
     *   `SELECT * FROM challenges WHERE audience_id IN (?) ORDER BY starts_at DESC`
     *
     * @param audienceIds non-empty list of audience IDs the caller belongs to
     * @return list of permitted challenges ordered with the most recent challenge first
     */
    List<ChallengeEntity> findByAudienceIdInOrderByStartsAtDesc(List<String> audienceIds);
}
