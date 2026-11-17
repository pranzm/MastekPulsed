package com.mastek.pulse.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeRepository.java
 * Journey: Campfire Weekly Challenge Discovery (Story CF-002).
 * Why it is designed this way:
 *   Looks up active challenges filtered by the caller's permitted `audience_id`s.
 * ============================================================================
 */
@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity, String> {
    List<ChallengeEntity> findByAudienceIdInOrderByStartsAtDesc(List<String> audienceIds);
}
