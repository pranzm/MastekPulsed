package com.mastek.pulse.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: OutboxEventEntity.java
 * Journey: Transactional Outbox & Background Worker (Proposal Pages 14 & 15).
 * Why it is designed this way:
 *   What happens if a Host accepts an invitation (`PATCH /api/v1/requests/{id}`)
 *   and the database commits, but the notification service is temporarily down?
 *   If we called the notification API directly, the notification would be lost!
 *   Instead, Page 14 & 15 state: "Outbox is in the same database transaction as
 *   request changes. Set accepted, increment accepted_count, and write an outbox
 *   event; commit together."
 * ============================================================================
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_run", nullable = false)
    private Instant nextRun = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static OutboxEventEntity create(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        OutboxEventEntity ev = new OutboxEventEntity();
        ev.setEventId("obx_" + UUID.randomUUID().toString().substring(0, 8));
        ev.setAggregateType(aggregateType);
        ev.setAggregateId(aggregateId);
        ev.setEventType(eventType);
        ev.setPayloadJson(payloadJson);
        ev.setStatus("PENDING");
        ev.setAttempts(0);
        ev.setNextRun(Instant.now());
        ev.setCreatedAt(Instant.now());
        return ev;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getNextRun() { return nextRun; }
    public void setNextRun(Instant nextRun) { this.nextRun = nextRun; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
