package com.mastek.pulse.listen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PlaybackProgressRepository.java
 * Journey: Cross-Device Audio Resume (Employee Web <-> Android/iOS Mobile).
 * Why it is designed this way:
 *   Reads and updates the employee's exact listening position (`position_seconds`)
 *   for Mastek Radio queues and Culture Cast episodes.
 * ============================================================================
 */
@Repository
public interface PlaybackProgressRepository extends JpaRepository<PlaybackProgressEntity, PlaybackProgressEntity.PlaybackProgressId> {
    List<PlaybackProgressEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
}
