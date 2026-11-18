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
 *
 * Why it is designed this way:
 *   Every editorial decision (create draft, edit revision, submit for review,
 *   approve, request changes, publish now, schedule publication, withdraw, or
 *   resolve moderation flag) records an append-only row in `audit_events`
 *   so reviewers, publishers, and scoped admins have clear accountability.
 *
 * JPA Annotations Explained for Interns:
 *   - `@Entity`: Declares this class as a JPA-managed persistent entity.
 *   - `@Table(name = "audit_events")`: Maps this class to the `audit_events` table
 *     defined in `V1__init_schema.sql`.
 *   - `@Id`: Marks `id` as the primary key column.
 *   - `@Column(...)`: Maps each Java field to its corresponding SQL column name
 *     with `NOT NULL` and length constraints.
 * ============================================================================
 */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    /**
     * Unique primary key for the audit event (e.g., `"aud_7f3a91c2"`).
     * Mapped to `id VARCHAR(64) NOT NULL PRIMARY KEY`.
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * ID of the user (`users.id`) or `"SYSTEM_OUTBOX"` who performed the audited action.
     * Mapped to `actor_id VARCHAR(64) NOT NULL`.
     */
    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    /**
     * Machine-readable action verb describing what happened (e.g., `"SUBMIT_FOR_REVIEW"`,
     * `"APPROVE_CONTENT"`, `"PUBLISH_CONTENT"`, `"WITHDRAW_CONTENT"`, `"EDIT_INVALIDATED_APPROVAL"`).
     * Mapped to `action_type VARCHAR(64) NOT NULL`.
     */
    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    /**
     * Domain entity type affected by the action (e.g., `"STORY"`, `"OFFICIAL_UPDATE"`,
     * `"MODERATION_FLAG"`, `"CONNECTION_REQUEST"`).
     * Mapped to `target_type VARCHAR(64) NOT NULL`.
     */
    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    /**
     * Primary key ID of the entity affected by the action (e.g., `"cnt_story_1"`).
     * Mapped to `target_id VARCHAR(64) NOT NULL`.
     */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    /**
     * Human-readable summary of the event, including version numbers, target audiences,
     * or reviewer notes for display in the Publishing Portal audit log table.
     * Mapped to `details VARCHAR(1000) NOT NULL`.
     */
    @Column(name = "details", nullable = false, length = 1000)
    private String details;

    /**
     * UTC timestamp when the audited action occurred.
     * Mapped to `created_at TIMESTAMP WITH TIME ZONE NOT NULL`.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Static factory helper that constructs a new `AuditEventEntity` with a unique
     * `"aud_"` ID and the current UTC timestamp.
     *
     * @param actorId    ID of the user (or `"SYSTEM_OUTBOX"`) performing the action.
     * @param actionType Action verb (e.g., `"APPROVE_CONTENT"`).
     * @param targetType Target resource type (e.g., `"STORY"`).
     * @param targetId   Target resource ID (e.g., `"cnt_story_1"`).
     * @param details    Human-readable explanation of the transition.
     * @return New `AuditEventEntity` ready to be persisted via `auditEventRepository.save(...)`.
     */
    public static AuditEventEntity create(String actorId, String actionType, String targetType, String targetId, String details) {
        // Instantiate a new audit event entity.
        AuditEventEntity ev = new AuditEventEntity();
        // Generate a unique ID with prefix "aud_" and 8 hex characters from a random UUID.
        ev.setId("aud_" + UUID.randomUUID().toString().substring(0, 8));
        // Record who performed the action.
        ev.setActorId(actorId);
        // Record what type of action was performed.
        ev.setActionType(actionType);
        // Record the domain entity type that was modified.
        ev.setTargetType(targetType);
        // Record the primary key of the modified domain entity.
        ev.setTargetId(targetId);
        // Record human-readable audit context (version, audience, reviewer feedback).
        ev.setDetails(details);
        // Stamp the current UTC instant.
        ev.setCreatedAt(Instant.now());
        return ev;
    }

    /** @return Unique audit event ID. */
    public String getId() { return id; }
    /** @param id Unique audit event ID to set. */
    public void setId(String id) { this.id = id; }

    /** @return User ID (or `"SYSTEM_OUTBOX"`) of the actor. */
    public String getActorId() { return actorId; }
    /** @param actorId Actor ID to set. */
    public void setActorId(String actorId) { this.actorId = actorId; }

    /** @return Action type verb (e.g., `"PUBLISH_CONTENT"`). */
    public String getActionType() { return actionType; }
    /** @param actionType Action type verb to set. */
    public void setActionType(String actionType) { this.actionType = actionType; }

    /** @return Target domain entity type. */
    public String getTargetType() { return targetType; }
    /** @param targetType Target domain entity type to set. */
    public void setTargetType(String targetType) { this.targetType = targetType; }

    /** @return Target domain entity ID. */
    public String getTargetId() { return targetId; }
    /** @param targetId Target domain entity ID to set. */
    public void setTargetId(String targetId) { this.targetId = targetId; }

    /** @return Human-readable audit details string. */
    public String getDetails() { return details; }
    /** @param details Human-readable audit details string to set. */
    public void setDetails(String details) { this.details = details; }

    /** @return UTC timestamp of the audit event. */
    public Instant getCreatedAt() { return createdAt; }
    /** @param createdAt UTC timestamp to set. */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
