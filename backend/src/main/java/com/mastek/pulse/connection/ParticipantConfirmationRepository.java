package com.mastek.pulse.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ParticipantConfirmationRepository.java
 * Journey: Bilateral Private Feedback & Pilot Metrics (Proposal Pages 8, 12, 15).
 * Why it is designed this way:
 *   Fetches both participants' confirmation rows for a `requestId` so we can
 *   evaluate whether BOTH confirmed `happened = 'YES'` and `useful = true`.
 * ============================================================================
 */
@Repository
public interface ParticipantConfirmationRepository extends JpaRepository<ParticipantConfirmationEntity, String> {

    List<ParticipantConfirmationEntity> findByRequestId(String requestId);

    Optional<ParticipantConfirmationEntity> findByRequestIdAndUserId(String requestId, String userId);
}
