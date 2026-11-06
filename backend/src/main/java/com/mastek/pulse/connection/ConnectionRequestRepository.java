package com.mastek.pulse.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ConnectionRequestRepository.java
 * Journey: Colleague Connections & Idempotent Retries (Proposal Pages 7 & 15).
 * Why it is designed this way:
 *   Supports looking up requests by `requesterId` + `idempotencyKey` (for safe
 *   POST retries), checking if a requester already invited a host on a given
 *   `invitationId`, and listing both Sent and Received requests.
 * ============================================================================
 */
@Repository
public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequestEntity, String> {

    Optional<ConnectionRequestEntity> findByRequesterIdAndIdempotencyKey(String requesterId, String idempotencyKey);

    Optional<ConnectionRequestEntity> findByInvitationIdAndRequesterId(String invitationId, String requesterId);

    List<ConnectionRequestEntity> findByRequesterIdOrderByCreatedAtDesc(String requesterId);

    List<ConnectionRequestEntity> findByInvitationIdInOrderByCreatedAtDesc(List<String> invitationIds);
}
