package com.mastek.pulse.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ContentItemEntity.java
 * Journey: Discovery Feed, Mastek Radio, Culture Cast & Controlled Publishing
 *          (Proposal Pages 3, 6, 8, 9, 10, 11, 15 + Campfire CF-010).
 *
 * Why it is designed this way:
 *   Represents the parent identity of a piece of content (`STORY`, `UPDATE`,
 *   `CULTURE_CAST`, `RADIO_QUEUE`).
 *   1. `currentVersion` tracks the latest draft/review revision while
 *      `publishedVersion` tracks the revision currently live to employees.
 *   2. `publishedAudienceId` tracks the immutable audience of the live
 *      `publishedVersion` snapshot, while `audienceId` tracks the working
 *      audience of `currentVersion`. This guarantees that editing a draft's
 *      audience never broadens access to the previously published text!
 * ============================================================================
 */
// @Entity registers this class as a JPA persistent entity managed by Hibernate.
@Entity
// @Table maps this entity to the `content_items` table in PostgreSQL.
@Table(name = "content_items")
public class ContentItemEntity {

    // Primary key of the content item (e.g., `story_daniel_01`, `cast_03`).
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    // Foreign key referencing `users.id` — the creator/author who owns this content item.
    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    // Foreign key referencing `audiences.id` — the working target audience for `currentVersion`.
    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    // The audience ID bound to the currently live `publishedVersion` snapshot (null until published).
    @Column(name = "published_audience_id", length = 64)
    private String publishedAudienceId;

    // Discriminator for the content format: `STORY`, `UPDATE`, `CULTURE_CAST`, or `RADIO_QUEUE`.
    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType; // STORY, UPDATE, CULTURE_CAST, RADIO_QUEUE

    // Editorial lifecycle state: `DRAFT`, `IN_REVIEW`, `APPROVED`, `SCHEDULED`, `PUBLISHED`, or `WITHDRAWN`.
    @Column(name = "status", nullable = false, length = 32)
    private String status; // DRAFT, IN_REVIEW, APPROVED, SCHEDULED, PUBLISHED, WITHDRAWN

    // Version number of the latest working revision in `content_revisions` (starts at 1).
    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    // Version number of the revision currently published to employees (`null` if never published or if withdrawn).
    @Column(name = "published_version")
    private Integer publishedVersion;

    // Optional future UTC timestamp when an `APPROVED` revision is scheduled to be published by the background worker.
    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    // Optional UTC timestamp when this content item expires.
    @Column(name = "expires_at")
    private Instant expiresAt;

    // UTC timestamp when the currently live `publishedVersion` was released.
    @Column(name = "published_at")
    private Instant publishedAt;

    // UTC timestamp when the content item was first created.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // UTC timestamp when the content item metadata or status was last updated.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Returns the audience governing the currently published snapshot (falling back
     * to `audienceId` only if `publishedAudienceId` was not yet populated).
     *
     * @return effective audience ID for published employee reads
     */
    public String getEffectivePublishedAudienceId() {
        // Prefer the immutable `publishedAudienceId` recorded at publication time; fall back to `audienceId` for seeded rows.
        return publishedAudienceId != null ? publishedAudienceId : audienceId;
    }

    /** @return unique content item ID */
    public String getId() { return id; }
    /** @param id unique content item ID */
    public void setId(String id) { this.id = id; }

    /** @return user ID of the content author */
    public String getAuthorId() { return authorId; }
    /** @param authorId user ID of the content author */
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    /** @return working target audience ID for `currentVersion` */
    public String getAudienceId() { return audienceId; }
    /** @param audienceId working target audience ID for `currentVersion` */
    public void setAudienceId(String audienceId) { this.audienceId = audienceId; }

    /** @return immutable audience ID bound to `publishedVersion` */
    public String getPublishedAudienceId() { return publishedAudienceId; }
    /** @param publishedAudienceId immutable audience ID bound to `publishedVersion` */
    public void setPublishedAudienceId(String publishedAudienceId) { this.publishedAudienceId = publishedAudienceId; }

    /** @return content type (`STORY`, `UPDATE`, `CULTURE_CAST`, `RADIO_QUEUE`) */
    public String getContentType() { return contentType; }
    /** @param contentType content type (`STORY`, `UPDATE`, `CULTURE_CAST`, `RADIO_QUEUE`) */
    public void setContentType(String contentType) { this.contentType = contentType; }

    /** @return editorial lifecycle status (`DRAFT`, `IN_REVIEW`, `APPROVED`, `SCHEDULED`, `PUBLISHED`, `WITHDRAWN`) */
    public String getStatus() { return status; }
    /** @param status editorial lifecycle status */
    public void setStatus(String status) { this.status = status; }

    /** @return latest revision number in `content_revisions` */
    public int getCurrentVersion() { return currentVersion; }
    /** @param currentVersion latest revision number in `content_revisions` */
    public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

    /** @return live published revision number, or `null` if unpublished/withdrawn */
    public Integer getPublishedVersion() { return publishedVersion; }
    /** @param publishedVersion live published revision number, or `null` */
    public void setPublishedVersion(Integer publishedVersion) { this.publishedVersion = publishedVersion; }

    /** @return scheduled publication UTC timestamp, or `null` */
    public Instant getScheduledFor() { return scheduledFor; }
    /** @param scheduledFor scheduled publication UTC timestamp */
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }

    /** @return content expiration UTC timestamp, or `null` */
    public Instant getExpiresAt() { return expiresAt; }
    /** @param expiresAt content expiration UTC timestamp */
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    /** @return UTC timestamp of publication, or `null` */
    public Instant getPublishedAt() { return publishedAt; }
    /** @param publishedAt UTC timestamp of publication */
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    /** @return UTC creation timestamp */
    public Instant getCreatedAt() { return createdAt; }
    /** @param createdAt UTC creation timestamp */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** @return UTC last-updated timestamp */
    public Instant getUpdatedAt() { return updatedAt; }
    /** @param updatedAt UTC last-updated timestamp */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
