package com.mastek.pulse.recognition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: RecognitionPreferenceRepository.java
 * Journey: Leaderboard Opt-Out Lookup & Update (Story CF-007).
 * ============================================================================
 */
@Repository
public interface RecognitionPreferenceRepository extends JpaRepository<RecognitionPreferenceEntity, String> {
}
