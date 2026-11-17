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
 * Journey: Colleague Appreciation & Deterministic Cap Recomputation (CF-006).
 * ============================================================================
 */
@Repository
public interface AppreciationRepository extends JpaRepository<AppreciationEntity, String> {

    Optional<AppreciationEntity> findByActorIdAndContentId(String actorId, String contentId);

    List<AppreciationEntity> findByWeekStartOrderByOccurredAtAscIdAsc(String weekStart);

    List<AppreciationEntity> findByActorIdAndWeekStartOrderByOccurredAtAscIdAsc(String actorId, String weekStart);

    List<AppreciationEntity> findByRecipientIdAndWeekStartOrderByOccurredAtAscIdAsc(String recipientId, String weekStart);
}
