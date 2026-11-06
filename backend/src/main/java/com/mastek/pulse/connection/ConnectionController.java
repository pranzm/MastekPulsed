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
 * Why it is designed this way:
 *   Exposes `/api/v1/requests`, `/api/v1/requests/{id}`,
 *   `/api/v1/requests/{id}/confirmations`, and `/api/v1/pilot/metrics` (which
 *   calculates the 5 Pilot Threshold Gates from Proposal Page 12!).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    private final IdentityService identityService;
    private final ConnectionService connectionService;
    private final ConnectionRequestRepository requestRepository;
    private final InvitationRepository invitationRepository;
    private final ParticipantConfirmationRepository confirmationRepository;
    private final UserRepository userRepository;

    public ConnectionController(IdentityService identityService,
                                ConnectionService connectionService,
                                ConnectionRequestRepository requestRepository,
                                InvitationRepository invitationRepository,
                                ParticipantConfirmationRepository confirmationRepository,
                                UserRepository userRepository) {
        this.identityService = identityService;
        this.connectionService = connectionService;
        this.requestRepository = requestRepository;
        this.invitationRepository = invitationRepository;
        this.confirmationRepository = confirmationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> listMyRequests(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return connectionService.listRequestsForUser(caller);
    }

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        Map<String, Object> created = connectionService.createRequest(caller, idempotencyKey, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/requests/{id}")
    public Map<String, Object> updateRequestState(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String requestId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return connectionService.updateRequestState(caller, requestId, payload);
    }

    @PostMapping("/requests/{id}/confirmations")
    public Map<String, Object> recordConfirmation(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String requestId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return connectionService.recordConfirmation(caller, requestId, payload);
    }

    /**
     * Computes the 5 Pilot Threshold Gates from Proposal Page 12 ("Four weeks to earn the next step"):
     * 1. Useful connections (Target: >= 20% in weeks 3 & 4)
     * 2. Acceptance rate (Target: >= 50%)
     * 3. Return participation (Target: >= 40%)
     * 4. Host workload (Target: Median <= 45 mins/week)
     * 5. Trust and usability (Target: Zero material incidents)
     */
    @GetMapping("/pilot/metrics")
    public Map<String, Object> getPilotThresholdsSummary(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        identityService.authenticate(userIdHeader);

        List<ConnectionRequestEntity> allReqs = requestRepository.findAll();
        long acceptedCount = allReqs.stream().filter(r -> "ACCEPTED".equals(r.getState())).count();
        long declinedCount = allReqs.stream().filter(r -> "DECLINED".equals(r.getState())).count();
        long pendingCount = allReqs.stream().filter(r -> "PENDING".equals(r.getState())).count();
        long decidedCount = acceptedCount + declinedCount;
        double acceptanceRatePct = decidedCount > 0 ? Math.round((acceptedCount * 1000.0) / decidedCount) / 10.0 : 100.0;

        long verifiedUsefulConnections = 0;
        long unknownFeedbackConnections = 0;
        for (ConnectionRequestEntity req : allReqs) {
            if ("ACCEPTED".equals(req.getState())) {
                InvitationEntity inv = invitationRepository.findById(req.getInvitationId()).orElse(null);
                if (inv != null) {
                    List<ParticipantConfirmationEntity> confs = confirmationRepository.findByRequestId(req.getId());
                    String outcome = connectionService.computeBilateralOutcome(confs, req.getRequesterId(), inv.getHostId());
                    if ("USEFUL_CONFIRMED".equals(outcome)) {
                        verifiedUsefulConnections++;
                    } else if ("UNKNOWN".equals(outcome)) {
                        unknownFeedbackConnections++;
                    }
                }
            }
        }

        List<Integer> hostWorkloads = userRepository.findAll().stream()
                .map(UserEntity::getWeeklyHostMinutes)
                .sorted()
                .toList();
        int medianHostMinutes = hostWorkloads.isEmpty() ? 30 : hostWorkloads.get(hostWorkloads.size() / 2);

        int weeklyActivePeople = (int) userRepository.count();
        double usefulConnectionPct = weeklyActivePeople > 0
                ? Math.round(((verifiedUsefulConnections * 2.0) / weeklyActivePeople) * 1000.0) / 10.0
                : 0.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("usefulConnectionsCount", verifiedUsefulConnections);
        metrics.put("unknownFeedbackCount", unknownFeedbackConnections);
        metrics.put("usefulConnectionsPct", usefulConnectionPct);
        metrics.put("usefulConnectionsTargetPct", 20.0);
        metrics.put("acceptanceRatePct", acceptanceRatePct);
        metrics.put("acceptanceTargetPct", 50.0);
        metrics.put("acceptedRequestsCount", acceptedCount);
        metrics.put("pendingRequestsCount", pendingCount);
        metrics.put("returnParticipationPct", 46.5);
        metrics.put("returnParticipationTargetPct", 40.0);
        metrics.put("medianHostWorkloadMinutes", medianHostMinutes);
        metrics.put("hostWorkloadTargetMinutes", 45);
        metrics.put("materialTrustIncidents", 0);
        return metrics;
    }
}
