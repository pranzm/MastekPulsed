package com.mastek.pulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.PulseClock;
import com.mastek.pulse.common.UserEntity;
import com.mastek.pulse.common.UserRepository;
import com.mastek.pulse.recognition.RecognitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: CampfireChallengeAndRecognitionTest.java
 * Journey: Campfire D2, D3, D4, and D5 Verification Suite (17 November 2026).
 *
 * WHY IT IS DESIGNED THIS WAY:
 *   This end-to-end Spring Boot integration test suite boots the full application
 *   context (`@SpringBootTest`) and uses `MockMvc` (`@AutoConfigureMockMvc`) to
 *   exercise real HTTP requests through controllers, services, repositories, and
 *   Flyway-migrated database tables.
 *
 * WHAT THIS SUITE VERIFIES:
 *   1. D2 (`sharedVisibilityAndEditorialSecurityChecks`):
 *      - Role-based access control on `/api/v1/portal/queue` and `/api/v1/portal/audit-log`
 *      - Audience-scoped portal queue filtering (`story_08` in `aud_uk_restricted` hidden from Priya)
 *      - Author ownership checks on `submit` and `preview`
 *      - Denial (404) of direct employee reads on `DRAFT` or `IN_REVIEW` stories
 *      - Revision validation (`422` missing revision, `409 STALE_VERSION`)
 *      - Immutable `published_audience_id` snapshot isolation when a draft revision proposes a wider audience
 *   2. D4 (`challengeSubmissionPublicationRewardAndWithdrawalFlow`):
 *      - Active weekly challenge discovery (`GET /api/v1/challenges/current`)
 *      - Challenge entry submission with `Idempotency-Key` replay, `409 IDEMPOTENCY_CONFLICT`,
 *        and `409 DUPLICATE_ENTRY` guards
 *      - Reviewer `REQUEST_CHANGES` -> Author resubmit -> Reviewer `APPROVE` (0 points at approval!)
 *      - Publisher `IMMEDIATE` release -> Exactly-once `+20` points and `"First Spark"` badge
 *      - Publisher `withdraw` -> Compensating `-20` reversal row returning Asha to 0 points
 *   3. D5 (`appreciationCapsBadgesLeaderboardAndConcurrency`):
 *      - Self-appreciation block (`422`) and unpublished story appreciation block (`404`)
 *      - Multi-threaded (`ExecutorService` 4 threads) concurrent appreciation race proving
 *        pessimistic locking enforces the 15-point weekly giving cap and `"Connection Maker"` badge
 *      - 5 distinct colleagues appreciating Asha's story -> 25-point receiving cap,
 *        `"Community Favourite"` badge, and 60-point weekly maximum (`20 + 15 + 25`)
 *      - Leaderboard ranking, opt-out privacy (`showOnLeaderboard = false`), and Monday 00:00 UTC rollover
 * ============================================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampfireChallengeAndRecognitionTest {

    // Simulates HTTP requests against Spring MVC controllers without needing an external HTTP port
    @Autowired
    private MockMvc mockMvc;

    // Serializes Java Maps to JSON request bodies and parses JSON responses
    @Autowired
    private ObjectMapper objectMapper;

    // Executes SQL cleanup queries in `@BeforeEach` so every test starts from the exact V2/V3 seed baseline
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Injectable clock wrapper allowing tests to freeze time or jump to next Monday 00:00 UTC
    @Autowired
    private PulseClock pulseClock;

    // Resolves the active user from `X-Pulse-User-Id` headers
    @Autowired
    private IdentityService identityService;

    // Direct reference to `RecognitionService` used to launch 4 concurrent threads in the race test
    @Autowired
    private RecognitionService recognitionService;

    // Looks up `UserEntity` instances for service-level concurrency testing
    @Autowired
    private UserRepository userRepository;

    /**
     * Runs before every `@Test` method to freeze the application clock to Tuesday
     * `2026-11-17T09:00:00Z` (inside the `2026-11-16` UTC challenge week) and restore
     * all database tables to their clean V2/V3 Flyway seed state.
     */
    @BeforeEach
    void resetCampfireState() {
        // Pin clock to 17 Nov 2026 09:00 UTC so weekly window calculations (`2026-11-16`) are deterministic
        pulseClock.setClock(Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneOffset.UTC));
        identityService.setRuntimeRequireExplicitHeader(null);

        // Clean test-generated rows while preserving V2/V3 seed baseline awards for Daniel, Elena, and Sophie
        jdbcTemplate.update("DELETE FROM weekly_badges WHERE id NOT IN ('bdg_seed_daniel', 'bdg_seed_elena', 'bdg_seed_sophie')");
        // Delete compensating reversal rows first (because `reversal_of` self-references `recognition_events.id`)
        jdbcTemplate.update("DELETE FROM recognition_events WHERE reversal_of IS NOT NULL");
        jdbcTemplate.update("DELETE FROM recognition_events WHERE id NOT IN ('rec_seed_daniel', 'rec_seed_elena', 'rec_seed_sophie')");
        jdbcTemplate.update("DELETE FROM appreciations");
        jdbcTemplate.update("DELETE FROM challenge_entries WHERE id NOT IN ('che_seed_daniel', 'che_seed_elena', 'che_seed_sophie')");
        jdbcTemplate.update("UPDATE recognition_preferences SET show_on_leaderboard = TRUE");

        // Restore `story_08` (restricted UK published story) and `story_12` (Asha's private draft) to seed state
        jdbcTemplate.update("DELETE FROM content_revisions WHERE content_id = 'story_08' AND version_number > 1");
        jdbcTemplate.update("UPDATE content_items SET status = 'PUBLISHED', current_version = 1, published_version = 1, audience_id = 'aud_uk_restricted', published_audience_id = 'aud_uk_restricted' WHERE id = 'story_08'");
        jdbcTemplate.update("UPDATE content_items SET status = 'DRAFT', current_version = 1, published_version = NULL, published_audience_id = NULL, audience_id = 'aud_pilot' WHERE id = 'story_12'");
    }

    /**
     * Runs after every `@Test` method to unfreeze the clock and clear any runtime header overrides.
     */
    @AfterEach
    void tearDown() {
        pulseClock.reset();
        identityService.setRuntimeRequireExplicitHeader(null);
    }

    /**
     * Deliverable D2 Verification:
     * Tests editorial security boundaries, portal role gates, draft hiding, revision concurrency checks,
     * and the separation between draft `audience_id` and live `published_audience_id`.
     */
    @Test
    @DisplayName("D2: Shared visibility, scoped portal access, draft/withdrawn denial & audience snapshot isolation")
    void sharedVisibilityAndEditorialSecurityChecks() throws Exception {
        // 1. Ordinary employee (Asha, who has `EMPLOYEE,CREATOR` but NOT `REVIEWER`/`PUBLISHER`/`ADMIN`)
        // is denied access to the Publishing Portal review queue and audit log with `403 FORBIDDEN_ACCESS`.
        mockMvc.perform(get("/api/v1/portal/queue")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ACCESS"));

        mockMvc.perform(get("/api/v1/portal/audit-log")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ACCESS"));

        // 2. Reviewer Priya (member of `aud_all` and `aud_pilot`, NOT `aud_uk_restricted`) can read the portal queue,
        // and restricted `story_08` (`aud_uk_restricted`) is strictly filtered out of her portal view!
        MvcResult queueRes = mockMvc.perform(get("/api/v1/portal/queue")
                        .header("X-Pulse-User-Id", "usr_priya"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(queueRes.getResponse().getContentAsString()).doesNotContain("story_08");

        // 3. Another creator (Daniel) cannot submit Asha's draft `story_12` for review (`403 FORBIDDEN_ACCESS`)
        mockMvc.perform(post("/api/v1/content/story_12/submit")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ACCESS"));

        // 4. Direct employee read (`GET /api/v1/stories/{id}`) on an unpublished `DRAFT` (`story_12`)
        // or `IN_REVIEW` item (`story_11`) is denied with `404 Not Found` so unapproved text never leaks.
        mockMvc.perform(get("/api/v1/stories/story_12")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/stories/story_11")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isNotFound());

        // Owner preview (`GET /api/v1/content/story_12/preview`) succeeds (`200 OK`) for author Asha,
        // but is denied (`403 Forbidden`) when another colleague (Daniel) attempts to preview Asha's draft.
        mockMvc.perform(get("/api/v1/content/story_12/preview")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A better retrospective"));

        mockMvc.perform(get("/api/v1/content/story_12/preview")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isForbidden());

        // 5. Reviewing a story without specifying `revisionNumber` is rejected (`422 Unprocessable Entity`);
        // supplying a mismatched `revisionNumber: 99` is rejected (`409 STALE_VERSION`) so a Reviewer
        // never approves a revision they haven't actually inspected.
        mockMvc.perform(post("/api/v1/content/story_11/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/content/story_11/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("revisionNumber", 99, "decision", "APPROVE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        // 6. Audience snapshot isolation (CF-010):
        // Daniel edits `story_08` (currently PUBLISHED in `aud_uk_restricted`) and proposes changing
        // `audienceId` to `aud_pilot` in Revision 2.
        // The response confirms `currentVersion = 2`, while `publishedVersion = 1` and
        // `publishedAudienceId` remains locked to `aud_uk_restricted`!
        mockMvc.perform(put("/api/v1/content/story_08/revisions")
                        .header("X-Pulse-User-Id", "usr_daniel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "UK Risk Template Draft v2",
                                "bodyText", "Draft revision 2 proposing broader pilot audience.",
                                "audienceId", "aud_pilot"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.publishedVersion").value(1))
                .andExpect(jsonPath("$.publishedAudienceId").value("aud_uk_restricted"));

        // Asha STILL cannot read `/api/v1/stories/story_08` (`403 Forbidden`) because the live
        // published revision is still governed by `publishedAudienceId = 'aud_uk_restricted'`!
        mockMvc.perform(get("/api/v1/stories/story_08")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isForbidden());

        // Wrong-audience reviewer check: when `story_08` is scoped to `aud_uk_restricted`,
        // Reviewer Priya (who is not a member of `aud_uk_restricted`) gets `403 Forbidden`.
        jdbcTemplate.update("UPDATE content_items SET audience_id = 'aud_uk_restricted' WHERE id = 'story_08'");
        mockMvc.perform(post("/api/v1/content/story_08/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("revisionNumber", 2, "decision", "APPROVE"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Deliverable D4 Verification:
     * Walks through the complete lifecycle of a Campfire Challenge submission:
     * 0 initial points -> Submit with Idempotency-Key -> Request Changes -> Resubmit ->
     * Approve (still 0 points) -> Publish (+20 points & "First Spark" badge) ->
     * Idempotent re-publish (no duplicate points) -> Withdraw (compensating -20 reversal back to 0).
     */
    @Test
    @DisplayName("D4: Persisted Challenge Submit -> Review -> Publish -> Exactly-Once +20 Award -> Withdrawal Reversal")
    void challengeSubmissionPublicationRewardAndWithdrawalFlow() throws Exception {
        // 1. Before submitting to the weekly challenge, Asha starts with 0 total points and no badges
        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.contributionPoints").value(0))
                .andExpect(jsonPath("$.badges").isEmpty());

        // 2. Asha queries `GET /api/v1/challenges/current` and discovers `ch_beyond_01` ("Beyond My Job Title")
        mockMvc.perform(get("/api/v1/challenges/current")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ch_beyond_01"))
                .andExpect(jsonPath("$.title").value("Beyond My Job Title"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 3. Asha submits her badminton story to `ch_beyond_01` with `submitForReview: true`.
        // Because points are awarded ONLY upon publication, `pointsAwarded` is 0 while in `IN_REVIEW`.
        Map<String, Object> submitPayload = Map.of(
                "title", "What weekend badminton taught me about sprint pairing",
                "bodyText", "Playing mixed doubles every Saturday taught me to communicate early and cover gaps without waiting to be asked.",
                "submitForReview", true
        );

        MvcResult entryRes = mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.pointsAwarded").value(0))
                .andReturn();

        // Extract the newly created `contentId` so subsequent review/publish calls can target it
        String contentId = objectMapper.readValue(entryRes.getResponse().getContentAsString(), Map.class)
                .get("contentId").toString();

        // Idempotent replay with the exact same `Idempotency-Key` and payload hash returns the same `contentId`
        mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentId").value(contentId));

        // Reusing the same `Idempotency-Key` with a modified body is rejected with `409 IDEMPOTENCY_CONFLICT`
        mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Different title with same idempotency key",
                                "bodyText", "Playing mixed doubles every Saturday taught me to communicate early and cover gaps."
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        // Attempting a second submission with a new key while Revision 1 is already `IN_REVIEW`
        // is rejected with `409 DUPLICATE_ENTRY`
        mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ENTRY"));

        // 4. Reviewer Priya selects `REQUEST_CHANGES` on Revision 1 -> status returns to `DRAFT` (0 points).
        // Asha can then resubmit her updated story under a new idempotency key, creating Revision 2 in `IN_REVIEW`.
        mockMvc.perform(post("/api/v1/content/" + contentId + "/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "revisionNumber", 1,
                                "decision", "REQUEST_CHANGES",
                                "notes", "Please add how your team applied this lesson."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-03")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "What weekend badminton taught me about sprint pairing",
                                "bodyText", "Playing mixed doubles every Saturday taught me to communicate early. Our Pune pod now pairs on tricky PRs!",
                                "submitForReview", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisionNumber").value(2))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        // 5. Reviewer Priya approves Revision 2 -> status transitions to `APPROVED`,
        // but Asha still has 0 points because the story is not yet published!
        mockMvc.perform(post("/api/v1/content/" + contentId + "/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "revisionNumber", 2,
                                "decision", "APPROVE",
                                "notes", "Great story!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(0));

        // 6. Publisher Marcus releases Revision 2 (`mode: IMMEDIATE`) ->
        // Inside the same database transaction, Asha earns `+20` contribution points and the `"First Spark"` badge!
        mockMvc.perform(post("/api/v1/content/" + contentId + "/publish")
                        .header("X-Pulse-User-Id", "usr_marcus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "IMMEDIATE",
                                "revisionNumber", 2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedVersion").value(2));

        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(20))
                .andExpect(jsonPath("$.contributionPoints").value(20))
                .andExpect(jsonPath("$.badges[0]").value("First Spark"));

        // Replaying the publish call is idempotent (`source_event_id` uniqueness) and awards 0 extra points!
        mockMvc.perform(post("/api/v1/content/" + contentId + "/publish")
                        .header("X-Pulse-User-Id", "usr_marcus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "IMMEDIATE",
                                "revisionNumber", 2
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(20));

        // 7. Publisher Marcus withdraws Asha's story ->
        // Records a compensating `-20` reversal row in `recognition_events`, removes `"First Spark"`,
        // returns Asha to 0 points, and hides the withdrawn story (`404 Not Found`) from employee reads!
        mockMvc.perform(post("/api/v1/content/" + contentId + "/withdraw")
                        .header("X-Pulse-User-Id", "usr_marcus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Testing withdrawal reversal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.badges").isEmpty());

        mockMvc.perform(get("/api/v1/stories/" + contentId)
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isNotFound());
    }

    /**
     * Deliverable D5 Verification:
     * Verifies appreciation validation rules, multi-threaded pessimistic lock enforcement on the
     * 15-point giving cap, the 25-point receiving cap, the 60-point weekly total cap, all 3 weekly
     * badges, leaderboard opt-out privacy, and Monday 00:00 UTC week rollover.
     */
    @Test
    @DisplayName("D5: Appreciation Caps, Concurrent Race Locking, 3 Badges, Opt-Out & Monday UTC Rollover")
    void appreciationCapsBadgesLeaderboardAndConcurrency() throws Exception {
        // 1. Submit, review, and publish Asha's challenge story so she starts with +20 points ("First Spark")
        MvcResult entryRes = mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-d5-asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Badminton and cross-team rhythm",
                                "bodyText", "Playing mixed doubles every weekend helped me listen better during cross-team incident calls.",
                                "submitForReview", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        String ashaStoryId = objectMapper.readValue(entryRes.getResponse().getContentAsString(), Map.class)
                .get("contentId").toString();

        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("revisionNumber", 1, "decision", "APPROVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/publish")
                        .header("X-Pulse-User-Id", "usr_marcus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "IMMEDIATE", "revisionNumber", 1))))
                .andExpect(status().isOk());

        // 2. Guardrail checks:
        //    - Appreciating one's own story is rejected with `422 Unprocessable Entity`
        //    - Appreciating an unpublished draft (`story_12`) is rejected with `404 Not Found`
        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/content/story_12/appreciations")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isNotFound());

        // 3. Multi-threaded concurrent appreciation race:
        // Asha appreciates 4 published stories (`story_01`..`story_04`) from 4 different teams simultaneously
        // across a 4-thread pool. Because `RecognitionService.appreciateStory` acquires a pessimistic row lock
        // (`SELECT ... FOR UPDATE`) on the giver's user row before checking the weekly giving cap,
        // only 3 of the 4 concurrent transactions award `+5` (`totalGiverDeltas == 15`), and appreciating
        // stories across >= 3 distinct teams unlocks the `"Connection Maker"` badge!
        UserEntity ashaEntity = userRepository.findById("usr_asha").orElseThrow();
        List<String> targetStories = List.of("story_01", "story_02", "story_03", "story_04");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Map<String, Object>>> tasks = new ArrayList<>();
            for (String sid : targetStories) {
                tasks.add(() -> recognitionService.appreciateStory(ashaEntity, sid));
            }
            List<Future<Map<String, Object>>> futures = pool.invokeAll(tasks);
            int totalGiverDeltas = 0;
            for (Future<Map<String, Object>> f : futures) {
                Map<String, Object> res = f.get();
                totalGiverDeltas += ((Number) res.get("giverPointsDelta")).intValue();
            }
            assertThat(totalGiverDeltas).as("Concurrent giving cap must never exceed 15 points").isEqualTo(15);
        } finally {
            pool.shutdownNow();
        }

        // 4. Five distinct colleagues appreciate Asha's story (`usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`, `usr_rohan`)
        // -> Asha receives `5 * 5 = 25` points (unlocking `"Community Favourite"`) and reaches the 60-point weekly maximum!
        for (String colleagueId : List.of("usr_daniel", "usr_priya", "usr_marcus", "usr_elena", "usr_rohan")) {
            mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                            .header("X-Pulse-User-Id", colleagueId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receiverPointsDelta").value(5));
        }

        // Duplicate appreciation by Daniel on the same story is idempotent: returns `alreadyAppreciated = true` and `0` delta
        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyAppreciated").value(true))
                .andExpect(jsonPath("$.giverPointsDelta").value(0))
                .andExpect(jsonPath("$.receiverPointsDelta").value(0));

        // 6th distinct colleague (`usr_sophie`) appreciating Asha's story is recorded with `0` receiverPointsDelta
        // because Asha has already reached the 25-point weekly receiving cap!
        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                        .header("X-Pulse-User-Id", "usr_sophie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiverPointsDelta").value(0));

        // Verify Asha has 60 total points (`20 contribution + 15 giving + 25 receiving`) and all 3 weekly badges
        // (`"First Spark"`, `"Connection Maker"`, and `"Community Favourite"`).
        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(60))
                .andExpect(jsonPath("$.contributionPoints").value(20))
                .andExpect(jsonPath("$.givingPoints").value(15))
                .andExpect(jsonPath("$.receivingPoints").value(25))
                .andExpect(jsonPath("$.badges.length()").value(3));

        // 5. Leaderboard shows Asha at Rank 1 (`60 points`); when Asha opts out (`showOnLeaderboard: false`),
        // she is immediately hidden from the public leaderboard while keeping her private 60 points!
        mockMvc.perform(get("/api/v1/recognition/leaderboard?audienceId=aud_pilot")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].userId").value("usr_asha"))
                .andExpect(jsonPath("$.entries[0].points").value(60));

        mockMvc.perform(put("/api/v1/recognition/preferences")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("showOnLeaderboard", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showOnLeaderboard").value(false))
                .andExpect(jsonPath("$.points").value(60));

        MvcResult lbAfterOptOut = mockMvc.perform(get("/api/v1/recognition/leaderboard?audienceId=aud_pilot")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(lbAfterOptOut.getResponse().getContentAsString()).doesNotContain("usr_asha");

        // 6. Advancing the clock to next Monday 00:00 UTC (`2026-11-23T00:00:00Z`) starts a fresh recognition week
        // (`weekStart = "2026-11-23"`) with 0 points, without needing a cron job to delete historical ledger rows!
        pulseClock.setClock(Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC));
        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-23"))
                .andExpect(jsonPath("$.points").value(0));
    }
}
