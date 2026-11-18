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
 *
 * Why it is designed this way:
 *   `DEVELOPMENT-PLAN-2026-11-17.md` (D3) states:
 *   "One active challenge for aud_pilot; Monday 00:00 UTC scoring boundary;
 *    return explicit start/end/timezone. Use an injected Clock for deterministic
 *    tests rather than fixed expired dates."
 *   Instead of calling `Instant.now()` directly across recognition and challenge
 *   services (which makes week-boundary unit testing brittle), this Spring
 *   `@Component` wraps a `volatile java.time.Clock` (defaulting to `Clock.systemUTC()`)
 *   and computes the canonical Monday 00:00 UTC `weekStart` (`YYYY-MM-DD`).
 *   Automated tests can call `setClock(...)` to jump across weeks deterministically!
 *
 * Key Annotations & Keywords Explained for Interns:
 *   - `@Component`: Registers `PulseClock` as a singleton Spring bean that can be
 *     injected into `RecognitionService`, `ContentService`, and `ConnectionService`.
 *   - `volatile`: Ensures that if a test thread updates `clock` via `setClock(...)`,
 *     all worker threads immediately see the updated `Clock` reference.
 * ============================================================================
 */
@Component
public class PulseClock {

    /**
     * Underlying `java.time.Clock` instance. Defaults to `Clock.systemUTC()` in production,
     * and can be swapped via `setClock(...)` in deterministic integration tests.
     */
    private volatile Clock clock = Clock.systemUTC();

    /**
     * Returns the current UTC `Instant` from the underlying clock.
     *
     * @return Current `Instant`.
     */
    public Instant now() {
        // Delegate to the active Clock instance.
        return clock.instant();
    }

    /**
     * Returns the active `java.time.Clock` instance.
     *
     * @return Current `Clock`.
     */
    public Clock getClock() {
        return clock;
    }

    /**
     * Overrides the active clock (used by tests to simulate specific dates or week rollovers).
     * Passing `null` safely resets the clock back to `Clock.systemUTC()`.
     *
     * @param customClock Fixed or offset `Clock` for testing, or `null` for system UTC.
     */
    public void setClock(Clock customClock) {
        // Use customClock if provided; otherwise fall back to real UTC system clock.
        this.clock = customClock != null ? customClock : Clock.systemUTC();
    }

    /**
     * Resets the clock back to the real UTC system clock (`Clock.systemUTC()`).
     */
    public void reset() {
        this.clock = Clock.systemUTC();
    }

    /**
     * Computes the Monday 00:00 UTC week key (`YYYY-MM-DD`) for any given `Instant`.
     * All weekly recognition caps (20 / 15 / 25 / 60 points) and Campfire weekly
     * challenges are bucketed by this Monday UTC date string.
     *
     * @param instant The timestamp to bucket into a Monday UTC week.
     * @return ISO-8601 date string (`YYYY-MM-DD`) of the Monday on or preceding `instant` in UTC.
     */
    public String weekStartFor(Instant instant) {
        // Convert the UTC Instant into a ZonedDateTime at UTC (+00:00).
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        // Walk backward (or stay on the same day if already Monday) to find Monday of that UTC week.
        LocalDate monday = utc.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // Format as "YYYY-MM-DD" (e.g., "2026-11-16").
        return monday.toString();
    }

    /**
     * Computes the Monday 00:00 UTC week key (`YYYY-MM-DD`) for the current clock instant (`now()`).
     *
     * @return ISO-8601 date string (`YYYY-MM-DD`) for the current UTC week's Monday.
     */
    public String currentWeekStart() {
        // Delegate to weekStartFor using the current clock instant.
        return weekStartFor(now());
    }
}
