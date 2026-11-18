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
 *
 * Why it is designed this way:
 *   Reads and updates the employee's exact listening position (`position_seconds`)
 *   for Mastek Radio queues and Culture Cast episodes using the composite key
 *   {@link PlaybackProgressEntity.PlaybackProgressId}.
 * ============================================================================
 */
// @Repository registers this interface as a Spring Data JPA repository bean.
@Repository
public interface PlaybackProgressRepository extends JpaRepository<PlaybackProgressEntity, PlaybackProgressEntity.PlaybackProgressId> {

    /**
     * Retrieves all saved playback positions for a specific user ordered by most recently updated first.
     *
     * @param userId ID of the authenticated listener
     * @return list of {@link PlaybackProgressEntity} rows for that user
     */
    List<PlaybackProgressEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
}
