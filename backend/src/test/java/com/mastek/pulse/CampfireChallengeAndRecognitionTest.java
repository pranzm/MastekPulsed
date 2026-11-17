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
 * Why it is designed this way:
 *   Verifies all security, editorial visibility, challenge submission,
 *   transactional +20 publication reward, withdrawal reversal, appreciation
 *   caps (15 giving / 25 receiving / 60 max), 3 badges, leaderboard opt-out,
 *   Monday 00:00 UTC rollover, and multi-threaded PostgreSQL concurrency locks!
 * ============================================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampfireChallengeAndRecognitionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PulseClock pulseClock;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private RecognitionService recognitionService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetCampfireState() {
        pulseClock.setClock(Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneOffset.UTC));
        identityService.setRuntimeRequireExplicitHeader(null);

        // Clean test-generated rows while preserving V2/V3 seed baseline
        jdbcTemplate.update("DELETE FROM weekly_badges WHERE id NOT IN ('bdg_seed_daniel', 'bdg_seed_elena', 'bdg_seed_sophie')");
        jdbcTemplate.update("DELETE FROM recognition_events WHERE reversal_of IS NOT NULL");
        jdbcTemplate.update("DELETE FROM recognition_events WHERE id NOT IN ('rec_seed_daniel', 'rec_seed_elena', 'rec_seed_sophie')");
        jdbcTemplate.update("DELETE FROM appreciations");
        jdbcTemplate.update("DELETE FROM challenge_entries WHERE id NOT IN ('che_seed_daniel', 'che_seed_elena', 'che_seed_sophie')");
        jdbcTemplate.update("UPDATE recognition_preferences SET show_on_leaderboard = TRUE");

        // Restore story_08 and story_12 to seed state
        jdbcTemplate.update("DELETE FROM content_revisions WHERE content_id = 'story_08' AND version_number > 1");
        jdbcTemplate.update("UPDATE content_items SET status = 'PUBLISHED', current_version = 1, published_version = 1, audience_id = 'aud_uk_restricted', published_audience_id = 'aud_uk_restricted' WHERE id = 'story_08'");
        jdbcTemplate.update("UPDATE content_items SET status = 'DRAFT', current_version = 1, published_version = NULL, published_audience_id = NULL, audience_id = 'aud_pilot' WHERE id = 'story_12'");
    }

    @AfterEach
    void tearDown() {
        pulseClock.reset();
        identityService.setRuntimeRequireExplicitHeader(null);
    }

    @Test
    @DisplayName("D2: Shared visibility, scoped portal access, draft/withdrawn denial & audience snapshot isolation")
    void sharedVisibilityAndEditorialSecurityChecks() throws Exception {
        // 1. Ordinary employee (Asha) denied access to portal queue and audit log (403)
        mockMvc.perform(get("/api/v1/portal/queue")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ACCESS"));

        mockMvc.perform(get("/api/v1/portal/audit-log")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ACCESS"));

        // 2. Reviewer Priya (`aud_all`, `aud_pilot`) can read portal queue, and restricted `story_08` (`aud_uk_restricted`) is filtered out!
        MvcResult queueRes = mockMvc.perform(get("/api/v1/portal/queue")
                        .header("X-Pulse-User-Id", "usr_priya"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(queueRes.getResponse().getContentAsString()).doesNotContain("story_08");

        // 3. Another creator (Daniel) cannot submit Asha's draft `story_12` (403)
        mockMvc.perform(post("/api/v1/content/story_12/submit")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ACCESS"));

        // 4. Direct employee read on unpublished draft (`story_12`) or in-review item (`story_11`) is denied (404)
        mockMvc.perform(get("/api/v1/stories/story_12")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/stories/story_11")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isNotFound());

        // Owner preview (`GET /api/v1/content/story_12/preview`) succeeds for author Asha, denied (403) for Daniel
        mockMvc.perform(get("/api/v1/content/story_12/preview")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A better retrospective"));

        mockMvc.perform(get("/api/v1/content/story_12/preview")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isForbidden());

        // 5. Missing revisionNumber in review is rejected (422); stale revisionNumber is rejected (409)
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

        // 6. Audience edit on published restricted story (`story_08` in `aud_uk_restricted`) to `aud_pilot`
        // must NOT disclose the previously published text to Asha before the new revision is reviewed & published!
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

        // Asha STILL cannot read `/api/v1/stories/story_08` (403 Forbidden because publishedAudienceId is still aud_uk_restricted!)
        mockMvc.perform(get("/api/v1/stories/story_08")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isForbidden());

        // Wrong-audience reviewer check: if an item is in `aud_uk_restricted`, Priya (not in `aud_uk_restricted`) gets 403
        jdbcTemplate.update("UPDATE content_items SET audience_id = 'aud_uk_restricted' WHERE id = 'story_08'");
        mockMvc.perform(post("/api/v1/content/story_08/review")
                        .header("X-Pulse-User-Id", "usr_priya")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("revisionNumber", 2, "decision", "APPROVE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D4: Persisted Challenge Submit -> Review -> Publish -> Exactly-Once +20 Award -> Withdrawal Reversal")
    void challengeSubmissionPublicationRewardAndWithdrawalFlow() throws Exception {
        // 1. Before submission, Asha has 0 points
        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(0))
                .andExpect(jsonPath("$.contributionPoints").value(0))
                .andExpect(jsonPath("$.badges").isEmpty());

        // 2. Asha discovers active challenge
        mockMvc.perform(get("/api/v1/challenges/current")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ch_beyond_01"))
                .andExpect(jsonPath("$.title").value("Beyond My Job Title"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 3. Asha submits her badminton story to `ch_beyond_01`
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

        String contentId = objectMapper.readValue(entryRes.getResponse().getContentAsString(), Map.class)
                .get("contentId").toString();

        // Idempotent replay with same key & body returns same contentId
        mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentId").value(contentId));

        // Same Idempotency-Key with modified body returns 409 IDEMPOTENCY_CONFLICT
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

        // Duplicate submission with a new key while IN_REVIEW returns 409 DUPLICATE_ENTRY
        mockMvc.perform(post("/api/v1/challenges/ch_beyond_01/entries")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-ch-asha-02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ENTRY"));

        // 4. Reviewer Priya requests changes -> status becomes DRAFT (0 points); Asha resubmits -> Revision 2 IN_REVIEW
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

        // 5. Priya approves Revision 2 -> Approval alone earns 0 points!
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

        // 6. Publisher Marcus releases Revision 2 -> Asha earns exactly +20 points and "First Spark"!
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

        // Replaying publish call adds 0 extra points!
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

        // 7. Marcus withdraws Asha's story -> Reverses the +20 reward back to 0 and removes story from employee reads!
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

    @Test
    @DisplayName("D5: Appreciation Caps, Concurrent Race Locking, 3 Badges, Opt-Out & Monday UTC Rollover")
    void appreciationCapsBadgesLeaderboardAndConcurrency() throws Exception {
        // 1. Publish Asha's story so she has +20 (First Spark)
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

        // 2. Self-appreciation is rejected (422); unpublished story appreciation is rejected (404)
        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/content/story_12/appreciations")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isNotFound());

        // 3. Concurrent appreciation race: Asha appreciates 4 published stories from different colleagues concurrently.
        // Only the first 3 earn +5 (givingPoints capped at 15!), and appreciating >=3 teams unlocks "Connection Maker"!
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
        // -> Asha receives 5 * 5 = 25 points (unlocking "Community Favourite") and reaches the 60-point weekly maximum!
        for (String colleagueId : List.of("usr_daniel", "usr_priya", "usr_marcus", "usr_elena", "usr_rohan")) {
            mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                            .header("X-Pulse-User-Id", colleagueId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receiverPointsDelta").value(5));
        }

        // Duplicate appreciation by Daniel returns alreadyAppreciated=true and 0 delta
        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                        .header("X-Pulse-User-Id", "usr_daniel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyAppreciated").value(true))
                .andExpect(jsonPath("$.giverPointsDelta").value(0))
                .andExpect(jsonPath("$.receiverPointsDelta").value(0));

        // 6th distinct colleague (`usr_sophie`) appreciating Asha's story is recorded with 0 receiverPointsDelta (25 cap!)
        mockMvc.perform(post("/api/v1/content/" + ashaStoryId + "/appreciations")
                        .header("X-Pulse-User-Id", "usr_sophie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiverPointsDelta").value(0));

        // Verify Asha has 60 points (20 + 15 + 25) and all 3 badges ("First Spark", "Connection Maker", "Community Favourite")
        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(60))
                .andExpect(jsonPath("$.contributionPoints").value(20))
                .andExpect(jsonPath("$.givingPoints").value(15))
                .andExpect(jsonPath("$.receivingPoints").value(25))
                .andExpect(jsonPath("$.badges.length()").value(3));

        // 5. Leaderboard shows Asha at Rank 1; opting out hides her from leaderboard while keeping private 60 points!
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

        // 6. Advancing clock to next Monday 00:00 UTC (`2026-11-23T00:00:00Z`) starts a fresh week (0 points)
        pulseClock.setClock(Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC));
        mockMvc.perform(get("/api/v1/recognition/me")
                        .header("X-Pulse-User-Id", "usr_asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-23"))
                .andExpect(jsonPath("$.points").value(0));
    }
}
