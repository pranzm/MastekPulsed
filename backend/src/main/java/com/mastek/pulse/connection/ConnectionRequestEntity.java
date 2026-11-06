package com.mastek.pulse.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ConnectionRequestEntity.java
 * Journey: Primary Journey ("Review and send an invitation -> Host accepts").
 * Why it is designed this way:
 *   Maps to `connection_requests` in `V1__init_schema.sql`.
 *   1. `idempotencyKey`: Prevents duplicate requests when a mobile user taps
 *      "Send invitation" on a spotty train commute connection (Page 7 & 15).
 *   2. `state`: Follows the strict state machine `PENDING -> ACCEPTED | DECLINED | CANCELLED | EXPIRED`.
 *   3. `version`: Optimistic version number checked on `PATCH /api/v1/requests/{id}`
 *      so stale UI clicks return `409 Conflict`.
 * ============================================================================
 */
@Entity
@Table(name = "connection_requests")
public class ConnectionRequestEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "invitation_id", nullable = false, length = 64)
    private String invitationId;

    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @Column(name = "message_text", nullable = false, length = 1000)
    private String messageText;

    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted = false;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "state", nullable = false, length = 32)
    private String state = "PENDING"; // PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInvitationId() { return invitationId; }
    public void setInvitationId(String invitationId) { this.invitationId = invitationId; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public boolean isAiAssisted() { return aiAssisted; }
    public void setAiAssisted(boolean aiAssisted) { this.aiAssisted = aiAssisted; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
