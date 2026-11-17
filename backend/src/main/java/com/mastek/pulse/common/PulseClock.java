package com.mastek.pulse.common;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PulseClock.java
 * Journey: Campfire Weekly Challenges & Recognition (Proposal Pages 16, 17, 19).
 * Why it is designed this way:
 *   `DEVELOPMENT-PLAN-2026-11-17.md` (D3) states:
 *   "One active challenge for aud_pilot; Monday 00:00 UTC scoring boundary;
 *    return explicit start/end/timezone. Use an injected Clock for deterministic
 *    tests rather than fixed expired dates."
 *   This component wraps `java.time.Clock` (defaulting to UTC) and computes
 *   the canonical Monday 00:00 UTC `weekStart` (`YYYY-MM-DD`), while allowing
 *   tests to advance or override the clock deterministically.
 * ============================================================================
 */
@Component
public class PulseClock {

    private volatile Clock clock = Clock.systemUTC();

    public Instant now() {
        return clock.instant();
    }

    public Clock getClock() {
        return clock;
    }

    public void setClock(Clock customClock) {
        this.clock = customClock != null ? customClock : Clock.systemUTC();
    }

    public void reset() {
        this.clock = Clock.systemUTC();
    }

    /**
     * Computes the Monday 00:00 UTC week key (`YYYY-MM-DD`) for a given Instant.
     */
    public String weekStartFor(Instant instant) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        LocalDate monday = utc.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.toString();
    }

    public String currentWeekStart() {
        return weekStartFor(now());
    }
}
