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
 * Journey: Transactional Recognition Ledger & Weekly Projections (CF-005, CF-007).
 * ============================================================================
 */
@Repository
public interface RecognitionEventRepository extends JpaRepository<RecognitionEventEntity, String> {

    Optional<RecognitionEventEntity> findBySourceEventId(String sourceEventId);

    Optional<RecognitionEventEntity> findByReversalOf(String reversalOf);

    List<RecognitionEventEntity> findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(String userId, String weekStart);

    List<RecognitionEventEntity> findByWeekStartAndAudienceIdInOrderByCreatedAtAscIdAsc(String weekStart, List<String> audienceIds);

    List<RecognitionEventEntity> findByContentIdOrderByCreatedAtAscIdAsc(String contentId);
}
