package com.mastek.pulse.connection;

import com.mastek.pulse.common.*;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import com.mastek.pulse.content.ContentRevisionRepository;
import com.mastek.pulse.outbox.OutboxEventEntity;
import com.mastek.pulse.outbox.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ConnectionService.java
 * Journey: Primary Journey ("Review and send an invitation -> Host accepts ->
 *          Both participants record feedback" - Proposal Pages 2, 7, 8, 12, 14, 15).
 *
 * Why it is designed this way:
 *   1. Idempotent Request Creation (`POST /api/v1/requests` + `Idempotency-Key` header):
 *      If a mobile client retries sending due to a spotty network, we return the
 *      existing request instead of creating a duplicate!
 *   2. ACCEPT FLOW / ONE TRANSACTION (Proposal Page 15):
 *      - Step 1: Authenticate the host and re-check audience, invitation expiry, and request ownership.
 *      - Step 2: Lock the invitation row (`findByIdWithPessimisticLock` -> `SELECT ... FOR UPDATE`);
 *                require a `PENDING` request and remaining capacity (`acceptedCount < capacity`).
 *      - Step 3: Set `ACCEPTED`, increment `acceptedCount`, and write an `OutboxEvent` in the
 *                exact same database transaction!
 *   3. BILATERAL USEFULNESS CONFIRMATION (Proposal Page 8, 12, 15):
 *      - Only the two participants (`requesterId` and `hostId`) can read or write
 *        confirmations for a request.
 *      - A connection is counted as `USEFUL_CONFIRMED` ONLY when BOTH confirm
 *        `happened == "YES"` and `useful == true`. Missing or skipped feedback
 *        stays `UNKNOWN`!
 * ============================================================================
 */
// @Service registers this class as a transactional business-logic bean in the Spring context.
@Service
public class ConnectionService {

    // Repository for reading and locking (`SELECT ... FOR UPDATE`) invitation offers.
    private final InvitationRepository invitationRepository;

    // Repository for reading and persisting colleague connection requests.
    private final ConnectionRequestRepository requestRepository;

    // Repository for reading and persisting bilateral participant feedback confirmations.
    private final ParticipantConfirmationRepository confirmationRepository;

    // Repository for verifying the parent story and its audience permissions.
    private final ContentItemRepository contentItemRepository;

    // Repository for looking up the story's title from its published/current revision.
    private final ContentRevisionRepository contentRevisionRepository;

    // Repository for looking up requester and host user profiles (display name, region, pause status).
    private final UserRepository userRepository;

    // Repository for writing transactional outbox events in the same DB commit as state changes.
    private final OutboxEventRepository outboxEventRepository;

    // Repository for recording immutable audit log entries when requests are accepted.
    private final AuditEventRepository auditEventRepository;

    // Service for verifying audience membership and role/access guardrails.
    private final IdentityService identityService;

    /**
     * Constructor injection of all repositories and services required for the connection lifecycle.
     */
    public ConnectionService(InvitationRepository invitationRepository,
                             ConnectionRequestRepository requestRepository,
                             ParticipantConfirmationRepository confirmationRepository,
                             ContentItemRepository contentItemRepository,
                             ContentRevisionRepository contentRevisionRepository,
                             UserRepository userRepository,
                             OutboxEventRepository outboxEventRepository,
                             AuditEventRepository auditEventRepository,
                             IdentityService identityService) {
        this.invitationRepository = invitationRepository;
        this.requestRepository = requestRepository;
        this.confirmationRepository = confirmationRepository;
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditEventRepository = auditEventRepository;
        this.identityService = identityService;
    }

    /**
     * Creates a connection request idempotently using the caller-scoped `Idempotency-Key`.
     *
     * @param requester      authenticated colleague sending the request
     * @param idempotencyKey mandatory unique key generated by the client per send attempt
     * @param payload        JSON payload containing `invitationId`, `messageText`, and `aiAssisted`
     * @return connection request DTO map
     */
    // @Transactional ensures the request insert and the outbox event insert succeed or roll back together.
    @Transactional
    public Map<String, Object> createRequest(UserEntity requester, String idempotencyKey, Map<String, Object> payload) {
        // Guardrail 1: Reject requests missing the `Idempotency-Key` header with 422 Unprocessable Entity.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PulseException.unprocessable("422: Idempotency-Key header is required for safe retries.");
        }

        // Guardrail 2: Check idempotency key first (safe mobile retry!).
        // If this requester already submitted a request with the exact same key, return the saved record immediately.
        Optional<ConnectionRequestEntity> existingByIdem = requestRepository
                .findByRequesterIdAndIdempotencyKey(requester.getId(), idempotencyKey.trim());
        if (existingByIdem.isPresent()) {
            ConnectionRequestEntity req = existingByIdem.get();
            InvitationEntity inv = invitationRepository.findById(req.getInvitationId()).orElseThrow();
            return toConnectionRequestDto(req, inv, requester.getId());
        }

        // Extract fields from the incoming request body map.
        String invitationId = (String) payload.get("invitationId");
        String messageText = (String) payload.get("messageText");
        boolean aiAssisted = Boolean.TRUE.equals(payload.get("aiAssisted"));

        // Validate that `invitationId` is provided.
        if (invitationId == null || invitationId.isBlank()) {
            throw PulseException.unprocessable("422: invitationId is required.");
        }
        // Validate that the requester wrote (or reviewed) a meaningful message of at least 5 characters.
        if (messageText == null || messageText.trim().length() < 5) {
            throw PulseException.unprocessable("422: messageText must be at least 5 characters.");
        }

        // Load the target invitation or return 404 Not Found.
        InvitationEntity inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> PulseException.notFound("404: Invitation not found: " + invitationId));

        // Load the parent story attached to the invitation so we can verify audience access.
        ContentItemEntity story = contentItemRepository.findById(inv.getStoryId())
                .orElseThrow(() -> PulseException.notFound("404: Story not found for invitation."));

        // Guardrail 3: Enforce audience permission before allowing request!
        // A user outside the story's audience cannot send an invitation request to its host.
        identityService.requireAudienceAccess(requester, story.getAudienceId());

        // Guardrail 4: Prevent a host from inviting themselves.
        if (inv.getHostId().equals(requester.getId())) {
            throw PulseException.unprocessable("422: Hosts cannot send an invitation request to themselves.");
        }

        // Guardrail 5: Check if the host has globally paused invitations or if this specific invitation is full/expired/paused.
        UserEntity host = userRepository.findById(inv.getHostId()).orElse(null);
        boolean hostPaused = host != null && host.isHostPaused();
        if (!inv.isOpenForRequests(hostPaused)) {
            throw PulseException.conflict(
                    "CAPACITY_FULL",
                    "409: Invitation is not open for requests (Status: " + inv.computeStatusLabel(hostPaused) + ")."
            );
        }

        // Guardrail 6: Prevent a requester from sending multiple requests to the same invitation using different idempotency keys.
        Optional<ConnectionRequestEntity> duplicateOnInvitation = requestRepository
                .findByInvitationIdAndRequesterId(invitationId, requester.getId());
        if (duplicateOnInvitation.isPresent()) {
            throw PulseException.conflict(
                    "CAPACITY_FULL",
                    "409: You have already sent a request for this invitation."
            );
        }

        // Build and persist the new `PENDING` connection request entity at version 1.
        ConnectionRequestEntity req = new ConnectionRequestEntity();
        req.setId("req_" + UUID.randomUUID().toString().substring(0, 8));
        req.setInvitationId(invitationId);
        req.setRequesterId(requester.getId());
        req.setMessageText(messageText.trim());
        req.setAiAssisted(aiAssisted);
        req.setIdempotencyKey(idempotencyKey.trim());
        req.setState("PENDING");
        req.setVersion(1);
        req.setCreatedAt(Instant.now());
        req.setUpdatedAt(Instant.now());
        requestRepository.save(req);

        // Write a durable `REQUEST_CREATED` outbox event in the same database transaction.
        outboxEventRepository.save(OutboxEventEntity.create(
                "CONNECTION_REQUEST",
                req.getId(),
                "REQUEST_CREATED",
                "{\"requestId\":\"" + req.getId() + "\",\"hostId\":\"" + inv.getHostId() + "\",\"requesterId\":\"" + requester.getId() + "\"}"
        ));

        // Return the enriched DTO representation for the caller.
        return toConnectionRequestDto(req, inv, requester.getId());
    }

    /**
     * Executes state transitions (`ACCEPTED`, `DECLINED`, `CANCELLED`) including
     * the exact 4-step ACCEPT FLOW / ONE TRANSACTION from Proposal Page 15.
     *
     * @param caller    authenticated user triggering the state transition
     * @param requestId ID of the connection request being updated
     * @param payload   JSON map containing `targetState` and `expectedVersion`
     * @return updated connection request DTO map
     */
    // @Transactional ensures the pessimistic lock (`SELECT ... FOR UPDATE`), state update, capacity increment, and outbox insert happen atomically.
    @Transactional
    public Map<String, Object> updateRequestState(UserEntity caller, String requestId, Map<String, Object> payload) {
        // Parse the requested target state (`ACCEPTED`, `DECLINED`, or `CANCELLED`) and optimistic version number.
        String targetState = payload.get("targetState") != null ? payload.get("targetState").toString().toUpperCase() : "";
        int expectedVersion = payload.get("expectedVersion") instanceof Number n ? n.intValue() : 1;

        // Fetch the connection request or throw 404 Not Found.
        ConnectionRequestEntity req = requestRepository.findById(requestId)
                .orElseThrow(() -> PulseException.notFound("404: Connection request not found: " + requestId));

        // STEP 2 OF PAGE 15: Lock the invitation row (`SELECT ... FOR UPDATE`).
        // Any concurrent transaction trying to accept another request for the same invitation will block here until we commit!
        InvitationEntity inv = invitationRepository.findByIdWithPessimisticLock(req.getInvitationId())
                .orElseThrow(() -> PulseException.notFound("404: Invitation not found."));

        // Fetch the parent story to verify audience membership.
        ContentItemEntity story = contentItemRepository.findById(inv.getStoryId())
                .orElseThrow(() -> PulseException.notFound("404: Story not found."));

        // STEP 1 OF PAGE 15: Re-check audience, version, and ownership.
        identityService.requireAudienceAccess(caller, story.getAudienceId());

        // Check optimistic version number: if another tab or request already modified this row, reject with 409 Conflict.
        if (req.getVersion() != expectedVersion) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Request version mismatch (expected " + expectedVersion + ", actual " + req.getVersion() + ")."
            );
        }
        // Only `PENDING` requests can transition to `ACCEPTED`, `DECLINED`, or `CANCELLED`.
        if (!"PENDING".equals(req.getState())) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Request is no longer PENDING (current state: " + req.getState() + ")."
            );
        }

        // Branch 1: Host accepts the request (`PENDING -> ACCEPTED`).
        if ("ACCEPTED".equals(targetState)) {
            // Only the host of the invitation is allowed to accept requests.
            if (!inv.getHostId().equals(caller.getId())) {
                throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the invitation host can accept this request.");
            }
            // Verify the invitation is not paused and has not expired since the request was sent.
            if (inv.isPaused() || caller.isHostPaused() || inv.getExpiresAt().isBefore(Instant.now())) {
                throw PulseException.conflict("INVITATION_EXPIRED_OR_PAUSED", "409: Invitation is paused or has expired.");
            }
            // Enforce hard capacity guardrail under the pessimistic write lock (`acceptedCount < capacity`).
            if (inv.getAcceptedCount() >= inv.getCapacity()) {
                throw PulseException.conflict(
                        "CAPACITY_FULL",
                        "409: Full capacity reached (" + inv.getCapacity() + " places already filled this week)."
                );
            }

            // STEP 3 OF PAGE 15: Set accepted, increment accepted_count, and write an outbox event; commit together!
            req.setState("ACCEPTED");
            req.setVersion(req.getVersion() + 1);
            req.setUpdatedAt(Instant.now());
            inv.setAcceptedCount(inv.getAcceptedCount() + 1);

            // Persist both the updated request and the incremented invitation counter.
            requestRepository.save(req);
            invitationRepository.save(inv);

            // Enqueue the `REQUEST_ACCEPTED` event in the transactional outbox table.
            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONNECTION_REQUEST",
                    req.getId(),
                    "REQUEST_ACCEPTED",
                    "{\"requestId\":\"" + req.getId() + "\",\"requesterId\":\"" + req.getRequesterId() + "\",\"hostId\":\"" + inv.getHostId() + "\"}"
            ));
            // Record an audit trail event showing the capacity usage after acceptance.
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "REQUEST_ACCEPTED", "CONNECTION_REQUEST", req.getId(),
                    "Host " + caller.getDisplayName() + " accepted request " + req.getId() + " (" + inv.getAcceptedCount() + "/" + inv.getCapacity() + " places used)."
            ));

        // Branch 2: Host declines the request (`PENDING -> DECLINED`).
        } else if ("DECLINED".equals(targetState)) {
            // Verify the caller is the invitation host.
            if (!inv.getHostId().equals(caller.getId())) {
                throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the invitation host can decline this request.");
            }
            // Transition state to `DECLINED` and bump version.
            req.setState("DECLINED");
            req.setVersion(req.getVersion() + 1);
            req.setUpdatedAt(Instant.now());
            requestRepository.save(req);

            // Enqueue a `REQUEST_DECLINED` outbox notification event.
            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONNECTION_REQUEST", req.getId(), "REQUEST_DECLINED",
                    "{\"requestId\":\"" + req.getId() + "\",\"requesterId\":\"" + req.getRequesterId() + "\"}"
            ));
        // Branch 3: Requester cancels their own pending request (`PENDING -> CANCELLED`).
        } else if ("CANCELLED".equals(targetState)) {
            // Verify the caller is the original requester.
            if (!req.getRequesterId().equals(caller.getId())) {
                throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the requester can cancel their pending request.");
            }
            // Transition state to `CANCELLED` and bump version.
            req.setState("CANCELLED");
            req.setVersion(req.getVersion() + 1);
            req.setUpdatedAt(Instant.now());
            requestRepository.save(req);
        } else {
            // Any other targetState string is invalid -> 422 Unprocessable Entity.
            throw PulseException.unprocessable("422: targetState must be ACCEPTED, DECLINED, or CANCELLED.");
        }

        // Return the updated connection request DTO map.
        return toConnectionRequestDto(req, inv, caller.getId());
    }

    /**
     * Records a participant's private follow-up confirmation (`Did the conversation happen? Was it useful?`).
     *
     * @param caller    authenticated participant submitting feedback
     * @param requestId ID of the accepted connection request
     * @param payload   JSON map containing `happened` (`YES`, `NO`, `NOT_YET`) and optional `useful` (`true`/`false`)
     * @return updated connection request DTO map with recomputed `bilateralOutcome`
     */
    @Transactional
    public Map<String, Object> recordConfirmation(UserEntity caller, String requestId, Map<String, Object> payload) {
        // Load the connection request and its parent invitation.
        ConnectionRequestEntity req = requestRepository.findById(requestId)
                .orElseThrow(() -> PulseException.notFound("404: Connection request not found: " + requestId));
        InvitationEntity inv = invitationRepository.findById(req.getInvitationId())
                .orElseThrow(() -> PulseException.notFound("404: Invitation not found."));

        // Guardrail 1: Only the two participants (requester and host) can access or record feedback!
        // Even an admin or third-party colleague receives 403 Forbidden here.
        if (!caller.getId().equals(req.getRequesterId()) && !caller.getId().equals(inv.getHostId())) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: Only the two participants of this conversation can access or submit private feedback."
            );
        }

        // Guardrail 2: Confirmations can only be submitted once the request has been `ACCEPTED`.
        if (!"ACCEPTED".equals(req.getState())) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Feedback can only be recorded for ACCEPTED requests."
            );
        }

        // Validate `happened` enum value (`YES`, `NO`, or `NOT_YET`).
        String happened = payload.get("happened") != null ? payload.get("happened").toString().toUpperCase() : "";
        if (!List.of("YES", "NO", "NOT_YET").contains(happened)) {
            throw PulseException.unprocessable("422: happened must be YES, NO, or NOT_YET.");
        }

        // When `happened` is `YES`, the participant must also specify whether it was `useful` (`true` or `false`).
        Boolean useful = null;
        if ("YES".equals(happened)) {
            if (!(payload.get("useful") instanceof Boolean b)) {
                throw PulseException.unprocessable("422: 'useful' (true/false) is required when happened is YES.");
            }
            useful = b;
        }

        // Upsert the caller's confirmation row for `(requestId, caller.getId())`.
        ParticipantConfirmationEntity conf = confirmationRepository
                .findByRequestIdAndUserId(requestId, caller.getId())
                .orElseGet(() -> {
                    ParticipantConfirmationEntity c = new ParticipantConfirmationEntity();
                    c.setId("conf_" + UUID.randomUUID().toString().substring(0, 8));
                    c.setRequestId(requestId);
                    c.setUserId(caller.getId());
                    return c;
                });

        // Update the confirmation fields and save to PostgreSQL.
        conf.setHappened(happened);
        conf.setUseful(useful);
        conf.setUpdatedAt(Instant.now());
        confirmationRepository.save(conf);

        // Return the updated request DTO (which recomputes `bilateralOutcome` across both participants).
        return toConnectionRequestDto(req, inv, caller.getId());
    }

    /**
     * Lists all connection requests where the caller is either the requester or the invitation host.
     *
     * @param caller authenticated user
     * @return list of connection request DTO maps
     */
    // `readOnly = true` optimizes the Hibernate session (no dirty checking) for read queries.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRequestsForUser(UserEntity caller) {
        // 1. Fetch all requests sent by this caller.
        List<ConnectionRequestEntity> sent = requestRepository.findByRequesterIdOrderByCreatedAtDesc(caller.getId());

        // 2. Find all invitation IDs where this caller is the host, then fetch all requests targeting those invitations.
        List<String> myInvitationIds = invitationRepository.findAll().stream()
                .filter(i -> i.getHostId().equals(caller.getId()))
                .map(InvitationEntity::getId)
                .toList();
        List<ConnectionRequestEntity> received = myInvitationIds.isEmpty()
                ? List.of()
                : requestRepository.findByInvitationIdInOrderByCreatedAtDesc(myInvitationIds);

        // 3. Merge received and sent requests into a LinkedHashMap keyed by request ID to preserve order and avoid duplicates.
        Map<String, ConnectionRequestEntity> merged = new LinkedHashMap<>();
        received.forEach(r -> merged.put(r.getId(), r));
        sent.forEach(r -> merged.put(r.getId(), r));

        // 4. Convert each entity into a rich DTO map containing requester/host details and bilateral confirmation status.
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConnectionRequestEntity req : merged.values()) {
            invitationRepository.findById(req.getInvitationId())
                    .ifPresent(inv -> result.add(toConnectionRequestDto(req, inv, caller.getId())));
        }
        return result;
    }

    /**
     * Maps a {@link ConnectionRequestEntity} and its parent {@link InvitationEntity} into a JSON-ready DTO map.
     * Note how `myConfirmation` only exposes the viewing user's own confirmation details, while
     * `bilateralOutcome` exposes the aggregate outcome (`USEFUL_CONFIRMED`, `UNKNOWN`, etc.).
     *
     * @param req          connection request entity
     * @param inv          parent invitation entity
     * @param viewerUserId user ID of the caller viewing the DTO
     * @return ordered DTO map
     */
    public Map<String, Object> toConnectionRequestDto(ConnectionRequestEntity req, InvitationEntity inv, String viewerUserId) {
        // Look up both user profiles for display names, regions, and functions.
        UserEntity requester = userRepository.findById(req.getRequesterId()).orElse(null);
        UserEntity host = userRepository.findById(inv.getHostId()).orElse(null);

        // Resolve the parent story's title from its published revision (or current revision fallback).
        String storyTitle = inv.getTopicLabel();
        Optional<ContentItemEntity> storyOpt = contentItemRepository.findById(inv.getStoryId());
        if (storyOpt.isPresent()) {
            int ver = storyOpt.get().getPublishedVersion() != null
                    ? storyOpt.get().getPublishedVersion()
                    : storyOpt.get().getCurrentVersion();
            storyTitle = contentRevisionRepository.findByContentIdAndVersionNumber(inv.getStoryId(), ver)
                    .map(ContentRevisionEntity::getTitle)
                    .orElse(inv.getTopicLabel());
        }

        // Load all confirmations for this request and locate the viewer's own confirmation (if any).
        List<ParticipantConfirmationEntity> confirmations = confirmationRepository.findByRequestId(req.getId());
        ParticipantConfirmationEntity myConf = confirmations.stream()
                .filter(c -> c.getUserId().equals(viewerUserId))
                .findFirst()
                .orElse(null);

        // Compute the bilateral usefulness outcome from both participants' confirmations.
        String bilateralOutcome = computeBilateralOutcome(confirmations, req.getRequesterId(), inv.getHostId());

        // Populate the response DTO map.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", req.getId());
        map.put("invitationId", inv.getId());
        map.put("storyId", inv.getStoryId());
        map.put("storyTitle", storyTitle);
        map.put("topicLabel", inv.getTopicLabel());
        map.put("requesterId", req.getRequesterId());
        map.put("requesterName", requester != null ? requester.getDisplayName() : req.getRequesterId());
        map.put("requesterRegion", requester != null ? requester.getRegion() : "GLOBAL");
        map.put("requesterFunction", requester != null ? requester.getFunctionName() : "Colleague");
        map.put("hostId", inv.getHostId());
        map.put("hostName", host != null ? host.getDisplayName() : inv.getHostId());
        map.put("hostRegion", host != null ? host.getRegion() : "GLOBAL");
        map.put("hostFunction", host != null ? host.getFunctionName() : "Colleague");
        map.put("messageText", req.getMessageText());
        map.put("aiAssisted", req.isAiAssisted());
        map.put("state", req.getState());
        map.put("version", req.getVersion());
        map.put("capacity", inv.getCapacity());
        map.put("acceptedCount", inv.getAcceptedCount());
        map.put("remainingPlaces", inv.getRemainingPlaces());
        map.put("createdAt", req.getCreatedAt().toString());
        map.put("myConfirmation", myConf != null
                ? Map.of("happened", myConf.getHappened(), "useful", myConf.getUseful() != null ? myConf.getUseful() : false)
                : null);
        map.put("confirmationsCount", confirmations.size());
        map.put("bilateralOutcome", bilateralOutcome);
        return map;
    }

    /**
     * Proposal Page 8, 12, 15 Invariant:
     * "Count a useful connection only when both confirm happened=true and useful=true;
     *  skipped or missing feedback stays unknown."
     *
     * @param confirmations list of confirmation entities recorded for a request
     * @param requesterId   user ID of the requester
     * @param hostId        user ID of the invitation host
     * @return `USEFUL_CONFIRMED`, `UNKNOWN`, `NOT_HAPPENED`, or `NOT_USEFUL`
     */
    public String computeBilateralOutcome(List<ParticipantConfirmationEntity> confirmations, String requesterId, String hostId) {
        // Find the requester's confirmation row, if submitted.
        ParticipantConfirmationEntity reqConf = confirmations.stream()
                .filter(c -> c.getUserId().equals(requesterId)).findFirst().orElse(null);
        // Find the host's confirmation row, if submitted.
        ParticipantConfirmationEntity hostConf = confirmations.stream()
                .filter(c -> c.getUserId().equals(hostId)).findFirst().orElse(null);

        // Rule 1: If either participant has not submitted feedback yet, outcome remains UNKNOWN.
        if (reqConf == null || hostConf == null) {
            return "UNKNOWN";
        }
        // Rule 2: If either participant answered `NOT_YET`, the conversation is still pending -> UNKNOWN.
        if ("NOT_YET".equals(reqConf.getHappened()) || "NOT_YET".equals(hostConf.getHappened())) {
            return "UNKNOWN";
        }
        // Rule 3: If either participant answered `NO` (did not happen), return NOT_HAPPENED.
        if ("NO".equals(reqConf.getHappened()) || "NO".equals(hostConf.getHappened())) {
            return "NOT_HAPPENED";
        }
        // Rule 4: ONLY when BOTH participants confirm `happened == "YES"` AND `useful == true`, count as USEFUL_CONFIRMED!
        if ("YES".equals(reqConf.getHappened()) && Boolean.TRUE.equals(reqConf.getUseful())
                && "YES".equals(hostConf.getHappened()) && Boolean.TRUE.equals(hostConf.getUseful())) {
            return "USEFUL_CONFIRMED";
        }
        // Rule 5: Otherwise, at least one participant marked `useful == false` -> NOT_USEFUL.
        return "NOT_USEFUL";
    }
}
