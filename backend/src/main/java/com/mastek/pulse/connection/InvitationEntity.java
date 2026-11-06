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
 * File: InvitationEntity.java
 * Journey: Primary Journey ("Discover -> Review & Send Invitation -> Host Accepts").
 * Why it is designed this way:
 *   Maps to the `invitations` table in `V1__init_schema.sql`. Each story can
 *   have an optional voluntary invitation offer (e.g., "Ask me about this — 15 min
 *   conversation / 2 places available").
 *   Notice the `remainingPlaces()` and `computeStatusLabel(boolean hostPaused)`
 *   methods: they enforce Proposal Page 6 ("A full or expired offer disables the
 *   invitation action and explains why").
 * ============================================================================
 */
@Entity
@Table(name = "invitations")
public class InvitationEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "story_id", nullable = false, unique = true, length = 64)
    private String storyId;

    @Column(name = "host_id", nullable = false, length = 64)
    private String hostId;

    @Column(name = "topic_label", nullable = false, length = 160)
    private String topicLabel;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 15;

    @Column(name = "capacity", nullable = false)
    private int capacity = 2;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount = 0;

    @Column(name = "paused", nullable = false)
    private boolean paused = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public int getRemainingPlaces() {
        return Math.max(0, capacity - acceptedCount);
    }

    public String computeStatusLabel(boolean hostGlobalPaused) {
        if (paused || hostGlobalPaused) {
            return "PAUSED";
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return "EXPIRED";
        }
        if (acceptedCount >= capacity) {
            return "FULL";
        }
        return "OPEN";
    }

    public boolean isOpenForRequests(boolean hostGlobalPaused) {
        return "OPEN".equals(computeStatusLabel(hostGlobalPaused));
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStoryId() { return storyId; }
    public void setStoryId(String storyId) { this.storyId = storyId; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getTopicLabel() { return topicLabel; }
    public void setTopicLabel(String topicLabel) { this.topicLabel = topicLabel; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
