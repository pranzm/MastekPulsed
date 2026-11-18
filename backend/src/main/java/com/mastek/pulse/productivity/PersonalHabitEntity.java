package com.mastek.pulse.productivity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PersonalHabitEntity.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-03)
 *
 * WHAT: Maps one row of `personal_habits` (V4 migration) — a habit an employee
 *       defines for themselves ("Drink water", "Walk after lunch").
 * WHY:
 *   - `ownerId` is a plain scalar FK (no `@ManyToOne` to `UserEntity`) so the
 *     module can be extracted later without untangling JPA graphs (ADR-007).
 *   - `@Version` gives optimistic locking: two devices editing the same habit
 *     cannot silently overwrite each other — the second gets `409 STALE_VERSION`.
 * ============================================================================
 */
@Entity
@Table(name = "personal_habits")
public class PersonalHabitEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    // Only this employee may ever read or change the habit.
    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    // 'daily' | 'weekdays' | 'weekends'
    @Column(name = "schedule", nullable = false, length = 16)
    private String schedule;

    // IANA zone (e.g. "Asia/Kolkata") used to compute the employee's "today".
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Incremented by Hibernate on every UPDATE.
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
