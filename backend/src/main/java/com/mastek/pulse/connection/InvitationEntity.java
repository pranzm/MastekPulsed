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
 *
 * Why it is designed this way:
 *   Maps to the `invitations` table in `V1__init_schema.sql`. Each story can
 *   have an optional voluntary invitation offer (e.g., "Ask me about this — 15 min
 *   conversation / 2 places available").
 *   Notice the `getRemainingPlaces()` and `computeStatusLabel(boolean hostPaused)`
 *   methods: they enforce Proposal Page 6 ("A full or expired offer disables the
 *   invitation action and explains why").
 * ============================================================================
 */
// @Entity marks this class as a JPA entity managed by Hibernate.
@Entity
// @Table binds this entity to the `invitations` table in PostgreSQL.
@Table(name = "invitations")
public class InvitationEntity {

    // Primary key of the invitation offer (e.g., `inv_daniel_01`).
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    // Foreign key to `content_items.id` (`unique = true` because each story has at most one voluntary invitation offer).
    @Column(name = "story_id", nullable = false, unique = true, length = 64)
    private String storyId;

    // Foreign key to `users.id` identifying the story author / host offering their time.
    @Column(name = "host_id", nullable = false, length = 64)
    private String hostId;

    // Short prompt explaining what the host is happy to chat about (e.g., "Ask me about handoff checklists").
    @Column(name = "topic_label", nullable = false, length = 160)
    private String topicLabel;

    // Suggested length of the informal conversation in minutes (default: 15 mins to protect host workload).
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 15;

    // Maximum number of requests the host is willing to accept for this offer (default: 2 places).
    @Column(name = "capacity", nullable = false)
    private int capacity = 2;

    // Number of requests currently accepted (`accepted_count <= capacity` enforced by SQL CHECK + pessimistic lock).
    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount = 0;

    // Whether the host has paused this specific invitation offer.
    @Column(name = "paused", nullable = false)
    private boolean paused = false;

    // UTC timestamp after which new requests can no longer be sent or accepted.
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // UTC timestamp when the invitation offer was created.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Computes how many open places remain on this invitation (`max(0, capacity - acceptedCount)`).
     *
     * @return non-negative remaining places count
     */
    public int getRemainingPlaces() {
        return Math.max(0, capacity - acceptedCount);
    }

    /**
     * Computes the human-readable availability status label for the story card and detail view.
     * Priority order: `PAUSED` -> `EXPIRED` -> `FULL` -> `OPEN`.
     *
     * @param hostGlobalPaused whether the host has paused all invitations on their user profile
     * @return `"PAUSED"`, `"EXPIRED"`, `"FULL"`, or `"OPEN"`
     */
    public String computeStatusLabel(boolean hostGlobalPaused) {
        // 1. Check if either the invitation itself or the host's global availability is paused.
        if (paused || hostGlobalPaused) {
            return "PAUSED";
        }
        // 2. Check if the invitation's expiry timestamp is in the past.
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return "EXPIRED";
        }
        // 3. Check if all available places have already been accepted.
        if (acceptedCount >= capacity) {
            return "FULL";
        }
        // 4. Otherwise, the offer is open for colleague requests!
        return "OPEN";
    }

    /**
     * Convenience helper returning `true` only when {@link #computeStatusLabel(boolean)} is `"OPEN"`.
     *
     * @param hostGlobalPaused whether the host has globally paused new invitations
     * @return `true` if open for new requests, `false` otherwise
     */
    public boolean isOpenForRequests(boolean hostGlobalPaused) {
        return "OPEN".equals(computeStatusLabel(hostGlobalPaused));
    }

    /** @return primary key of this invitation */
    public String getId() { return id; }
    /** @param id primary key of this invitation */
    public void setId(String id) { this.id = id; }

    /** @return ID of the parent story in `content_items` */
    public String getStoryId() { return storyId; }
    /** @param storyId ID of the parent story in `content_items` */
    public void setStoryId(String storyId) { this.storyId = storyId; }

    /** @return user ID of the host offering this conversation */
    public String getHostId() { return hostId; }
    /** @param hostId user ID of the host offering this conversation */
    public void setHostId(String hostId) { this.hostId = hostId; }

    /** @return short topic prompt displayed on the invitation card */
    public String getTopicLabel() { return topicLabel; }
    /** @param topicLabel short topic prompt displayed on the invitation card */
    public void setTopicLabel(String topicLabel) { this.topicLabel = topicLabel; }

    /** @return conversation duration in minutes (typically 15) */
    public int getDurationMinutes() { return durationMinutes; }
    /** @param durationMinutes conversation duration in minutes */
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    /** @return maximum number of colleagues who can be accepted */
    public int getCapacity() { return capacity; }
    /** @param capacity maximum number of colleagues who can be accepted */
    public void setCapacity(int capacity) { this.capacity = capacity; }

    /** @return number of requests accepted so far */
    public int getAcceptedCount() { return acceptedCount; }
    /** @param acceptedCount updated number of accepted requests */
    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }

    /** @return `true` if this invitation offer is paused */
    public boolean isPaused() { return paused; }
    /** @param paused whether this invitation offer is paused */
    public void setPaused(boolean paused) { this.paused = paused; }

    /** @return UTC expiration timestamp */
    public Instant getExpiresAt() { return expiresAt; }
    /** @param expiresAt UTC expiration timestamp */
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    /** @return UTC creation timestamp */
    public Instant getCreatedAt() { return createdAt; }
    /** @param createdAt UTC creation timestamp */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
