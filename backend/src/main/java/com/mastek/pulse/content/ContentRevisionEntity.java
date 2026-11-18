package com.mastek.pulse.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ContentRevisionEntity.java
 * Journey: Revision-Based Review & Publishing (Proposal Pages 8, 11, 15 + CF-010).
 *
 * Why it is designed this way:
 *   Page 11 & 15 mandate: "Approvals bind to a revision. Any content or audience
 *   edit invalidates approval and returns the new revision to review."
 *   Each row in `content_revisions` captures an immutable snapshot (`version_number`)
 *   of the title, summary, body, target `audienceId`, tags, audio metadata,
 *   transcript, and who approved that specific revision (`approvedBy`).
 * ============================================================================
 */
// @Entity marks this class as a JPA persistent entity managed by Hibernate.
@Entity
// @Table binds this entity to the `content_revisions` table (`UNIQUE (content_id, version_number)` in SQL).
@Table(name = "content_revisions")
public class ContentRevisionEntity {

    // Unique primary key for this specific revision row (e.g., `rev_daniel_01_v1`).
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    // Foreign key referencing `content_items.id`.
    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    // Sequential 1-based revision number (`1`, `2`, `3`, ...) for this content item.
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    // Target `audience_id` bound to this specific revision snapshot.
    @Column(name = "audience_id", length = 64)
    private String audienceId;

    // Headline title of the story, update, or audio episode in this revision.
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // Short summary displayed on Discovery Feed cards.
    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    // Full article or show-notes body text (`TEXT` column in PostgreSQL).
    @Column(name = "body_text", nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    // Comma-separated interest tags (e.g., `"team learning,delivery excellence"`).
    @Column(name = "tags_csv", nullable = false, length = 300)
    private String tagsCsv = "";

    // Optional official source attribution for curated company updates (e.g., `"People & Culture Team"`).
    @Column(name = "official_source", length = 160)
    private String officialSource;

    // Optional link to the canonical intranet page or external document for company updates.
    @Column(name = "external_url", length = 300)
    private String externalUrl;

    // Flag indicating whether this revision includes playable audio (`true` for Mastek Radio & Culture Cast).
    @Column(name = "has_audio", nullable = false)
    private boolean hasAudio = false;

    // Storage key of the audio asset in object storage (verified before publishing audio items).
    @Column(name = "media_asset_key", length = 200)
    private String mediaAssetKey;

    // Audio playback duration in seconds.
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Full readable transcript text required for accessibility and low-bandwidth reading (`Proposal Page 9`).
    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    // JSON array string of chapter timestamps and titles for Culture Cast / Mastek Radio.
    @Column(name = "chapters_json", columnDefinition = "TEXT")
    private String chaptersJson;

    // Optional `content_items.id` of a written story discussed in this audio episode (`Listen -> Read -> Connect`).
    @Column(name = "related_story_id", length = 64)
    private String relatedStoryId;

    // Editorial feedback notes recorded by a reviewer when approving or requesting changes.
    @Column(name = "reviewer_notes", length = 500)
    private String reviewerNotes;

    // User ID of the `REVIEWER` who approved this specific revision (`null` if unapproved; must never equal `author_id`).
    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    // UTC timestamp when this specific revision was approved.
    @Column(name = "approved_at")
    private Instant approvedAt;

    // UTC timestamp when this revision snapshot was created.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Parses the comma-separated `tagsCsv` column into a trimmed list of non-empty tag strings.
     *
     * @return immutable list of tag strings (or empty list if `tagsCsv` is null/blank)
     */
    public List<String> getTagsList() {
        // Return an empty list if no tags are stored.
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return List.of();
        }
        // Split on commas, trim whitespace, discard empty tokens, and collect to an unmodifiable list.
        return Arrays.stream(tagsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** @return unique primary key of this revision */
    public String getId() { return id; }
    /** @param id unique primary key of this revision */
    public void setId(String id) { this.id = id; }

    /** @return ID of the parent {@link ContentItemEntity} */
    public String getContentId() { return contentId; }
    /** @param contentId ID of the parent {@link ContentItemEntity} */
    public void setContentId(String contentId) { this.contentId = contentId; }

    /** @return 1-based revision number */
    public int getVersionNumber() { return versionNumber; }
    /** @param versionNumber 1-based revision number */
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    /** @return target audience ID captured on this revision */
    public String getAudienceId() { return audienceId; }
    /** @param audienceId target audience ID captured on this revision */
    public void setAudienceId(String audienceId) { this.audienceId = audienceId; }

    /** @return revision title */
    public String getTitle() { return title; }
    /** @param title revision title */
    public void setTitle(String title) { this.title = title; }

    /** @return short card summary */
    public String getSummary() { return summary; }
    /** @param summary short card summary */
    public void setSummary(String summary) { this.summary = summary; }

    /** @return full body text */
    public String getBodyText() { return bodyText; }
    /** @param bodyText full body text */
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    /** @return comma-separated tags string */
    public String getTagsCsv() { return tagsCsv; }
    /** @param tagsCsv comma-separated tags string */
    public void setTagsCsv(String tagsCsv) { this.tagsCsv = tagsCsv; }

    /** @return official source label for updates, or `null` */
    public String getOfficialSource() { return officialSource; }
    /** @param officialSource official source label for updates */
    public void setOfficialSource(String officialSource) { this.officialSource = officialSource; }

    /** @return external URL for updates, or `null` */
    public String getExternalUrl() { return externalUrl; }
    /** @param externalUrl external URL for updates */
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    /** @return `true` if this revision has playable audio */
    public boolean isHasAudio() { return hasAudio; }
    /** @param hasAudio whether this revision has playable audio */
    public void setHasAudio(boolean hasAudio) { this.hasAudio = hasAudio; }

    /** @return media asset storage key, or `null` */
    public String getMediaAssetKey() { return mediaAssetKey; }
    /** @param mediaAssetKey media asset storage key */
    public void setMediaAssetKey(String mediaAssetKey) { this.mediaAssetKey = mediaAssetKey; }

    /** @return audio duration in seconds, or `null` */
    public Integer getDurationSeconds() { return durationSeconds; }
    /** @param durationSeconds audio duration in seconds */
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    /** @return full readable transcript text, or `null` */
    public String getTranscriptText() { return transcriptText; }
    /** @param transcriptText full readable transcript text */
    public void setTranscriptText(String transcriptText) { this.transcriptText = transcriptText; }

    /** @return JSON string of chapter markers, or `null` */
    public String getChaptersJson() { return chaptersJson; }
    /** @param chaptersJson JSON string of chapter markers */
    public void setChaptersJson(String chaptersJson) { this.chaptersJson = chaptersJson; }

    /** @return ID of a related story linked from this audio episode, or `null` */
    public String getRelatedStoryId() { return relatedStoryId; }
    /** @param relatedStoryId ID of a related story linked from this audio episode */
    public void setRelatedStoryId(String relatedStoryId) { this.relatedStoryId = relatedStoryId; }

    /** @return reviewer notes recorded during review, or `null` */
    public String getReviewerNotes() { return reviewerNotes; }
    /** @param reviewerNotes reviewer notes recorded during review */
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    /** @return user ID of the reviewer who approved this revision, or `null` */
    public String getApprovedBy() { return approvedBy; }
    /** @param approvedBy user ID of the reviewer who approved this revision */
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    /** @return UTC timestamp of approval, or `null` */
    public Instant getApprovedAt() { return approvedAt; }
    /** @param approvedAt UTC timestamp of approval */
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    /** @return UTC creation timestamp of this revision */
    public Instant getCreatedAt() { return createdAt; }
    /** @param createdAt UTC creation timestamp of this revision */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
