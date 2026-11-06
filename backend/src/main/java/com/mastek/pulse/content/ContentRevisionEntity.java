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
 * Journey: Revision-Based Review & Publishing (Proposal Pages 8, 11, 15).
 * Why it is designed this way:
 *   Page 11 & 15 mandate: "Approvals bind to a revision. Any content or audience
 *   edit invalidates approval and returns the new revision to review."
 *   Each row in `content_revisions` captures an immutable snapshot (`version_number`)
 *   of the title, summary, body, tags, audio metadata, transcript, and who
 *   approved that specific revision (`approvedBy`).
 * ============================================================================
 */
@Entity
@Table(name = "content_revisions")
public class ContentRevisionEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "content_id", nullable = false, length = 64)
    private String contentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "body_text", nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "tags_csv", nullable = false, length = 300)
    private String tagsCsv = "";

    @Column(name = "official_source", length = 160)
    private String officialSource;

    @Column(name = "external_url", length = 300)
    private String externalUrl;

    @Column(name = "has_audio", nullable = false)
    private boolean hasAudio = false;

    @Column(name = "media_asset_key", length = 200)
    private String mediaAssetKey;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "chapters_json", columnDefinition = "TEXT")
    private String chaptersJson;

    @Column(name = "related_story_id", length = 64)
    private String relatedStoryId;

    @Column(name = "reviewer_notes", length = 500)
    private String reviewerNotes;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public List<String> getTagsList() {
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }

    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }

    public String getTagsCsv() { return tagsCsv; }
    public void setTagsCsv(String tagsCsv) { this.tagsCsv = tagsCsv; }

    public String getOfficialSource() { return officialSource; }
    public void setOfficialSource(String officialSource) { this.officialSource = officialSource; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public boolean isHasAudio() { return hasAudio; }
    public void setHasAudio(boolean hasAudio) { this.hasAudio = hasAudio; }

    public String getMediaAssetKey() { return mediaAssetKey; }
    public void setMediaAssetKey(String mediaAssetKey) { this.mediaAssetKey = mediaAssetKey; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getTranscriptText() { return transcriptText; }
    public void setTranscriptText(String transcriptText) { this.transcriptText = transcriptText; }

    public String getChaptersJson() { return chaptersJson; }
    public void setChaptersJson(String chaptersJson) { this.chaptersJson = chaptersJson; }

    public String getRelatedStoryId() { return relatedStoryId; }
    public void setRelatedStoryId(String relatedStoryId) { this.relatedStoryId = relatedStoryId; }

    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
