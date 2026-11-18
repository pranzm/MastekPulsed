package com.mastek.pulse.productivity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: FocusSessionEntity.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-05)
 *
 * WHAT: A timer session the employee actually ran to zero (focus, break,
 *       box breathing or mindfulness).
 * WHY:
 *   - The CLIENT records a session only when its deadline reaches zero — we
 *     never infer completion from a closed screen.
 *   - `clientSessionId` + `UNIQUE(owner_id, client_session_id)` means a flaky
 *     network retry or a page reload re-sending the same completion is a no-op.
 *   - No habit text or notes are stored here (privacy minimisation).
 * ============================================================================
 */
@Entity
@Table(name = "focus_sessions")
public class FocusSessionEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "client_session_id", nullable = false, length = 64)
    private String clientSessionId;

    // 'focus' | 'short' | 'long' | 'breath' | 'mind'
    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "planned_seconds", nullable = false)
    private int plannedSeconds;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getClientSessionId() { return clientSessionId; }
    public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public int getPlannedSeconds() { return plannedSeconds; }
    public void setPlannedSeconds(int plannedSeconds) { this.plannedSeconds = plannedSeconds; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
