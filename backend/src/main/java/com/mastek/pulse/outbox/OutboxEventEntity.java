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
 *
 * Why it is designed this way:
 *   What happens if a Host accepts an invitation (`PATCH /api/v1/requests/{id}`)
 *   and the database commits, but the notification service is temporarily down?
 *   If we called the notification API directly, the notification would be lost!
 *   Instead, Page 14 & 15 state: "Outbox is in the same database transaction as
 *   request changes. Set accepted, increment accepted_count, and write an outbox
 *   event; commit together."
 * ============================================================================
 */
// @Entity marks this class as a JPA persistent entity managed by Hibernate.
@Entity
// @Table binds this entity to the `outbox_events` table in PostgreSQL.
@Table(name = "outbox_events")
public class OutboxEventEntity {

    // Unique primary key for the outbox event (e.g., `obx_a1b2c3d4`).
    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    // Domain aggregate type that emitted the event (e.g., `"CONNECTION_REQUEST"`, `"CONTENT_ITEM"`).
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    // ID of the specific domain aggregate instance (e.g., `req_01` or `story_01`).
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    // Semantic event name (e.g., `"REQUEST_CREATED"`, `"REQUEST_ACCEPTED"`, `"CONTENT_PUBLISHED"`).
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    // Serialized JSON payload containing context for downstream notification/worker handlers.
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    // Processing state of the outbox event: `"PENDING"`, `"PROCESSED"`, or `"FAILED"`.
    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    // Number of delivery attempts made by `OutboxWorkerService` so far.
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    // Next UTC timestamp at which the background worker should attempt to process this event (supports backoff).
    @Column(name = "next_run", nullable = false)
    private Instant nextRun = Instant.now();

    // UTC timestamp when the outbox event was written inside the originating transaction.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Static factory helper that constructs a new `"PENDING"` {@link OutboxEventEntity} ready for immediate processing.
     *
     * @param aggregateType domain aggregate category (`CONNECTION_REQUEST`, `CONTENT_ITEM`)
     * @param aggregateId   ID of the domain aggregate
     * @param eventType     event type identifier (`REQUEST_ACCEPTED`, `CONTENT_PUBLISHED`, etc.)
     * @param payloadJson   serialized JSON string payload
     * @return initialized {@link OutboxEventEntity}
     */
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

    /** @return unique primary key of this outbox event */
    public String getEventId() { return eventId; }
    /** @param eventId unique primary key of this outbox event */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /** @return aggregate type (`CONNECTION_REQUEST`, `CONTENT_ITEM`, etc.) */
    public String getAggregateType() { return aggregateType; }
    /** @param aggregateType aggregate type */
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    /** @return ID of the aggregate that produced this event */
    public String getAggregateId() { return aggregateId; }
    /** @param aggregateId ID of the aggregate that produced this event */
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    /** @return event type name */
    public String getEventType() { return eventType; }
    /** @param eventType event type name */
    public void setEventType(String eventType) { this.eventType = eventType; }

    /** @return serialized JSON event payload */
    public String getPayloadJson() { return payloadJson; }
    /** @param payloadJson serialized JSON event payload */
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    /** @return current delivery status (`PENDING`, `PROCESSED`, `FAILED`) */
    public String getStatus() { return status; }
    /** @param status current delivery status */
    public void setStatus(String status) { this.status = status; }

    /** @return number of worker delivery attempts */
    public int getAttempts() { return attempts; }
    /** @param attempts number of worker delivery attempts */
    public void setAttempts(int attempts) { this.attempts = attempts; }

    /** @return UTC timestamp when this event is next eligible for processing */
    public Instant getNextRun() { return nextRun; }
    /** @param nextRun UTC timestamp when this event is next eligible for processing */
    public void setNextRun(Instant nextRun) { this.nextRun = nextRun; }

    /** @return UTC timestamp when this event was created */
    public Instant getCreatedAt() { return createdAt; }
    /** @param createdAt UTC timestamp when this event was created */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
