package com.mastek.pulse.productivity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: HabitCheckinEntity.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-03)
 *
 * WHAT: One tick (or explicit untick) of a habit on one calendar day.
 * WHY:
 *   - `localDate` is a `LocalDate` (SQL DATE), not an `Instant`. "Tuesday" for
 *     an employee in Pune must stay Tuesday regardless of UTC offset or DST.
 *   - `UNIQUE(habit_id, local_date)` in V4 makes `PUT .../checkins/{date}` an
 *     idempotent upsert: repeating the call never creates a duplicate row.
 * ============================================================================
 */
@Entity
@Table(name = "habit_checkins")
public class HabitCheckinEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "habit_id", nullable = false, length = 64)
    private String habitId;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHabitId() { return habitId; }
    public void setHabitId(String habitId) { this.habitId = habitId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public LocalDate getLocalDate() { return localDate; }
    public void setLocalDate(LocalDate localDate) { this.localDate = localDate; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
