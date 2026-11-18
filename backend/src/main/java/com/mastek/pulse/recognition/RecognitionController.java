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
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    This Spring MVC REST controller exposes the 4 Campfire recognition and
 *    appreciation endpoints under `/api/v1`:
 *      a) `POST /api/v1/content/{id}/appreciations` — appreciate a colleague's story
 *      b) `GET  /api/v1/recognition/me`             — read caller's personal weekly progress & badges
 *      c) `GET  /api/v1/recognition/leaderboard`    — read Top-5 opted-in Community Champions
 *      d) `PUT  /api/v1/recognition/preferences`    — toggle caller's leaderboard opt-in/opt-out
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Pages 16, 17 & 19 (Point System, Weekly Cap of 60, Three Badges,
 *      and Optional Community Champions View with Opt-Out).
 *    - Implementation Stories CF-005, CF-006, CF-007 & Slices D4 / D5.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Server-Owned Points (Zero Client Point Injection):
 *      Notice that NO endpoint in this controller accepts a point value from the
 *      client! Clients only submit domain actions ("I appreciate story X") or
 *      privacy preferences (`showOnLeaderboard`), and `RecognitionService`
 *      calculates all points, caps, and badges on the server.
 *    - Audience-Scoped Leaderboard:
 *      `GET /api/v1/recognition/leaderboard` requires `audienceId` access and
 *      computes rankings exclusively from activity inside that permitted audience.
 * ============================================================================
 */
@RestController // Marks this class as a Spring REST controller returning JSON responses.
@RequestMapping("/api/v1") // Base path for all Campfire v1 API endpoints.
public class RecognitionController {

    /**
     * Authenticates the caller from the `X-Pulse-User-Id` header (in demo mode).
     */
    private final IdentityService identityService;

    /**
     * Transactional service that manages the recognition ledger, appreciations,
     * badge synchronization, personal progress, and leaderboard projection.
     */
    private final RecognitionService recognitionService;

    /**
     * Constructor-based dependency injection.
     */
    public RecognitionController(IdentityService identityService, RecognitionService recognitionService) {
        this.identityService = identityService;
        this.recognitionService = recognitionService;
    }

    /**
     * Records the caller's appreciation for a published story (`{id}`).
     *
     * Business Rules Enforced by `RecognitionService.appreciateStory`:
     *   - Target story must be `PUBLISHED` and in an audience the caller can access (`403` otherwise).
     *   - Caller cannot appreciate their own story (`422` self-appreciation disallowed).
     *   - Repeat appreciation of the same story by the same user is idempotent (`200 OK`,
     *     `alreadyAppreciated: true`, `giverPointsDelta: 0`, `receiverPointsDelta: 0`).
     *   - Giver earns `+5` points for their first 3 appreciated stories of the week (max `+15`).
     *   - Story author earns `+5` points for their first 5 distinct appreciating colleagues
     *     of the week (max `+25`).
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the caller in demo mode
     * @param contentId    path variable `{id}` identifying the published story
     * @return JSON map matching `AppreciationResultDto` in `openapi.yaml`
     */
    @PostMapping("/content/{id}/appreciations")
    public Map<String, Object> appreciateStory(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @PathVariable("id") String contentId) {
        // Step 1: Authenticate the calling employee.
        UserEntity caller = identityService.authenticate(userIdHeader);

        // Step 2: Record appreciation, evaluate giver/receiver caps, update badges, and return updated progress.
        return recognitionService.appreciateStory(caller, contentId);
    }

    /**
     * Returns the authenticated caller's private weekly recognition summary,
     * including total points (capped at 60), contribution points (0 or 20),
     * giving points (0..15), receiving points (0..25), distinct teams supported,
     * earned badges (`First Spark`, `Connection Maker`, `Community Favourite`),
     * and leaderboard visibility preference (`showOnLeaderboard`).
     *
     * Intern Note:
     *   Even if a user opts out of the public leaderboard (`showOnLeaderboard = false`),
     *   this endpoint still returns their complete personal progress!
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the caller
     * @param week         optional ISO Monday date (`YYYY-MM-DD`); defaults to current UTC week
     * @return JSON map matching `PersonalRecognitionDto` in `openapi.yaml`
     */
    @GetMapping("/recognition/me")
    public Map<String, Object> getMyRecognitionProgress(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "week", required = false) String week) {
        // Step 1: Authenticate the calling employee.
        UserEntity caller = identityService.authenticate(userIdHeader);

        // Step 2: Compute and return the caller's audience-scoped weekly recognition summary.
        return recognitionService.getPersonalProgress(caller, week);
    }

    /**
     * Returns the Top-5 opted-in Community Champions for the requested `audienceId`
     * and `week` (Monday 00:00 UTC boundary).
     *
     * Security & Privacy Rules:
     *   - Caller must be a member of `audienceId` (`403 Forbidden` if excluded).
     *   - Users who set `showOnLeaderboard = false` are excluded from the returned list.
     *   - Only points earned inside `audienceId` are counted so restricted-audience
     *     stories (`aud_uk_restricted`) can never be inferred from public rankings.
     *   - Tied scores share the same `rank` number and are ordered alphabetically by `displayName`.
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the caller
     * @param week         optional ISO Monday date (`YYYY-MM-DD`); defaults to current UTC week
     * @param audienceId   optional audience ID; defaults to `"aud_pilot"`
     * @return JSON map matching `LeaderboardDto` in `openapi.yaml`
     */
    @GetMapping("/recognition/leaderboard")
    public Map<String, Object> getLeaderboard(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "week", required = false) String week,
            @RequestParam(value = "audienceId", required = false) String audienceId) {
        // Step 1: Authenticate the calling employee.
        UserEntity caller = identityService.authenticate(userIdHeader);

        // Step 2: Verify audience access and compute the Top-5 opted-in leaderboard projection.
        return recognitionService.getLeaderboard(caller, week, audienceId);
    }

    /**
     * Updates whether the calling employee appears on the weekly Community Champions
     * leaderboard (`showOnLeaderboard: true | false`).
     *
     * @param userIdHeader optional `X-Pulse-User-Id` header identifying the caller
     * @param payload      JSON object containing boolean `showOnLeaderboard`
     * @return updated `PersonalRecognitionDto` reflecting the new preference
     */
    @PutMapping("/recognition/preferences")
    public Map<String, Object> updateRecognitionPreferences(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        // Step 1: Authenticate the calling employee.
        UserEntity caller = identityService.authenticate(userIdHeader);

        // Step 2: Validate that `showOnLeaderboard` is explicitly provided as a JSON boolean (422 otherwise).
        if (!(payload.get("showOnLeaderboard") instanceof Boolean show)) {
            throw PulseException.unprocessable("422: showOnLeaderboard (boolean) is required.");
        }

        // Step 3: Persist the preference and return the caller's updated personal recognition DTO.
        return recognitionService.updatePreferences(caller, show);
    }
}
