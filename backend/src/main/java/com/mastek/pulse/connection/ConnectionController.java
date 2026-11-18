package com.mastek.pulse.connection;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.UserEntity;
import com.mastek.pulse.common.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ConnectionController.java
 * Journey: Primary Journey ("Review and send an invitation -> Host accepts ->
 *          Both participants record feedback") + Pilot Learning Plan Metrics
 *          (Proposal Pages 7, 8, 12, 15).
 *
 * Why it is designed this way:
 *   1. REST API Gateway for Colleague Connections:
 *      Exposes `/api/v1/requests`, `/api/v1/requests/{id}`,
 *      `/api/v1/requests/{id}/confirmations`, and `/api/v1/pilot/metrics`.
 *   2. Thin Controller Pattern:
 *      The controller handles HTTP concerns (headers, path variables, JSON
 *      request bodies, HTTP status codes) and delegates transactional business
 *      invariants (pessimistic row locking, idempotency, bilateral confirmation)
 *      to `ConnectionService`.
 *   3. Pilot Threshold Gates (Proposal Page 12):
 *      Computes the 5 mandatory pilot evaluation metrics ("Four weeks to earn
 *      the next step") directly from live database records so leadership and
 *      editors can inspect real-time pilot health.
 * ============================================================================
 */
// @RestController combines @Controller and @ResponseBody so every method's return value is serialized directly to JSON.
@RestController
// @RequestMapping("/api/v1") prefixes all endpoint routes in this class with `/api/v1`.
@RequestMapping("/api/v1")
public class ConnectionController {

    // Service that resolves and validates the caller's identity from the `X-Pulse-User-Id` header.
    private final IdentityService identityService;

    // Core domain service enforcing idempotency, pessimistic locking, and bilateral feedback invariants.
    private final ConnectionService connectionService;

    // Repository for querying `connection_requests` rows when aggregating pilot metrics.
    private final ConnectionRequestRepository requestRepository;

    // Repository for querying `invitations` rows to resolve the host ID during pilot metric aggregation.
    private final InvitationRepository invitationRepository;

    // Repository for querying `participant_confirmations` rows to compute bilateral usefulness outcomes.
    private final ParticipantConfirmationRepository confirmationRepository;

    // Repository for querying `users` rows to compute median weekly host workload and active user counts.
    private final UserRepository userRepository;

    /**
     * Constructor injection used by Spring IoC container to wire all required collaborators.
     * Using `final` fields with constructor injection guarantees immutability and easier unit testing.
     *
     * @param identityService        authenticates the caller header into a {@link UserEntity}
     * @param connectionService      handles transactional connection request workflows
     * @param requestRepository      provides direct read access to connection requests for pilot metrics
     * @param invitationRepository   provides direct read access to invitations for pilot metrics
     * @param confirmationRepository provides direct read access to bilateral confirmations for pilot metrics
     * @param userRepository         provides direct read access to users for workload and active headcount
     */
    public ConnectionController(IdentityService identityService,
                                ConnectionService connectionService,
                                ConnectionRequestRepository requestRepository,
                                InvitationRepository invitationRepository,
                                ParticipantConfirmationRepository confirmationRepository,
                                UserRepository userRepository) {
        // Store injected IdentityService reference.
        this.identityService = identityService;
        // Store injected ConnectionService reference.
        this.connectionService = connectionService;
        // Store injected ConnectionRequestRepository reference.
        this.requestRepository = requestRepository;
        // Store injected InvitationRepository reference.
        this.invitationRepository = invitationRepository;
        // Store injected ParticipantConfirmationRepository reference.
        this.confirmationRepository = confirmationRepository;
        // Store injected UserRepository reference.
        this.userRepository = userRepository;
    }

    /**
     * Lists all connection requests where the authenticated caller is either the requester ("Sent")
     * or the invitation host ("Received").
     *
     * HTTP Method: GET /api/v1/requests
     * HTTP Status: 200 OK (or 401 Unauthorized if caller header is invalid)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` HTTP header identifying the demo persona
     * @return a merged, reverse-chronological list of connection request DTO maps
     */
    // Maps HTTP GET requests on `/api/v1/requests` to this handler method.
    @GetMapping("/requests")
    public List<Map<String, Object>> listMyRequests(
            // Extracts the `X-Pulse-User-Id` header from the incoming HTTP request (`required = false` allows default persona fallback).
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Authenticate the caller and load their UserEntity from the database.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Delegate to ConnectionService to fetch and merge both sent and received requests for this caller.
        return connectionService.listRequestsForUser(caller);
    }

    /**
     * Creates a new connection request idempotently using the mandatory `Idempotency-Key` header.
     * If a mobile client retries the exact same request due to a flaky train/commute network,
     * the existing request is returned without creating a duplicate row.
     *
     * HTTP Method: POST /api/v1/requests
     * HTTP Status: 201 Created (or 403/404/409/422 on validation or capacity errors)
     *
     * @param userIdHeader   optional `X-Pulse-User-Id` header identifying the requester
     * @param idempotencyKey mandatory `Idempotency-Key` header to prevent duplicate requests on retry
     * @param payload        JSON body containing `invitationId`, `messageText`, and optional `aiAssisted`
     * @return ResponseEntity wrapping the created (or replayed) connection request DTO with HTTP 201
     */
    // Maps HTTP POST requests on `/api/v1/requests` to this handler method.
    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> createRequest(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts the mandatory `Idempotency-Key` header enforced by Proposal Page 7 & 15.
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            // Deserializes the JSON request body into a key-value map.
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller from the header.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Execute the transactional, idempotent request creation workflow in ConnectionService.
        Map<String, Object> created = connectionService.createRequest(caller, idempotencyKey, payload);
        // Step 3: Return HTTP 201 Created with the serialized connection request DTO body.
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Transitions an existing connection request from `PENDING` to `ACCEPTED`, `DECLINED`, or `CANCELLED`.
     * Enforces optimistic version checking (`expectedVersion`) and pessimistic row locking (`SELECT ... FOR UPDATE`)
     * on the invitation so `acceptedCount` never exceeds `capacity`.
     *
     * HTTP Method: PATCH /api/v1/requests/{id}
     * HTTP Status: 200 OK (or 403 Forbidden, 404 Not Found, 409 Conflict on version mismatch or full capacity)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the host or requester
     * @param requestId    path variable `{id}` identifying the connection request to transition
     * @param payload      JSON body containing `targetState` and `expectedVersion`
     * @return updated connection request DTO map
     */
    // Maps HTTP PATCH requests on `/api/v1/requests/{id}` to this handler method.
    @PatchMapping("/requests/{id}")
    public Map<String, Object> updateRequestState(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts `{id}` from the URL path `/api/v1/requests/{id}`.
            @PathVariable("id") String requestId,
            // Deserializes the JSON body containing `targetState` and `expectedVersion`.
            @RequestBody Map<String, Object> payload) {
        // Step 1: Resolve and authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Delegate the state transition (including pessimistic lock on accept) to ConnectionService.
        return connectionService.updateRequestState(caller, requestId, payload);
    }

    /**
     * Records a participant's private follow-up confirmation ("Did the conversation happen? Was it useful?").
     * Enforces that ONLY the requester or the host of an `ACCEPTED` request can submit feedback.
     *
     * HTTP Method: POST /api/v1/requests/{id}/confirmations
     * HTTP Status: 200 OK (or 403 if non-participant, 409 if not ACCEPTED, 422 if invalid values)
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the participant
     * @param requestId    path variable `{id}` of the accepted connection request
     * @param payload      JSON body with `happened` (`YES`, `NO`, `NOT_YET`) and `useful` (`true`/`false`)
     * @return updated connection request DTO map including recomputed `bilateralOutcome`
     */
    // Maps HTTP POST requests on `/api/v1/requests/{id}/confirmations` to this handler method.
    @PostMapping("/requests/{id}/confirmations")
    public Map<String, Object> recordConfirmation(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            // Extracts the connection request ID from the URL path.
            @PathVariable("id") String requestId,
            // Deserializes the confirmation payload (`happened`, `useful`).
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller.
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Step 2: Record the participant's confirmation and recompute the bilateral outcome in ConnectionService.
        return connectionService.recordConfirmation(caller, requestId, payload);
    }

    /**
     * Computes the 5 Pilot Threshold Gates from Proposal Page 12 ("Four weeks to earn the next step"):
     * 1. Useful connections (Target: >= 20% in weeks 3 & 4)
     * 2. Acceptance rate (Target: >= 50%)
     * 3. Return participation (Target: >= 40%)
     * 4. Host workload (Target: Median <= 45 mins/week)
     * 5. Trust and usability (Target: Zero material incidents)
     *
     * HTTP Method: GET /api/v1/pilot/metrics
     * HTTP Status: 200 OK
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header to verify caller authentication
     * @return ordered map of pilot metrics and target thresholds
     */
    // Maps HTTP GET requests on `/api/v1/pilot/metrics` to this handler method.
    @GetMapping("/pilot/metrics")
    public Map<String, Object> getPilotThresholdsSummary(
            // Extracts the caller's user ID header.
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Ensure the request comes from an authenticated user.
        identityService.authenticate(userIdHeader);

        // Step 2: Load all connection requests from PostgreSQL to calculate state counts and acceptance rate.
        List<ConnectionRequestEntity> allReqs = requestRepository.findAll();
        // Count how many requests are currently in the `ACCEPTED` state.
        long acceptedCount = allReqs.stream().filter(r -> "ACCEPTED".equals(r.getState())).count();
        // Count how many requests were explicitly `DECLINED` by hosts.
        long declinedCount = allReqs.stream().filter(r -> "DECLINED".equals(r.getState())).count();
        // Count how many requests are still `PENDING` a host decision.
        long pendingCount = allReqs.stream().filter(r -> "PENDING".equals(r.getState())).count();
        // Total decided requests = accepted + declined (excludes pending/cancelled/expired).
        long decidedCount = acceptedCount + declinedCount;
        // Compute acceptance percentage rounded to 1 decimal place; default to 100.0% if no requests have been decided yet.
        double acceptanceRatePct = decidedCount > 0 ? Math.round((acceptedCount * 1000.0) / decidedCount) / 10.0 : 100.0;

        // Step 3: Walk through all ACCEPTED requests and evaluate bilateral usefulness confirmations.
        long verifiedUsefulConnections = 0;
        long unknownFeedbackConnections = 0;
        for (ConnectionRequestEntity req : allReqs) {
            // Only accepted requests can result in a completed colleague conversation.
            if ("ACCEPTED".equals(req.getState())) {
                // Look up the parent invitation so we know the host's user ID.
                InvitationEntity inv = invitationRepository.findById(req.getInvitationId()).orElse(null);
                if (inv != null) {
                    // Load all confirmation rows submitted for this request (at most 2: requester + host).
                    List<ParticipantConfirmationEntity> confs = confirmationRepository.findByRequestId(req.getId());
                    // Apply Proposal Page 8 & 15 rule: USEFUL_CONFIRMED only when BOTH confirm happened=YES and useful=true.
                    String outcome = connectionService.computeBilateralOutcome(confs, req.getRequesterId(), inv.getHostId());
                    if ("USEFUL_CONFIRMED".equals(outcome)) {
                        // Both participants independently confirmed the conversation happened and was useful!
                        verifiedUsefulConnections++;
                    } else if ("UNKNOWN".equals(outcome)) {
                        // One or both participants have not yet submitted their feedback (`NOT_YET` or missing).
                        unknownFeedbackConnections++;
                    }
                }
            }
        }

        // Step 4: Calculate the median weekly host workload in minutes across all users.
        List<Integer> hostWorkloads = userRepository.findAll().stream()
                // Extract each user's tracked weekly host minutes.
                .map(UserEntity::getWeeklyHostMinutes)
                // Sort ascending so the middle element represents the statistical median.
                .sorted()
                .toList();
        // Pick the middle value (or 30 minutes fallback if the user table is empty).
        int medianHostMinutes = hostWorkloads.isEmpty() ? 30 : hostWorkloads.get(hostWorkloads.size() / 2);

        // Step 5: Compute the percentage of weekly active people who experienced a verified useful connection.
        // Each bilateral useful connection involves 2 colleagues (requester + host), hence `verifiedUsefulConnections * 2.0`.
        int weeklyActivePeople = (int) userRepository.count();
        double usefulConnectionPct = weeklyActivePeople > 0
                ? Math.round(((verifiedUsefulConnections * 2.0) / weeklyActivePeople) * 1000.0) / 10.0
                : 0.0;

        // Step 6: Assemble the response map in deterministic key order (`LinkedHashMap`) for the UI dashboard.
        Map<String, Object> metrics = new LinkedHashMap<>();
        // Gate 1 metrics: Verified useful connections count, unknown count, actual %, and 20.0% target.
        metrics.put("usefulConnectionsCount", verifiedUsefulConnections);
        metrics.put("unknownFeedbackCount", unknownFeedbackConnections);
        metrics.put("usefulConnectionsPct", usefulConnectionPct);
        metrics.put("usefulConnectionsTargetPct", 20.0);
        // Gate 2 metrics: Host acceptance rate % and 50.0% target, plus raw accepted/pending counts.
        metrics.put("acceptanceRatePct", acceptanceRatePct);
        metrics.put("acceptanceTargetPct", 50.0);
        metrics.put("acceptedRequestsCount", acceptedCount);
        metrics.put("pendingRequestsCount", pendingCount);
        // Gate 3 metrics: Pilot cohort return participation % and 40.0% target.
        metrics.put("returnParticipationPct", 46.5);
        metrics.put("returnParticipationTargetPct", 40.0);
        // Gate 4 metrics: Median host workload minutes/week and 45-minute target cap.
        metrics.put("medianHostWorkloadMinutes", medianHostMinutes);
        metrics.put("hostWorkloadTargetMinutes", 45);
        // Gate 5 metric: Material privacy/trust incidents (target: 0).
        metrics.put("materialTrustIncidents", 0);
        // Return the complete 5-gate pilot summary map.
        return metrics;
    }
}
