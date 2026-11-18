package com.mastek.pulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastek.pulse.common.PulseClock;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: MakeMeProductiveTest.java
 * Journey: Section 14 / Make me Productive Integration Verification (`MP-03`, `MP-05`).
 *
 * Verifies:
 *   - Owner-only isolation across employees and privileged roles (`ADMIN`/`REVIEWER` get 404).
 *   - Idempotent habit creation (`Idempotency-Key`), optimistic locking (`409 STALE_VERSION`).
 *   - Local-date check-in validation (future/off-schedule -> 422; upsert -> 200).
 *   - Duration range validation (`422`), session deduplication (`clientSessionId`), and purge.
 * ============================================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
class MakeMeProductiveTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PulseClock pulseClock;

    @BeforeEach
    void setup() {
        // Tuesday 2026-11-17T09:00:00Z
        pulseClock.setClock(Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneOffset.UTC));
        jdbcTemplate.update("DELETE FROM habit_checkins");
        jdbcTemplate.update("DELETE FROM personal_habits");
        jdbcTemplate.update("DELETE FROM focus_sessions");
        jdbcTemplate.update("DELETE FROM productivity_preferences");
    }

    @AfterEach
    void tearDown() {
        pulseClock.reset();
    }

    @Test
    @DisplayName("Owner-only isolation, optimistic lock, check-ins, timer preferences, sessions & purge")
    void productivityFullLifecycleAndPrivacyIsolation() throws Exception {
        // 1. Unauthenticated token -> 401
        mockMvc.perform(get("/api/v1/me/productivity/habits")
                        .header("X-Pulse-User-Id", "INVALID"))
                .andExpect(status().isUnauthorized());

        // 2. Asha creates a weekdays habit with Idempotency-Key (replay returns same ID)
        MvcResult createRes = mockMvc.perform(post("/api/v1/me/productivity/habits")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-hab-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "10-min morning stretch",
                                "schedule", "weekdays",
                                "timezone", "UTC"
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.name").value("10-min morning stretch"))
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class);
        String habitId = (String) created.get("id");
        long version = ((Number) created.get("version")).longValue();

        // Replay with same Idempotency-Key returns same habit ID
        mockMvc.perform(post("/api/v1/me/productivity/habits")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .header("Idempotency-Key", "idem-hab-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "10-min morning stretch",
                                "schedule", "weekdays",
                                "timezone", "UTC"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(habitId));

        // 3. Cross-user & privileged role isolation: Daniel, Priya (REVIEWER), and Elena (ADMIN) get 404
        for (String otherUser : new String[]{"usr_daniel", "usr_priya", "usr_elena"}) {
            mockMvc.perform(get("/api/v1/me/productivity/habits")
                            .header("X-Pulse-User-Id", otherUser))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            mockMvc.perform(patch("/api/v1/me/productivity/habits/" + habitId)
                            .header("X-Pulse-User-Id", otherUser)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "name", "Hacked",
                                    "expectedVersion", version
                            ))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(put("/api/v1/me/productivity/habits/" + habitId + "/checkins/2026-11-17")
                            .header("X-Pulse-User-Id", otherUser)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("completed", true))))
                    .andExpect(status().isNotFound());

            mockMvc.perform(delete("/api/v1/me/productivity/habits/" + habitId)
                            .header("X-Pulse-User-Id", otherUser))
                    .andExpect(status().isNotFound());
        }

        // 4. Asha updates habit with stale version -> 409; valid version -> 200
        mockMvc.perform(patch("/api/v1/me/productivity/habits/" + habitId)
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated stretch",
                                "expectedVersion", version + 99
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        mockMvc.perform(patch("/api/v1/me/productivity/habits/" + habitId)
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated stretch",
                                "expectedVersion", version
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated stretch"));

        // 5. Check-in validation: future day -> 422, weekend day on weekdays habit -> 422, Tuesday -> 200
        mockMvc.perform(put("/api/v1/me/productivity/habits/" + habitId + "/checkins/2026-11-18")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completed", true))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/me/productivity/habits/" + habitId + "/checkins/2026-11-15") // Sunday
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completed", true))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/me/productivity/habits/" + habitId + "/checkins/2026-11-17") // Tuesday
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("completed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        // 6. Timer preferences & session deduplication
        mockMvc.perform(put("/api/v1/me/productivity/preferences")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("focusMinutes", 999))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/me/productivity/preferences")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "focusMinutes", 30,
                                "shortBreakMinutes", 5,
                                "longBreakMinutes", 15,
                                "resetMinutes", 3,
                                "timezone", "Asia/Kolkata"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusMinutes").value(30));

        // Duplicate clientSessionId increments focus count once
        mockMvc.perform(post("/api/v1/me/productivity/sessions")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientSessionId", "ses-client-001",
                                "kind", "focus",
                                "plannedSeconds", 1800
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedFocusSessions").value(1));

        mockMvc.perform(post("/api/v1/me/productivity/sessions")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientSessionId", "ses-client-001",
                                "kind", "focus",
                                "plannedSeconds", 1800
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedFocusSessions").value(1));

        // 7. Purge all data for Asha
        mockMvc.perform(delete("/api/v1/me/productivity/data")
                        .header("X-Pulse-User-Id", "usr_asha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confirm", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purged").value(true))
                .andExpect(jsonPath("$.habitsDeleted").value(1));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM personal_habits WHERE owner_id = 'usr_asha'", Integer.class)).isZero();
    }
}
