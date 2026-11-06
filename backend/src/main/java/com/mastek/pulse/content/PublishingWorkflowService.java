package com.mastek.pulse.content;

import com.mastek.pulse.common.*;
import com.mastek.pulse.connection.InvitationEntity;
import com.mastek.pulse.connection.InvitationRepository;
import com.mastek.pulse.outbox.OutboxEventEntity;
import com.mastek.pulse.outbox.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: PublishingWorkflowService.java
 * Journey: Supporting Journey B ("Submit a contribution -> review exact revision ->
 *          publish to intended audience" - Proposal Pages 8, 11, 14, 15).
 * Why it is designed this way:
 *   Enforces the strict editorial state machine & scoped roles from Page 11:
 *   `DRAFT -> IN_REVIEW -> APPROVED -> SCHEDULED / PUBLISHED -> WITHDRAWN`
 *   Key Invariants Enforced Here:
 *   1. CREATOR: Own drafts and submissions.
 *   2. REVIEWER: Approve or return assigned content; NO SELF-APPROVAL!
 *      If `caller.id == item.authorId`, throws `403 SELF_APPROVAL_DENIED`.
 *   3. EDIT INVALIDATES APPROVAL: If an item is `APPROVED` or `SCHEDULED` and
 *      someone edits its title, summary, body, or audience, we automatically
 *      increment `currentVersion` (creating a new revision snapshot) and reset
 *      `status` back to `IN_REVIEW` (or `DRAFT`) so unreviewed changes can never
 *      be published!
 *   4. PUBLISHER: Releases `APPROVED` revisions immediately or schedules them.
 *      Admin (`ADMIN`) has no automatic editorial approval right unless they
 *      also hold `REVIEWER`.
 * ============================================================================
 */
@Service
public class PublishingWorkflowService {

    private final ContentItemRepository contentItemRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final AudienceRepository audienceRepository;
    private final AuditEventRepository auditEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdentityService identityService;
    private final FeedService feedService;

    public PublishingWorkflowService(ContentItemRepository contentItemRepository,
                                     ContentRevisionRepository contentRevisionRepository,
                                     InvitationRepository invitationRepository,
                                     UserRepository userRepository,
                                     AudienceRepository audienceRepository,
                                     AuditEventRepository auditEventRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     IdentityService identityService,
                                     FeedService feedService) {
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
        this.auditEventRepository = auditEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.identityService = identityService;
        this.feedService = feedService;
    }

    /**
     * Creates a new contribution (DRAFT or immediately submitted IN_REVIEW)
     * along with its optional Colleague Connection Invitation offer.
     */
    @Transactional
    public Map<String, Object> createOrSaveContribution(UserEntity caller, Map<String, Object> payload) {
        String title = getRequiredString(payload, "title");
        String bodyText = getRequiredString(payload, "bodyText");
        String summary = (String) payload.getOrDefault("summary",
                bodyText.length() > 140 ? bodyText.substring(0, 140) + "..." : bodyText);
        String audienceId = (String) payload.getOrDefault("audienceId", "aud_pilot");
        String contentType = (String) payload.getOrDefault("contentType", "STORY");
        String tagsCsv = (String) payload.getOrDefault("tagsCsv", "team learning,culture");
        boolean submitImmediately = Boolean.TRUE.equals(payload.get("submitForReview"));

        audienceRepository.findById(audienceId)
                .orElseThrow(() -> PulseException.unprocessable("422: Unknown audienceId: " + audienceId));

        String contentId = "story_" + UUID.randomUUID().toString().substring(0, 8);
        ContentItemEntity item = new ContentItemEntity();
        item.setId(contentId);
        item.setAuthorId(caller.getId());
        item.setAudienceId(audienceId);
        item.setContentType(contentType);
        item.setCurrentVersion(1);
        item.setStatus(submitImmediately ? "IN_REVIEW" : "DRAFT");
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        contentItemRepository.save(item);

        ContentRevisionEntity rev = new ContentRevisionEntity();
        rev.setId("rev_" + UUID.randomUUID().toString().substring(0, 8));
        rev.setContentId(contentId);
        rev.setVersionNumber(1);
        rev.setTitle(title);
        rev.setSummary(summary);
        rev.setBodyText(bodyText);
        rev.setTagsCsv(tagsCsv);
        rev.setCreatedAt(Instant.now());
        contentRevisionRepository.save(rev);

        // Optional Invitation creation (Wireframe 05 - Page 8: Capacity: 2 / Expires: 7 days)
        InvitationEntity inv = null;
        if (Boolean.TRUE.equals(payload.get("includeInvitation"))) {
            String topicLabel = (String) payload.getOrDefault("invitationTopic", "Ask me about this: " + title);
            int capacity = payload.get("invitationCapacity") instanceof Number n ? n.intValue() : 2;
            int expiresDays = payload.get("invitationExpiresDays") instanceof Number n ? n.intValue() : 7;
            if (capacity < 1 || capacity > 10) {
                throw PulseException.unprocessable("422: Invitation capacity must be between 1 and 10.");
            }
            inv = new InvitationEntity();
            inv.setId("inv_" + UUID.randomUUID().toString().substring(0, 8));
            inv.setStoryId(contentId);
            inv.setHostId(caller.getId());
            inv.setTopicLabel(topicLabel);
            inv.setDurationMinutes(15);
            inv.setCapacity(capacity);
            inv.setAcceptedCount(0);
            inv.setPaused(false);
            inv.setExpiresAt(Instant.now().plus(expiresDays, ChronoUnit.DAYS));
            invitationRepository.save(inv);
        }

        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(),
                submitImmediately ? "CONTENT_SUBMITTED" : "DRAFT_SAVED",
                "CONTENT_ITEM",
                contentId,
                "Created Revision 1 (" + item.getStatus() + ") for audience " + audienceId
        ));

        AudienceEntity aud = audienceRepository.findById(audienceId).orElse(null);
        return feedService.toContentCardDto(item, rev, caller, aud, inv, "Draft / Submission preview");
    }

    /**
     * Edits an existing content item.
     * CRITICAL INVARIANT (Page 11): "Any content or audience edit invalidates
     * approval and returns the new revision to review."
     */
    @Transactional
    public Map<String, Object> editContentAndCreateNewRevision(UserEntity caller, String contentId, Map<String, Object> payload) {
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        if (!item.getAuthorId().equals(caller.getId()) && !caller.hasRole("REVIEWER") && !caller.hasRole("PUBLISHER")) {
            throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the author or editorial staff can edit this item.");
        }

        ContentRevisionEntity latestRev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Latest revision not found"));

        String newTitle = (String) payload.getOrDefault("title", latestRev.getTitle());
        String newSummary = (String) payload.getOrDefault("summary", latestRev.getSummary());
        String newBody = (String) payload.getOrDefault("bodyText", latestRev.getBodyText());
        String newAudienceId = (String) payload.getOrDefault("audienceId", item.getAudienceId());
        String newTags = (String) payload.getOrDefault("tagsCsv", latestRev.getTagsCsv());

        int nextVersion = item.getCurrentVersion() + 1;
        ContentRevisionEntity newRev = new ContentRevisionEntity();
        newRev.setId("rev_" + UUID.randomUUID().toString().substring(0, 8));
        newRev.setContentId(contentId);
        newRev.setVersionNumber(nextVersion);
        newRev.setTitle(newTitle);
        newRev.setSummary(newSummary);
        newRev.setBodyText(newBody);
        newRev.setTagsCsv(newTags);
        newRev.setOfficialSource(latestRev.getOfficialSource());
        newRev.setExternalUrl(latestRev.getExternalUrl());
        newRev.setHasAudio(latestRev.isHasAudio());
        newRev.setMediaAssetKey(latestRev.getMediaAssetKey());
        newRev.setDurationSeconds(latestRev.getDurationSeconds());
        newRev.setTranscriptText(latestRev.getTranscriptText());
        newRev.setChaptersJson(latestRev.getChaptersJson());
        newRev.setRelatedStoryId(latestRev.getRelatedStoryId());
        // Approval is explicitly cleared on the new revision!
        newRev.setApprovedBy(null);
        newRev.setApprovedAt(null);
        contentRevisionRepository.save(newRev);

        String previousStatus = item.getStatus();
        item.setAudienceId(newAudienceId);
        item.setCurrentVersion(nextVersion);
        // If it was APPROVED, SCHEDULED, or PUBLISHED, editing returns the new revision to IN_REVIEW!
        if ("APPROVED".equals(previousStatus) || "SCHEDULED".equals(previousStatus) || "PUBLISHED".equals(previousStatus)) {
            item.setStatus("IN_REVIEW");
            item.setScheduledFor(null);
        }
        item.setUpdatedAt(Instant.now());
        contentItemRepository.save(item);

        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(),
                "REVISION_CREATED_APPROVAL_INVALIDATED",
                "CONTENT_REVISION",
                newRev.getId(),
                "Edited content/audience on " + contentId + " -> created Revision " + nextVersion + " and reset status from " + previousStatus + " to " + item.getStatus()
        ));

        return toPortalQueueItemDto(item, newRev);
    }

    /**
     * Submits a DRAFT item to IN_REVIEW.
     */
    @Transactional
    public Map<String, Object> submitForReview(UserEntity caller, String contentId) {
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        if (!item.getAuthorId().equals(caller.getId()) && !caller.hasRole("CREATOR")) {
            throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the creator can submit this draft for review.");
        }

        item.setStatus("IN_REVIEW");
        item.setUpdatedAt(Instant.now());
        contentItemRepository.save(item);

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow();

        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(),
                "SUBMITTED_FOR_REVIEW",
                "CONTENT_REVISION",
                rev.getId(),
                "Submitted Revision " + item.getCurrentVersion() + " of " + contentId + " for review."
        ));

        return toPortalQueueItemDto(item, rev);
    }

    /**
     * Reviewer approves or requests changes on a specific revisionNumber.
     * Enforces:
     * 1. Caller must have role `REVIEWER` (Note: `ADMIN` alone cannot approve editorial content!).
     * 2. No Self-Approval (`caller.id != item.authorId`).
     * 3. Revision number must match `item.currentVersion` (`409 STALE_VERSION` otherwise).
     */
    @Transactional
    public Map<String, Object> reviewRevision(UserEntity caller, String contentId, Map<String, Object> payload) {
        identityService.requireRole(caller, "REVIEWER");

        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        if (item.getAuthorId().equals(caller.getId())) {
            throw PulseException.forbidden(
                    "SELF_APPROVAL_DENIED",
                    "403: Reviewers cannot self-approve their own submissions (Author == Reviewer)."
            );
        }

        int expectedRevision = payload.get("revisionNumber") instanceof Number n
                ? n.intValue()
                : item.getCurrentVersion();
        if (expectedRevision != item.getCurrentVersion()) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Revision " + expectedRevision + " is stale; current version is " + item.getCurrentVersion()
            );
        }

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, expectedRevision)
                .orElseThrow(() -> PulseException.notFound("404: Revision not found"));

        String decision = getRequiredString(payload, "decision").toUpperCase(); // APPROVE or REQUEST_CHANGES
        String notes = (String) payload.getOrDefault("notes", "");

        rev.setReviewerNotes(notes);
        if ("APPROVE".equals(decision)) {
            rev.setApprovedBy(caller.getId());
            rev.setApprovedAt(Instant.now());
            item.setStatus("APPROVED");
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "REVISION_APPROVED", "CONTENT_REVISION", rev.getId(),
                    "Reviewer " + caller.getDisplayName() + " approved Revision " + expectedRevision + " of " + contentId
            ));
        } else if ("REQUEST_CHANGES".equals(decision)) {
            rev.setApprovedBy(null);
            rev.setApprovedAt(null);
            item.setStatus("DRAFT");
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "CHANGES_REQUESTED", "CONTENT_REVISION", rev.getId(),
                    "Reviewer " + caller.getDisplayName() + " requested changes on Revision " + expectedRevision + ": " + notes
            ));
        } else {
            throw PulseException.unprocessable("422: decision must be APPROVE or REQUEST_CHANGES");
        }

        item.setUpdatedAt(Instant.now());
        contentRevisionRepository.save(rev);
        contentItemRepository.save(item);
        return toPortalQueueItemDto(item, rev);
    }

    /**
     * Publisher releases an APPROVED revision immediately or schedules it.
     */
    @Transactional
    public Map<String, Object> publishOrSchedule(UserEntity caller, String contentId, Map<String, Object> payload) {
        identityService.requireRole(caller, "PUBLISHER");

        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Revision not found"));

        if (!"APPROVED".equals(item.getStatus()) || rev.getApprovedBy() == null) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Cannot publish Revision " + item.getCurrentVersion() + " because it is not in APPROVED state (current status: " + item.getStatus() + ")."
            );
        }

        String mode = (String) payload.getOrDefault("mode", "IMMEDIATE"); // IMMEDIATE or SCHEDULED
        if ("SCHEDULED".equalsIgnoreCase(mode)) {
            String scheduledForStr = getRequiredString(payload, "scheduledFor");
            Instant scheduledFor = Instant.parse(scheduledForStr);
            item.setStatus("SCHEDULED");
            item.setScheduledFor(scheduledFor);
            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONTENT_ITEM", item.getId(), "SCHEDULED_PUBLISH_DUE",
                    "{\"contentId\":\"" + item.getId() + "\",\"versionNumber\":" + rev.getVersionNumber() + "}"
            ));
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "CONTENT_SCHEDULED", "CONTENT_ITEM", item.getId(),
                    "Publisher " + caller.getDisplayName() + " scheduled Revision " + rev.getVersionNumber() + " for " + scheduledFor
            ));
        } else {
            item.setStatus("PUBLISHED");
            item.setPublishedVersion(rev.getVersionNumber());
            item.setPublishedAt(Instant.now());
            item.setScheduledFor(null);
            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONTENT_ITEM", item.getId(), "CONTENT_PUBLISHED",
                    "{\"contentId\":\"" + item.getId() + "\",\"versionNumber\":" + rev.getVersionNumber() + "}"
            ));
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "CONTENT_PUBLISHED", "CONTENT_ITEM", item.getId(),
                    "Publisher " + caller.getDisplayName() + " published Revision " + rev.getVersionNumber() + " to audience " + item.getAudienceId()
            ));
        }

        item.setUpdatedAt(Instant.now());
        contentItemRepository.save(item);
        return toPortalQueueItemDto(item, rev);
    }

    /**
     * Publisher or Moderator withdraws published content.
     */
    @Transactional
    public Map<String, Object> withdrawContent(UserEntity caller, String contentId, String reason) {
        if (!caller.hasRole("PUBLISHER") && !caller.hasRole("MODERATOR")) {
            throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Requires PUBLISHER or MODERATOR role to withdraw content.");
        }

        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        item.setStatus("WITHDRAWN");
        item.setPublishedVersion(null);
        item.setUpdatedAt(Instant.now());
        contentItemRepository.save(item);

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow();

        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(), "CONTENT_WITHDRAWN", "CONTENT_ITEM", item.getId(),
                "Withdrawn by " + caller.getDisplayName() + ". Reason: " + (reason == null || reason.isBlank() ? "Editorial withdrawal" : reason)
        ));

        return toPortalQueueItemDto(item, rev);
    }

    /**
     * Returns all items & revisions for the Publishing Portal Review Queue (Page 11 Wireframe).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPortalQueue() {
        List<ContentItemEntity> allItems = contentItemRepository.findAllByOrderByUpdatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ContentItemEntity item : allItems) {
            contentRevisionRepository.findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion())
                    .ifPresent(rev -> result.add(toPortalQueueItemDto(item, rev)));
        }
        return result;
    }

    public Map<String, Object> toPortalQueueItemDto(ContentItemEntity item, ContentRevisionEntity rev) {
        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        AudienceEntity audience = audienceRepository.findById(item.getAudienceId()).orElse(null);
        UserEntity approver = rev.getApprovedBy() != null ? userRepository.findById(rev.getApprovedBy()).orElse(null) : null;

        // Note: Culture Cast Episode 03 (`cc_ep_03`) has publishedVersion=2 while currentVersion=3 is in review!
        String effectivePortalStatus = (item.getPublishedVersion() != null
                && item.getCurrentVersion() > item.getPublishedVersion()
                && rev.getApprovedBy() == null)
                ? "IN_REVIEW"
                : item.getStatus();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("revisionId", rev.getId());
        map.put("contentType", item.getContentType());
        map.put("title", rev.getTitle());
        map.put("summary", rev.getSummary());
        map.put("bodyText", rev.getBodyText());
        map.put("authorId", item.getAuthorId());
        map.put("authorName", author != null ? author.getDisplayName() : item.getAuthorId());
        map.put("audienceId", item.getAudienceId());
        map.put("audienceName", audience != null ? audience.getName() : item.getAudienceId());
        map.put("status", effectivePortalStatus);
        map.put("currentVersion", item.getCurrentVersion());
        map.put("publishedVersion", item.getPublishedVersion());
        map.put("hasAudio", rev.isHasAudio());
        map.put("transcriptChecked", !rev.isHasAudio() || (rev.getTranscriptText() != null && !rev.getTranscriptText().isBlank()));
        map.put("mediaReady", !rev.isHasAudio() || rev.getMediaAssetKey() != null);
        map.put("approvedBy", rev.getApprovedBy());
        map.put("approvedByName", approver != null ? approver.getDisplayName() : null);
        map.put("reviewerNotes", rev.getReviewerNotes());
        map.put("scheduledFor", item.getScheduledFor() != null ? item.getScheduledFor().toString() : null);
        map.put("updatedAt", item.getUpdatedAt().toString());
        return map;
    }

    private String getRequiredString(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null || val.toString().isBlank()) {
            throw PulseException.unprocessable("422: Missing required field: " + key);
        }
        return val.toString().trim();
    }
}
