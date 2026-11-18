package com.mastek.pulse.recognition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: RecognitionPreferenceRepository.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    Spring Data JPA repository for reading and updating an employee's
 *    `RecognitionPreferenceEntity` (`recognition_preferences` table) by `userId`.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 17 & Story CF-007 (Opt-out of public ranking while keeping
 *      personal progress).
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Because `RecognitionPreferenceEntity` uses `userId` (`String`) as its `@Id`,
 *      standard `JpaRepository.findById(userId)` and `save(pref)` provide all
 *      required CRUD operations without any custom query methods.
 * ============================================================================
 */
@Repository // Registers this interface as a Spring Data JPA repository bean.
public interface RecognitionPreferenceRepository extends JpaRepository<RecognitionPreferenceEntity, String> {
}
