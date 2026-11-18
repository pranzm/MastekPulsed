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
 * Module: `challenge/` (Weekly Campfire Connection Challenges)
 *
 * 1. WHAT THIS FILE DOES:
 *    This Spring MVC REST controller exposes the HTTP endpoints under
 *    `/api/v1/challenges` that let employees view the current week's Campfire
 *    challenge prompt and submit their own story response.
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 16 ("Beyond My Job Title" Weekly Connection Challenge).
 *    - Implementation Story CF-002 (Challenge Discovery & Submission) and Slice D4.
 *    - OpenAPI 3.1 Operations:
 *      a) `GET /api/v1/challenges/current`
 *      b) `POST /api/v1/challenges/{id}/entries`
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Thin Controller Pattern: The controller only extracts HTTP headers,
 *      path variables, and request bodies, delegates authentication to
 *      `IdentityService`, and passes domain execution to `ChallengeService`.
 *    - Mandatory `Idempotency-Key` Header on `POST`: Mobile or browser clients
 *      on flaky networks might retry a story submission. By requiring an
 *      `Idempotency-Key` header, the backend guarantees a retry never creates
 *      duplicate entries or throws an unexpected error when replaying the exact
 *      same request.
 *    - Zero Points on Submission: Submitting an entry returns HTTP `201 Created`
 *      with `pointsAwarded: 0` and status `IN_REVIEW` (or `DRAFT`). Points (+20)
 *      are awarded ONLY after human editorial review and publication.
 * ============================================================================
 */
@RestController // Marks this class as a Spring REST controller whose methods return JSON directly in the HTTP response body.
@RequestMapping("/api/v1/challenges") // Prefixes every route in this controller with `/api/v1/challenges`.
public class ChallengeController {

    /**
     * Central identity & authorization service used to resolve the calling employee
     * from the `X-Pulse-User-Id` header (in demo mode) or reject unauthenticated requests.
     */
    private final IdentityService identityService;

    /**
     * Domain service that owns challenge lookup, audience filtering, active-window
     * validation, idempotency verification, and story/revision persistence.
     */
    private final ChallengeService challengeService;

    /**
     * Constructor-based dependency injection (recommended in Spring Boot over `@Autowired`
     * fields because dependencies are `final`, immutable, and easy to mock in unit tests).
     *
     * @param identityService resolves the authenticated `UserEntity`
     * @param challengeService executes challenge discovery and entry submission logic
     */
    public ChallengeController(IdentityService identityService, ChallengeService challengeService) {
        this.identityService = identityService;
        this.challengeService = challengeService;
    }

    /**
     * Returns the current active weekly Campfire challenge visible to the caller's
     * permitted audiences (e.g., `aud_pilot`), along with the caller's own entry
     * status (`myEntry`) if they have already saved a draft or submitted a response.
     *
     * HTTP Route: `GET /api/v1/challenges/current`
     * Success Status: `200 OK`
     * Error Statuses: `401 Unauthorized` if caller identity is missing/invalid in live mode.
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the persona in demo mode
     * @return JSON map matching the `ChallengeDto` contract in `openapi.yaml`
     */
    @GetMapping("/current")
    public Map<String, Object> getCurrentChallenge(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Step 1: Authenticate the HTTP caller and load their UserEntity from PostgreSQL.
        UserEntity caller = identityService.authenticate(userIdHeader);

        // Step 2: Delegate audience filtering and challenge + myEntry assembly to ChallengeService.
        return challengeService.getCurrentChallenge(caller);
    }

    /**
     * Submits (or resubmits after `REQUEST_CHANGES`) the caller's story response
     * for a specific weekly challenge (`{id}`).
     *
     * HTTP Route: `POST /api/v1/challenges/{id}/entries`
     * Required Header: `Idempotency-Key` (unique string per user submission attempt)
     * Success Status: `201 Created`
     * Error Statuses:
     *   - `401 Unauthorized`: Unknown or missing caller identity
     *   - `403 Forbidden`: Caller lacks `CREATOR` role or is outside the challenge's `audienceId`
     *   - `404 Not Found`: Challenge ID does not exist
     *   - `409 Conflict`: Challenge window is closed (`CHALLENGE_CLOSED`), user already submitted
     *     an active entry (`DUPLICATE_ENTRY`), or `Idempotency-Key` was reused with a different body
     *     (`IDEMPOTENCY_CONFLICT`)
     *   - `422 Unprocessable Entity`: Blank `Idempotency-Key`, or `title`/`bodyText` length out of bounds
     *
     * @param userIdHeader   optional `X-Pulse-User-Id` header identifying the persona in demo mode
     * @param idempotencyKey mandatory client-generated key to prevent duplicate submissions on network retry
     * @param challengeId    path variable `{id}` identifying the target challenge (e.g. `ch_beyond_01`)
     * @param payload        JSON body containing `title`, `bodyText`, and optional `submitForReview` boolean
     * @return `ResponseEntity` with HTTP `201 Created` and the `ChallengeEntryDto` body
     */
    @PostMapping("/{id}/entries")
    public ResponseEntity<Map<String, Object>> submitChallengeEntry(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @PathVariable("id") String challengeId,
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the caller from the request header.
        UserEntity caller = identityService.authenticate(userIdHeader);

        // Step 2: Execute transactional submission (role check, audience check, time window check,
        //         idempotency hash check, and creation/update of ContentItem + ContentRevision + ChallengeEntry).
        Map<String, Object> entry = challengeService.submitEntry(caller, challengeId, idempotencyKey, payload);

        // Step 3: Return HTTP 201 Created with the serialized ChallengeEntryDto map.
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }
}
