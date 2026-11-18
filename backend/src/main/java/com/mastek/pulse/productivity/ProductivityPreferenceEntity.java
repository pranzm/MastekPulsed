package com.mastek.pulse.productivity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ProductivityPreferenceEntity.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-03 / MP-05)
 *
 * WHAT: One row per employee holding their Pomodoro and reset timer durations.
 * WHY:
 *   - `ownerId` is the primary key, so there is at most one preference row per
 *     employee and a missing row simply means "use defaults 25/5/15/2".
 *   - Ranges (focus 1–120, breaks 1–60, reset 1–30) are enforced twice: in Java
 *     (friendly `422`) and as V4 CHECK constraints (defense in depth).
 * ============================================================================
 */
@Entity
@Table(name = "productivity_preferences")
public class ProductivityPreferenceEntity {

    @Id
    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "focus_minutes", nullable = false)
    private int focusMinutes = 25;

    @Column(name = "short_break_minutes", nullable = false)
    private int shortBreakMinutes = 5;

    @Column(name = "long_break_minutes", nullable = false)
    private int longBreakMinutes = 15;

    @Column(name = "reset_minutes", nullable = false)
    private int resetMinutes = 2;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public int getFocusMinutes() { return focusMinutes; }
    public void setFocusMinutes(int focusMinutes) { this.focusMinutes = focusMinutes; }
    public int getShortBreakMinutes() { return shortBreakMinutes; }
    public void setShortBreakMinutes(int shortBreakMinutes) { this.shortBreakMinutes = shortBreakMinutes; }
    public int getLongBreakMinutes() { return longBreakMinutes; }
    public void setLongBreakMinutes(int longBreakMinutes) { this.longBreakMinutes = longBreakMinutes; }
    public int getResetMinutes() { return resetMinutes; }
    public void setResetMinutes(int resetMinutes) { this.resetMinutes = resetMinutes; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
