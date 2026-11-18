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
 *
 * Why it is designed this way:
 *   Spring Data JPA automatically generates SQL queries from these method names!
 *   1. `findByRequesterIdAndIdempotencyKey`: Enforces safe POST retries on
 *      mobile/web by looking up whether this requester already sent a request
 *      with the same `Idempotency-Key`.
 *   2. `findByInvitationIdAndRequesterId`: Prevents the same requester from
 *      sending multiple separate requests to the same invitation offer.
 *   3. `findByRequesterIdOrderByCreatedAtDesc` &
 *      `findByInvitationIdInOrderByCreatedAtDesc`: Power the "Sent" and
 *      "Received" tabs in the Colleague Connections UI.
 * ============================================================================
 */
// @Repository registers this interface as a Spring Data repository bean and translates SQL exceptions into Spring DataAccessExceptions.
@Repository
public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequestEntity, String> {

    /**
     * Looks up an existing connection request by `(requester_id, idempotency_key)` to support
     * idempotent retries on `POST /api/v1/requests`.
     *
     * @param requesterId    authenticated caller's user ID
     * @param idempotencyKey client-provided `Idempotency-Key` header value
     * @return matching {@link ConnectionRequestEntity} if this key was already used by the caller
     */
    Optional<ConnectionRequestEntity> findByRequesterIdAndIdempotencyKey(String requesterId, String idempotencyKey);

    /**
     * Checks whether a requester has already submitted a connection request for a specific invitation.
     *
     * @param invitationId target invitation ID
     * @param requesterId  authenticated caller's user ID
     * @return existing {@link ConnectionRequestEntity} if found
     */
    Optional<ConnectionRequestEntity> findByInvitationIdAndRequesterId(String invitationId, String requesterId);

    /**
     * Retrieves all connection requests sent by a specific user, newest first.
     *
     * @param requesterId user ID of the requester
     * @return list of sent connection requests ordered by `created_at DESC`
     */
    List<ConnectionRequestEntity> findByRequesterIdOrderByCreatedAtDesc(String requesterId);

    /**
     * Retrieves all connection requests received across any of a host's invitations, newest first.
     *
     * @param invitationIds list of invitation IDs owned by the host
     * @return list of received connection requests ordered by `created_at DESC`
     */
    List<ConnectionRequestEntity> findByInvitationIdInOrderByCreatedAtDesc(List<String> invitationIds);
}
