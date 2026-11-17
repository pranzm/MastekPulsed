package com.mastek.pulse.content;

import com.mastek.pulse.common.*;
import com.mastek.pulse.connection.InvitationEntity;
import com.mastek.pulse.connection.InvitationRepository;
import com.mastek.pulse.outbox.OutboxEventEntity;
import com.mastek.pulse.outbox.OutboxEventRepository;
import com.mastek.pulse.recognition.RecognitionService;
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
 *          publish to intended audience") + Campfire Recognition Trigger
 *          (Proposal Pages 8, 11, 14, 15, 16, 17 & Stories CF-004, CF-010 / D2 & D4).
 * Why it is designed this way:
 *   Enforces the strict editorial state machine & scoped roles from Page 11 + D2:
 *   `DRAFT -> IN_REVIEW -> APPROVED -> SCHEDULED / PUBLISHED -> WITHDRAWN`
 *   Key Invariants Enforced Here:
 *   1. CREATOR OWNERSHIP: Only the author with `CREATOR` role can submit their
 *      own `DRAFT` revision for review (`403` if another creator tries).
 *   2. SCOPED REVIEWER & EXACT REVISION: Reviewer must hold `REVIEWER` role,
 *      have access to the item's `audienceId`, not be the author (`403 SELF_APPROVAL_DENIED`),
 *      and pass an explicit `revisionNumber` matching the `IN_REVIEW` revision.
 *   3. PUBLISHED SNAPSHOT ISOLATION: Editing content or `audienceId` after
 *      approval/publication creates `Revision (N+1)` in `IN_REVIEW` without
 *      altering `publishedVersion` or `publishedAudienceId` of any live snapshot!
 *   4. SYNCHRONOUS PUBLICATION REWARD (+20) & WITHDRAWAL REVERSAL:
 *      Publishing an approved story calls `recognitionService.awardOnPublication`
 *      inside the same transaction (+20 points once/week); withdrawing calls
 *      `recognitionService.reverseAwardsOnWithdrawal`.
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
    private final RecognitionService recognitionService;
    private final PulseClock pulseClock;

    public PublishingWorkflowService(ContentItemRepository contentItemRepository,
                                     ContentRevisionRepository contentRevisionRepository,
                                     InvitationRepository invitationRepository,
                                     UserRepository userRepository,
                                     AudienceRepository audienceRepository,
                                     AuditEventRepository auditEventRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     IdentityService identityService,
                                     FeedService feedService,
                                     RecognitionService recognitionService,
                                     PulseClock pulseClock) {
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
        this.auditEventRepository = auditEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.identityService = identityService;
        this.feedService = feedService;
        this.recognitionService = recognitionService;
        this.pulseClock = pulseClock;
    }

    @Transactional
    public Map<String, Object> createOrSaveContribution(UserEntity caller, Map<String, Object> payload) {
        identityService.requireRole(caller, "CREATOR");

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
        identityService.requireAudienceAccess(caller, audienceId);

        String contentId = "story_" + UUID.randomUUID().toString().substring(0, 8);
        ContentItemEntity item = new ContentItemEntity();
        item.setId(contentId);
        item.setAuthorId(caller.getId());
        item.setAudienceId(audienceId);
        item.setPublishedAudienceId(null);
        item.setContentType(contentType);
        item.setCurrentVersion(1);
        item.setStatus(submitImmediately ? "IN_REVIEW" : "DRAFT");
        item.setCreatedAt(pulseClock.now());
        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);

        ContentRevisionEntity rev = new ContentRevisionEntity();
        rev.setId("rev_" + UUID.randomUUID().toString().substring(0, 8));
        rev.setContentId(contentId);
        rev.setVersionNumber(1);
        rev.setAudienceId(audienceId);
        rev.setTitle(title);
        rev.setSummary(summary);
        rev.setBodyText(bodyText);
        rev.setTagsCsv(tagsCsv);
        rev.setCreatedAt(pulseClock.now());
        contentRevisionRepository.save(rev);

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
            inv.setExpiresAt(pulseClock.now().plus(expiresDays, ChronoUnit.DAYS));
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
     * D2 INVARIANT: Editing content or audience invalidates approval on the working
     * revision and returns the new revision to review, while preserving any prior
     * published revision (`publishedVersion` and `publishedAudienceId`) unchanged!
     */
    @Transactional
    public Map<String, Object> editContentAndCreateNewRevision(UserEntity caller, String contentId, Map<String, Object> payload) {
        ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        boolean isAuthor = item.getAuthorId().equals(caller.getId());
        boolean isScopedEditor = caller.hasRole("REVIEWER") || caller.hasRole("PUBLISHER");
        if (!isAuthor && !isScopedEditor) {
            throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the author or scoped editorial staff can edit this item.");
        }
        identityService.requireAudienceAccess(caller, item.getAudienceId());

        ContentRevisionEntity latestRev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Latest revision not found"));

        String newTitle = (String) payload.getOrDefault("title", latestRev.getTitle());
        String newSummary = (String) payload.getOrDefault("summary", latestRev.getSummary());
        String newBody = (String) payload.getOrDefault("bodyText", latestRev.getBodyText());
        String newAudienceId = (String) payload.getOrDefault("audienceId", item.getAudienceId());
        String newTags = (String) payload.getOrDefault("tagsCsv", latestRev.getTagsCsv());

        audienceRepository.findById(newAudienceId)
                .orElseThrow(() -> PulseException.unprocessable("422: Unknown audienceId: " + newAudienceId));
        identityService.requireAudienceAccess(caller, newAudienceId);

        int nextVersion = item.getCurrentVersion() + 1;
        ContentRevisionEntity newRev = new ContentRevisionEntity();
        newRev.setId("rev_" + UUID.randomUUID().toString().substring(0, 8));
        newRev.setContentId(contentId);
        newRev.setVersionNumber(nextVersion);
        newRev.setAudienceId(newAudienceId);
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
        newRev.setApprovedBy(null);
        newRev.setApprovedAt(null);
        newRev.setCreatedAt(pulseClock.now());
        contentRevisionRepository.save(newRev);

        String previousStatus = item.getStatus();
        item.setAudienceId(newAudienceId);
        item.setCurrentVersion(nextVersion);

        // If the item has a live published snapshot, keep item.status = "PUBLISHED" and
        // keep item.publishedAudienceId untouched so the live snapshot stays restricted
        // to its original approved audience while the new revision is IN_REVIEW!
        if (item.getPublishedVersion() != null && "PUBLISHED".equals(previousStatus)) {
            if (item.getPublishedAudienceId() == null) {
                item.setPublishedAudienceId(latestRev.getAudienceId() != null ? latestRev.getAudienceId() : item.getAudienceId());
            }
        } else if ("APPROVED".equals(previousStatus) || "SCHEDULED".equals(previousStatus)) {
            item.setStatus("IN_REVIEW");
            item.setScheduledFor(null);
        }

        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);

        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(),
                "REVISION_CREATED_APPROVAL_INVALIDATED",
                "CONTENT_REVISION",
                newRev.getId(),
                "Edited content/audience on " + contentId + " -> created Revision " + nextVersion + " and invalidated prior approval"
        ));

        return toPortalQueueItemDto(item, newRev);
    }

    /**
     * Submits a DRAFT item to IN_REVIEW.
     * D2 Invariant: Requires creator role AND author ownership, and valid source state (`DRAFT`).
     */
    @Transactional
    public Map<String, Object> submitForReview(UserEntity caller, String contentId) {
        identityService.requireRole(caller, "CREATOR");

        ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        if (!item.getAuthorId().equals(caller.getId())) {
            throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Only the author of this draft can submit it for review.");
        }

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Revision not found"));

        String effectiveStatus = computeEffectivePortalStatus(item, rev);
        if (!"DRAFT".equals(effectiveStatus)) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Revision " + item.getCurrentVersion() + " cannot be submitted because it is currently " + effectiveStatus
            );
        }

        rev.setReviewerNotes(null);
        contentRevisionRepository.save(rev);

        if (item.getPublishedVersion() == null) {
            item.setStatus("IN_REVIEW");
        }
        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);

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
     * Reviewer approves or requests changes on an explicit `revisionNumber`.
     * D2 Invariants:
     * 1. Caller must have role `REVIEWER` (`ADMIN` alone is denied!).
     * 2. Caller must have audience permission for `item.getAudienceId()`.
     * 3. No Self-Approval (`caller.id != item.authorId`).
     * 4. `revisionNumber` must be explicitly provided and match `item.currentVersion`.
     * 5. Revision must currently be in `IN_REVIEW` state (`409 STALE_VERSION` otherwise).
     */
    @Transactional
    public Map<String, Object> reviewRevision(UserEntity caller, String contentId, Map<String, Object> payload) {
        identityService.requireRole(caller, "REVIEWER");

        ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        identityService.requireAudienceAccess(caller, item.getAudienceId());

        if (item.getAuthorId().equals(caller.getId())) {
            throw PulseException.forbidden(
                    "SELF_APPROVAL_DENIED",
                    "403: Reviewers cannot self-approve their own submissions (Author == Reviewer)."
            );
        }

        if (!(payload.get("revisionNumber") instanceof Number n)) {
            throw PulseException.unprocessable("422: Explicit revisionNumber is required when reviewing content.");
        }
        int expectedRevision = n.intValue();
        if (expectedRevision != item.getCurrentVersion()) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Revision " + expectedRevision + " is stale; current version is " + item.getCurrentVersion()
            );
        }

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, expectedRevision)
                .orElseThrow(() -> PulseException.notFound("404: Revision not found"));

        String effectiveStatus = computeEffectivePortalStatus(item, rev);
        if (!"IN_REVIEW".equals(effectiveStatus)) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Revision " + expectedRevision + " is not awaiting review (current state: " + effectiveStatus + ")."
            );
        }

        String decision = getRequiredString(payload, "decision").toUpperCase();
        String notes = (String) payload.getOrDefault("notes", "");

        rev.setReviewerNotes(notes);
        if ("APPROVE".equals(decision)) {
            rev.setApprovedBy(caller.getId());
            rev.setApprovedAt(pulseClock.now());
            if (item.getPublishedVersion() == null) {
                item.setStatus("APPROVED");
            }
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "REVISION_APPROVED", "CONTENT_REVISION", rev.getId(),
                    "Reviewer " + caller.getDisplayName() + " approved Revision " + expectedRevision + " of " + contentId
            ));
        } else if ("REQUEST_CHANGES".equals(decision)) {
            if (notes == null || notes.isBlank()) {
                notes = "Changes requested by reviewer.";
                rev.setReviewerNotes(notes);
            }
            rev.setApprovedBy(null);
            rev.setApprovedAt(null);
            if (item.getPublishedVersion() == null) {
                item.setStatus("DRAFT");
            }
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "CHANGES_REQUESTED", "CONTENT_REVISION", rev.getId(),
                    "Reviewer " + caller.getDisplayName() + " requested changes on Revision " + expectedRevision + ": " + notes
            ));
        } else {
            throw PulseException.unprocessable("422: decision must be APPROVE or REQUEST_CHANGES");
        }

        item.setUpdatedAt(pulseClock.now());
        contentRevisionRepository.save(rev);
        contentItemRepository.save(item);
        return toPortalQueueItemDto(item, rev);
    }

    /**
     * Publisher releases an APPROVED revision immediately or schedules it.
     * D4 Invariant: Awards +20 recognition points ONLY upon actual publication,
     * inside the same transaction, at most once per user/week!
     */
    @Transactional
    public Map<String, Object> publishOrSchedule(UserEntity caller, String contentId, Map<String, Object> payload) {
        identityService.requireRole(caller, "PUBLISHER");

        ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        identityService.requireAudienceAccess(caller, item.getAudienceId());

        if (payload != null && payload.get("revisionNumber") instanceof Number n) {
            int expectedRevision = n.intValue();
            if (expectedRevision != item.getCurrentVersion()) {
                throw PulseException.conflict(
                        "STALE_VERSION",
                        "409: Revision " + expectedRevision + " is stale; current version is " + item.getCurrentVersion()
                );
            }
        }

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(contentId, item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Revision not found"));

        // Idempotent replay check: if this exact revision is ALREADY published, ensure award is synced and return 200
        if ("PUBLISHED".equals(item.getStatus())
                && item.getPublishedVersion() != null
                && item.getPublishedVersion() == rev.getVersionNumber()
                && rev.getApprovedBy() != null) {
            recognitionService.awardOnPublication(item, rev);
            return toPortalQueueItemDto(item, rev);
        }

        String effectiveStatus = computeEffectivePortalStatus(item, rev);
        if (!"APPROVED".equals(effectiveStatus) || rev.getApprovedBy() == null) {
            throw PulseException.conflict(
                    "STALE_VERSION",
                    "409: Cannot publish Revision " + item.getCurrentVersion() + " because it is not in APPROVED state (current status: " + effectiveStatus + ")."
            );
        }

        String mode = payload != null ? (String) payload.getOrDefault("mode", "IMMEDIATE") : "IMMEDIATE";
        if ("SCHEDULED".equalsIgnoreCase(mode)) {
            String scheduledForStr = getRequiredString(payload, "scheduledFor");
            Instant scheduledFor = Instant.parse(scheduledForStr);
            if (item.getPublishedVersion() == null) {
                item.setStatus("SCHEDULED");
            }
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
            String targetAudience = rev.getAudienceId() != null ? rev.getAudienceId() : item.getAudienceId();
            item.setStatus("PUBLISHED");
            item.setPublishedVersion(rev.getVersionNumber());
            item.setPublishedAudienceId(targetAudience);
            item.setAudienceId(targetAudience);
            item.setPublishedAt(pulseClock.now());
            item.setScheduledFor(null);

            outboxEventRepository.save(OutboxEventEntity.create(
                    "CONTENT_ITEM", item.getId(), "CONTENT_PUBLISHED",
                    "{\"contentId\":\"" + item.getId() + "\",\"versionNumber\":" + rev.getVersionNumber() + "}"
            ));
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(), "CONTENT_PUBLISHED", "CONTENT_ITEM", item.getId(),
                    "Publisher " + caller.getDisplayName() + " published Revision " + rev.getVersionNumber() + " to audience " + targetAudience
            ));

            contentItemRepository.save(item);
            // Award +20 recognition points synchronously inside the same transaction!
            recognitionService.awardOnPublication(item, rev);
        }

        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);
        return toPortalQueueItemDto(item, rev);
    }

    /**
     * Publisher or Moderator withdraws published content and reverses associated rewards.
     */
    @Transactional
    public Map<String, Object> withdrawContent(UserEntity caller, String contentId, String reason) {
        if (!caller.hasRole("PUBLISHER") && !caller.hasRole("MODERATOR")) {
            throw PulseException.forbidden("FORBIDDEN_ACCESS", "403: Requires PUBLISHER or MODERATOR role to withdraw content.");
        }

        ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        identityService.requireAudienceAccess(caller, item.getEffectivePublishedAudienceId());

        item.setStatus("WITHDRAWN");
        item.setPublishedVersion(null);
        item.setPublishedAudienceId(null);
        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);

        // Reverse contribution and appreciation points inside the same transaction!
        recognitionService.reverseAwardsOnWithdrawal(item, caller.getId());

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
     * D2 Invariant: Portal queue requires portal role (`REVIEWER`, `PUBLISHER`, `MODERATOR`, `ADMIN`)
     * and filters items to the caller's permitted audiences.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPortalQueue(UserEntity caller) {
        identityService.requirePortalAccess(caller);
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());

        List<ContentItemEntity> allItems = contentItemRepository.findAllByOrderByUpdatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ContentItemEntity item : allItems) {
            if (!permittedAudiences.contains(item.getAudienceId())) {
                continue;
            }
            contentRevisionRepository.findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion())
                    .ifPresent(rev -> result.add(toPortalQueueItemDto(item, rev)));
        }
        return result;
    }

    /**
     * D2 Invariant: Audit log requires portal role and scopes content events to permitted audiences.
     */
    @Transactional(readOnly = true)
    public List<AuditEventEntity> listScopedAuditLog(UserEntity caller) {
        identityService.requirePortalAccess(caller);
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());

        Map<String, ContentItemEntity> contentById = new HashMap<>();
        contentItemRepository.findAll().forEach(c -> contentById.put(c.getId(), c));

        Map<String, String> contentIdByRevId = new HashMap<>();
        contentRevisionRepository.findAll().forEach(r -> contentIdByRevId.put(r.getId(), r.getContentId()));

        return auditEventRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(ev -> {
                    if ("CONTENT_ITEM".equals(ev.getTargetType())) {
                        ContentItemEntity item = contentById.get(ev.getTargetId());
                        return item == null || permittedAudiences.contains(item.getAudienceId());
                    }
                    if ("CONTENT_REVISION".equals(ev.getTargetType())) {
                        String cid = contentIdByRevId.get(ev.getTargetId());
                        ContentItemEntity item = cid != null ? contentById.get(cid) : null;
                        return item == null || permittedAudiences.contains(item.getAudienceId());
                    }
                    return true;
                })
                .toList();
    }

    private String computeEffectivePortalStatus(ContentItemEntity item, ContentRevisionEntity rev) {
        if ("WITHDRAWN".equals(item.getStatus())) {
            return "WITHDRAWN";
        }
        if (item.getPublishedVersion() != null && item.getCurrentVersion() > item.getPublishedVersion()) {
            if (rev.getApprovedBy() != null) {
                return "APPROVED";
            }
            if (rev.getReviewerNotes() != null && !rev.getReviewerNotes().isBlank()) {
                return "DRAFT";
            }
            return "IN_REVIEW";
        }
        return item.getStatus();
    }

    public Map<String, Object> toPortalQueueItemDto(ContentItemEntity item, ContentRevisionEntity rev) {
        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        AudienceEntity audience = audienceRepository.findById(item.getAudienceId()).orElse(null);
        UserEntity approver = rev.getApprovedBy() != null ? userRepository.findById(rev.getApprovedBy()).orElse(null) : null;

        String effectivePortalStatus = computeEffectivePortalStatus(item, rev);

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
        map.put("publishedAudienceId", item.getPublishedAudienceId());
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
        Object val = payload != null ? payload.get(key) : null;
        if (val == null || val.toString().isBlank()) {
            throw PulseException.unprocessable("422: Missing required field: " + key);
        }
        return val.toString().trim();
    }
}
