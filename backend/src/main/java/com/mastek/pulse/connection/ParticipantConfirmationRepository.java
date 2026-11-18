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
 *
 * Why it is designed this way:
 *   Fetches both participants' confirmation rows for a `requestId` so we can
 *   evaluate whether BOTH confirmed `happened = 'YES'` and `useful = true`.
 * ============================================================================
 */
// @Repository registers this interface as a Spring Data JPA repository bean.
@Repository
public interface ParticipantConfirmationRepository extends JpaRepository<ParticipantConfirmationEntity, String> {

    /**
     * Loads all confirmation rows (up to 2: requester and host) associated with a connection request.
     *
     * @param requestId ID of the connection request
     * @return list of {@link ParticipantConfirmationEntity} rows for that request
     */
    List<ParticipantConfirmationEntity> findByRequestId(String requestId);

    /**
     * Looks up a single participant's confirmation row for `(request_id, user_id)` so repeat
     * submissions update the existing row rather than inserting a duplicate.
     *
     * @param requestId ID of the connection request
     * @param userId    ID of the participant (requester or host)
     * @return optional {@link ParticipantConfirmationEntity}
     */
    Optional<ParticipantConfirmationEntity> findByRequestIdAndUserId(String requestId, String userId);
}
