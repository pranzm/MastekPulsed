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
@Service
public class ConnectionService {

    private final InvitationRepository invitationRepository;
    private final ConnectionRequestRepository requestRepository;
    private final ParticipantConfirmationRepository confirmationRepository;
    private final ContentItemRepository contentItemRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditEventRepository auditEventRepository;
    private final IdentityService identityService;

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
     * Creates a connection request idempotently using the caller-scoped Idempotency-Key.
     */
    @Transactional
    public Map<String, Object> createRequest(UserEntity requester, String idempotencyKey, Map<String, Object> payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PulseException.unprocessable("422: Idempotency-Key header is required for safe retries.");
        }

        // 1. Check idempotency key first (safe mobile retry!)
        Optional<ConnectionRequestEntity> existingByIdem = requestRepository
                .findByRequesterIdAndIdempotencyKey(requester.getId(), idempotencyKey.trim());
        if (existingByIdem.isPresent()) {
            ConnectionRequestEntity req = existingByIdem.get();
            InvitationEntity inv = invitationRepository.findById(req.getInvitationId()).orElseThrow();
            return toConnectionRequestDto(req, inv, requester.getId());
        }

        String invitationId = (String) payload.get("invitationId");
        String messageText = (String) payload.get("messageText");
        boolean aiAssisted = Boolean.TRUE.equals(payload.get("aiAssisted"));

        if (invitationId == null || invitationId.isBlank()) {
            throw PulseException.unprocessable("422: invitationId is required.");
        }
        if (messageText == null || messageText.trim().length() < 5) {
            throw PulseException.unprocessable("422: messageText must be at least 5 characters.");
        }

        InvitationEntity inv = invitationRepository.findById(invitationId)
                .orElseThrow(() -> PulseException.notFound("404: Invitation not found: " + invitationId));

        ContentItemEntity story = contentItemRepository.findById(inv.getStoryId())
                .orElseThrow(() -> PulseException.notFound("404: Story not found for invitation."));

        // Enforce audience permission before allowing request!
        identityService.requireAudienceAccess(requester, story.getAudienceId());

        if (inv.getHostId().equals(requester.getId())) {
            throw PulseException.unprocessable("422: Hosts cannot send an invitation request to themselves.");
        }

        UserEntity host = userRepository.findById(inv.getHostId()).orElse(null);
        boolean hostPaused = host != null && host.isHostPaused();
        if (!inv.isOpenForRequests(hostPaused)) {
            throw PulseException.conflict(
                    "CAPACITY_FULL",
                    "409: Invitation is not open for requests (Status: " + inv.computeStatusLabel(hostPaused) + ")."
            );
        }

        Optional<ConnectionRequestEntity> duplicateOnInvitation = requestRepository
                .findByInvitationIdAndRequesterId(invitationId, requester.getId());
        if (duplicateOnInvitation.isPresent()) {
            throw PulseException.conflict(
                    "CAPACITY_FULL",
                    "409: You have already sent a request for this invitation."
            );
        }

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

        outboxEventRepository.save(OutboxEventEntity.create(
                "CONNECTION_REQUEST",
                req.getId(),
                "REQUEST_CREATED",
                "{\"requestId\":\"" + req.getId() + "\",\"hostId\":\"" + inv.getHostId() + "\",\"requesterId\":\"" + requester.getId() + "\"}"
        ));

        return toConnectionRequestDto(req, inv, requester.getId());
    }

    /**
     * Executes state transitions (`ACCEPTED`, `DECLINED`, `CANCELLED`) including
     * the exact 4-step ACCEPT FLOW / ONE TRANSACTION from Proposal Page 15.
     */
    @Transactional
    public Map<String, Object> updateRequestState(UserEntity caller, String requestId, Map<String, Object> payload) {
        String targetState = payload.get("targetState") != null ? payload.get("targetState").toString().toUpperCase() : "";
        int expectedVersion = payload.get("expectedVersion") instanceof Number n ? n.intValue() : 1;

        ConnectionRequestEntity req = requestRepository.findById(requestId)
                .orElseThrow(() -> PulseException.notFound("404: Connection request not found: " + requestId));

        // STEP 2 OF PAGE 15: Lock the invitation row (`SELECT ... FOR UPDATE`)
        InvitationEntity inv = invitationRepository.findByIdWithPessimisticLock(req.getInvitationId())
                .orElseThrow(() -> PulseException.notFound("404: Invitation not found."));

        ContentItemEntity story = contentItemRepository.findById(inv.getStoryId())
                .orElseThrow(() -> PulseException.notFound("404: Story not found."));

        // STEP 1 OF PAGE 15: Re-check audience, version, and ownership
        identityService.requireAudienceAccess(caller, story.getAudienceId());

        if (req.getVersion() != expectedVersion) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Request version mismatch (expected " + expectedVersion + ", actual " + req.getVersion() + ")."
            );
        }
        if (!"PENDING".equals(req.getState())) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Request is no longer PENDING (current state: " + req.getState() + ")."
            );
        }

        if ("ACCEPTED".equals(targetState)) {
            if (!inv.getHostId().equals(caller.getId())) {
                throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the invitation host can accept this request.");
            }
            if (inv.isPaused() || caller.isHostPaused() || inv.getExpiresAt().isBefore(Instant.now())) {
                throw PulseException.conflict("INVITATION_EXPIRED_OR_PAUSED", "409: Invitation is paused or has expired.");
            }
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

            requestRepository.save(req);
            invitationRepository.save(inv);

            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONNECTION_REQUEST",
                    req.getId(),
                    "REQUEST_ACCEPTED",
                    "{\"requestId\":\"" + req.getId() + "\",\"requesterId\":\"" + req.getRequesterId() + "\",\"hostId\":\"" + inv.getHostId() + "\"}"
            ));
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "REQUEST_ACCEPTED", "CONNECTION_REQUEST", req.getId(),
                    "Host " + caller.getDisplayName() + " accepted request " + req.getId() + " (" + inv.getAcceptedCount() + "/" + inv.getCapacity() + " places used)."
            ));

        } else if ("DECLINED".equals(targetState)) {
            if (!inv.getHostId().equals(caller.getId())) {
                throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the invitation host can decline this request.");
            }
            req.setState("DECLINED");
            req.setVersion(req.getVersion() + 1);
            req.setUpdatedAt(Instant.now());
            requestRepository.save(req);

            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONNECTION_REQUEST", req.getId(), "REQUEST_DECLINED",
                    "{\"requestId\":\"" + req.getId() + "\",\"requesterId\":\"" + req.getRequesterId() + "\"}"
            ));
        } else if ("CANCELLED".equals(targetState)) {
            if (!req.getRequesterId().equals(caller.getId())) {
                throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the requester can cancel their pending request.");
            }
            req.setState("CANCELLED");
            req.setVersion(req.getVersion() + 1);
            req.setUpdatedAt(Instant.now());
            requestRepository.save(req);
        } else {
            throw PulseException.unprocessable("422: targetState must be ACCEPTED, DECLINED, or CANCELLED.");
        }

        return toConnectionRequestDto(req, inv, caller.getId());
    }

    /**
     * Records a participant's private follow-up confirmation (`Did the conversation happen? Was it useful?`).
     */
    @Transactional
    public Map<String, Object> recordConfirmation(UserEntity caller, String requestId, Map<String, Object> payload) {
        ConnectionRequestEntity req = requestRepository.findById(requestId)
                .orElseThrow(() -> PulseException.notFound("404: Connection request not found: " + requestId));
        InvitationEntity inv = invitationRepository.findById(req.getInvitationId())
                .orElseThrow(() -> PulseException.notFound("404: Invitation not found."));

        // Only the two participants (requester and host) can access or record feedback!
        if (!caller.getId().equals(req.getRequesterId()) && !caller.getId().equals(inv.getHostId())) {
            throw PulseException.forbidden(
                    "FORBIDDEN_ACCESS",
                    "403: Only the two participants of this conversation can access or submit private feedback."
            );
        }

        if (!"ACCEPTED".equals(req.getState())) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Feedback can only be recorded for ACCEPTED requests."
            );
        }

        String happened = payload.get("happened") != null ? payload.get("happened").toString().toUpperCase() : "";
        if (!List.of("YES", "NO", "NOT_YET").contains(happened)) {
            throw PulseException.unprocessable("422: happened must be YES, NO, or NOT_YET.");
        }

        Boolean useful = null;
        if ("YES".equals(happened)) {
            if (!(payload.get("useful") instanceof Boolean b)) {
                throw PulseException.unprocessable("422: 'useful' (true/false) is required when happened is YES.");
            }
            useful = b;
        }

        ParticipantConfirmationEntity conf = confirmationRepository
                .findByRequestIdAndUserId(requestId, caller.getId())
                .orElseGet(() -> {
                    ParticipantConfirmationEntity c = new ParticipantConfirmationEntity();
                    c.setId("conf_" + UUID.randomUUID().toString().substring(0, 8));
                    c.setRequestId(requestId);
                    c.setUserId(caller.getId());
                    return c;
                });

        conf.setHappened(happened);
        conf.setUseful(useful);
        conf.setUpdatedAt(Instant.now());
        confirmationRepository.save(conf);

        return toConnectionRequestDto(req, inv, caller.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRequestsForUser(UserEntity caller) {
        List<ConnectionRequestEntity> sent = requestRepository.findByRequesterIdOrderByCreatedAtDesc(caller.getId());

        List<String> myInvitationIds = invitationRepository.findAll().stream()
                .filter(i -> i.getHostId().equals(caller.getId()))
                .map(InvitationEntity::getId)
                .toList();
        List<ConnectionRequestEntity> received = myInvitationIds.isEmpty()
                ? List.of()
                : requestRepository.findByInvitationIdInOrderByCreatedAtDesc(myInvitationIds);

        Map<String, ConnectionRequestEntity> merged = new LinkedHashMap<>();
        received.forEach(r -> merged.put(r.getId(), r));
        sent.forEach(r -> merged.put(r.getId(), r));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ConnectionRequestEntity req : merged.values()) {
            invitationRepository.findById(req.getInvitationId())
                    .ifPresent(inv -> result.add(toConnectionRequestDto(req, inv, caller.getId())));
        }
        return result;
    }

    public Map<String, Object> toConnectionRequestDto(ConnectionRequestEntity req, InvitationEntity inv, String viewerUserId) {
        UserEntity requester = userRepository.findById(req.getRequesterId()).orElse(null);
        UserEntity host = userRepository.findById(inv.getHostId()).orElse(null);

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

        List<ParticipantConfirmationEntity> confirmations = confirmationRepository.findByRequestId(req.getId());
        ParticipantConfirmationEntity myConf = confirmations.stream()
                .filter(c -> c.getUserId().equals(viewerUserId))
                .findFirst()
                .orElse(null);

        String bilateralOutcome = computeBilateralOutcome(confirmations, req.getRequesterId(), inv.getHostId());

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
     */
    public String computeBilateralOutcome(List<ParticipantConfirmationEntity> confirmations, String requesterId, String hostId) {
        ParticipantConfirmationEntity reqConf = confirmations.stream()
                .filter(c -> c.getUserId().equals(requesterId)).findFirst().orElse(null);
        ParticipantConfirmationEntity hostConf = confirmations.stream()
                .filter(c -> c.getUserId().equals(hostId)).findFirst().orElse(null);

        if (reqConf == null || hostConf == null) {
            return "UNKNOWN";
        }
        if ("NOT_YET".equals(reqConf.getHappened()) || "NOT_YET".equals(hostConf.getHappened())) {
            return "UNKNOWN";
        }
        if ("NO".equals(reqConf.getHappened()) || "NO".equals(hostConf.getHappened())) {
            return "NOT_HAPPENED";
        }
        if ("YES".equals(reqConf.getHappened()) && Boolean.TRUE.equals(reqConf.getUseful())
                && "YES".equals(hostConf.getHappened()) && Boolean.TRUE.equals(hostConf.getUseful())) {
            return "USEFUL_CONFIRMED";
        }
        return "NOT_USEFUL";
    }
}
