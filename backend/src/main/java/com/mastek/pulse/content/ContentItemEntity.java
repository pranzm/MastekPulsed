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
 *          (Proposal Pages 3, 6, 8, 9, 10, 11, 15).
 * Why it is designed this way:
 *   Represents the parent identity of a piece of content (`STORY`, `UPDATE`,
 *   `CULTURE_CAST`, `RADIO_QUEUE`). Notice how `currentVersion` tracks the
 *   latest draft/review revision while `publishedVersion` tracks the revision
 *   currently live to employees! This guarantees that editing an approved or
 *   published item never leaks unapproved edits to readers.
 * ============================================================================
 */
@Entity
@Table(name = "content_items")
public class ContentItemEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    @Column(name = "audience_id", nullable = false, length = 64)
    private String audienceId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType; // STORY, UPDATE, CULTURE_CAST, RADIO_QUEUE

    @Column(name = "status", nullable = false, length = 32)
    private String status; // DRAFT, IN_REVIEW, APPROVED, SCHEDULED, PUBLISHED, WITHDRAWN

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAudienceId() { return audienceId; }
    public void setAudienceId(String audienceId) { this.audienceId = audienceId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

    public Integer getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(Integer publishedVersion) { this.publishedVersion = publishedVersion; }

    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
