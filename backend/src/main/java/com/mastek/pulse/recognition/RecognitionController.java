package com.mastek.pulse.recognition;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.PulseException;
import com.mastek.pulse.common.UserEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: RecognitionController.java
 * Journey: Campfire Recognition, Appreciation, Personal Progress & Community
 *          Champions Leaderboard (Proposal Pages 16, 17, 19 & Stories CF-005..CF-007).
 * Why it is designed this way:
 *   Exposes the server-authoritative REST endpoints defined in `docs/campfire/NEW-CONTRACTS.md`
 *   and `docs/aidlc/openapi.yaml`. Notice that no endpoint accepts a client-supplied
 *   point total—all points and badges are computed transactionally on the server!
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1")
public class RecognitionController {

    private final IdentityService identityService;
    private final RecognitionService recognitionService;

    public RecognitionController(IdentityService identityService, RecognitionService recognitionService) {
        this.identityService = identityService;
        this.recognitionService = recognitionService;
    }

    @PostMapping("/content/{id}/appreciations")
    public Map<String, Object> appreciateStory(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return recognitionService.appreciateStory(caller, contentId);
    }

    @GetMapping("/recognition/me")
    public Map<String, Object> getMyRecognitionProgress(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "week", required = false) String week) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return recognitionService.getPersonalProgress(caller, week);
    }

    @GetMapping("/recognition/leaderboard")
    public Map<String, Object> getLeaderboard(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "week", required = false) String week,
            @RequestParam(value = "audienceId", required = false) String audienceId) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return recognitionService.getLeaderboard(caller, week, audienceId);
    }

    @PutMapping("/recognition/preferences")
    public Map<String, Object> updateRecognitionPreferences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        if (!(payload.get("showOnLeaderboard") instanceof Boolean show)) {
            throw PulseException.unprocessable("422: showOnLeaderboard (boolean) is required.");
        }
        return recognitionService.updatePreferences(caller, show);
    }
}
