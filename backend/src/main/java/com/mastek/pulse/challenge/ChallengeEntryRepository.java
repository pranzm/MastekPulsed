package com.mastek.pulse.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeEntryRepository.java
 * Journey: Campfire Weekly Challenge Submission & Publication Award Lookup.
 * ============================================================================
 */
@Repository
public interface ChallengeEntryRepository extends JpaRepository<ChallengeEntryEntity, String> {

    Optional<ChallengeEntryEntity> findByChallengeIdAndUserId(String challengeId, String userId);

    Optional<ChallengeEntryEntity> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    Optional<ChallengeEntryEntity> findByContentId(String contentId);
}
