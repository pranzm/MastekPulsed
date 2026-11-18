package com.mastek.pulse.listen;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PlaybackProgressEntity.java
 * Journey: Supporting Journey A ("Listen to Mastek Radio and Culture Cast,
 *          with transcripts and playback resume" - Proposal Pages 3, 9, 10).
 *
 * Why it is designed this way:
 *   Maps to `playback_progress` (`PRIMARY KEY (user_id, content_id)`).
 *   Page 10 states: "Resume from the last server-saved listening position".
 *   When Asha pauses Culture Cast Episode 03 at `02:10` (130 seconds) on her
 *   mobile commute and opens Employee Web at her desk, she resumes at `02:10`.
 * ============================================================================
 */
// @Entity marks this class as a JPA persistent entity.
@Entity
// @Table binds this entity to the `playback_progress` table in PostgreSQL.
@Table(name = "playback_progress")
// @IdClass specifies the composite primary key class `(userId, contentId)` required by JPA for multi-column primary keys.
@IdClass(PlaybackProgressEntity.PlaybackProgressId.class)
public class PlaybackProgressEntity {

    // First half of the composite primary key: foreign key to `users.id`.
    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // Second half of the composite primary key: foreign key to `content_items.id`.
    @Id
    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    // Last saved playback position in seconds (e.g., `130` for `02:10`).
    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds = 0;

    // Total duration of the audio episode in seconds (e.g., `480` for `08:00`).
    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 0;

    // UTC timestamp when the user's playback position was last saved.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** @return user ID of the listener */
    public String getUserId() { return userId; }
    /** @param userId user ID of the listener */
    public void setUserId(String userId) { this.userId = userId; }

    /** @return content ID of the audio episode or radio queue */
    public String getContentId() { return contentId; }
    /** @param contentId content ID of the audio episode or radio queue */
    public void setContentId(String contentId) { this.contentId = contentId; }

    /** @return saved resume position in seconds */
    public int getPositionSeconds() { return positionSeconds; }
    /** @param positionSeconds saved resume position in seconds */
    public void setPositionSeconds(int positionSeconds) { this.positionSeconds = positionSeconds; }

    /** @return total audio duration in seconds */
    public int getDurationSeconds() { return durationSeconds; }
    /** @param durationSeconds total audio duration in seconds */
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    /** @return UTC timestamp of last progress save */
    public Instant getUpdatedAt() { return updatedAt; }
    /** @param updatedAt UTC timestamp of last progress save */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Composite primary key class (`user_id`, `content_id`) for {@link PlaybackProgressEntity}.
     * JPA requires `@IdClass` classes to implement {@link Serializable}, have a no-arg constructor,
     * and implement value-based {@link #equals(Object)} and {@link #hashCode()}.
     */
    public static class PlaybackProgressId implements Serializable {
        // Listener's user ID.
        private String userId;
        // Audio item's content ID.
        private String contentId;

        /** Default no-arg constructor required by JPA/Hibernate reflection. */
        public PlaybackProgressId() {}

        /**
         * Convenience constructor for looking up a progress record by `(userId, contentId)`.
         *
         * @param userId    listener's user ID
         * @param contentId audio item's content ID
         */
        public PlaybackProgressId(String userId, String contentId) {
            this.userId = userId;
            this.contentId = contentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlaybackProgressId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(contentId, that.contentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, contentId);
        }
    }
}
