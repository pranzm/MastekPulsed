package com.mastek.pulse.challenge;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeController.java
 * Journey: Campfire Weekly Challenge Discovery & Submission (Story CF-002).
 * Why it is designed this way:
 *   Exposes `GET /api/v1/challenges/current` and `POST /api/v1/challenges/{id}/entries`
 *   with mandatory `Idempotency-Key` header support.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {

    private final IdentityService identityService;
    private final ChallengeService challengeService;

    public ChallengeController(IdentityService identityService, ChallengeService challengeService) {
        this.identityService = identityService;
        this.challengeService = challengeService;
    }

    @GetMapping("/current")
    public Map<String, Object> getCurrentChallenge(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return challengeService.getCurrentChallenge(caller);
    }

    @PostMapping("/{id}/entries")
    public ResponseEntity<Map<String, Object>> submitChallengeEntry(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @PathVariable("id") String challengeId,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        Map<String, Object> entry = challengeService.submitEntry(caller, challengeId, idempotencyKey, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }
}
