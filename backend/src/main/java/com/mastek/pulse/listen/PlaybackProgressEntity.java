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
 * Why it is designed this way:
 *   Maps to `playback_progress` (`PRIMARY KEY (user_id, content_id)`).
 *   Page 10 states: "Resume from the last server-saved listening position".
 *   When Asha pauses Culture Cast Episode 03 at `02:10` (130 seconds) on her
 *   mobile commute and opens Employee Web at her desk, she resumes at `02:10`.
 * ============================================================================
 */
@Entity
@Table(name = "playback_progress")
@IdClass(PlaybackProgressEntity.PlaybackProgressId.class)
public class PlaybackProgressEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Id
    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds = 0;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public int getPositionSeconds() { return positionSeconds; }
    public void setPositionSeconds(int positionSeconds) { this.positionSeconds = positionSeconds; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static class PlaybackProgressId implements Serializable {
        private String userId;
        private String contentId;

        public PlaybackProgressId() {}

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
