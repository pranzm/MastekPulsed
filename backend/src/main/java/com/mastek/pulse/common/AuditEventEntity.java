package com.mastek.pulse.common;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AuditEventEntity.java
 * Journey: Publishing Portal Audit Log & Operational Controls (Proposal Pages 11 & 14).
 * Why it is designed this way:
 *   Every editorial decision (submit, approve, request changes, publish, schedule,
 *   withdraw) and role/audience action records an immutable row in `audit_events`
 *   so reviewers, publishers, and scoped admins have clear accountability.
 * ============================================================================
 */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "details", nullable = false, length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static AuditEventEntity create(String actorId, String actionType, String targetType, String targetId, String details) {
        AuditEventEntity ev = new AuditEventEntity();
        ev.setId("aud_" + UUID.randomUUID().toString().substring(0, 8));
        ev.setActorId(actorId);
        ev.setActionType(actionType);
        ev.setTargetType(targetType);
        ev.setTargetId(targetId);
        ev.setDetails(details);
        ev.setCreatedAt(Instant.now());
        return ev;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
