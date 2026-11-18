package com.mastek.pulse.outbox;

import com.mastek.pulse.common.AuditEventEntity;
import com.mastek.pulse.common.AuditEventRepository;
import com.mastek.pulse.common.PulseClock;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import com.mastek.pulse.content.ContentRevisionRepository;
import com.mastek.pulse.recognition.RecognitionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: OutboxWorkerService.java
 * Journey: Background Worker / Durable Outbox (Proposal Pages 11, 14, 15 + CF-004).
 *
 * Why it is designed this way:
 *   1. Scheduled Publication Release (Proposal Page 11 & 15):
 *      Rechecks approval (`approvedBy != null`), target audience, and media/transcript
 *      readiness for `SCHEDULED` publications whose `scheduledFor <= now`, sets
 *      `publishedAudienceId`, and triggers `recognitionService.awardOnPublication`
 *      in the same database transaction!
 *   2. Transactional Outbox Polling & Retry Backoff:
 *      Polls `PENDING` rows in `outbox_events` where `nextRun <= now`, marks them
 *      `PROCESSED`, or increments `attempts` with linear backoff (`30s * attempts`,
 *      marking `FAILED` after 5 attempts).
 * ============================================================================
 */
// @Service registers this background worker as a Spring-managed singleton bean.
@Service
public class OutboxWorkerService {

    // Repository for polling and updating `outbox_events` rows.
    private final OutboxEventRepository outboxEventRepository;

    // Repository for finding `SCHEDULED` content items that are due for publication.
    private final ContentItemRepository contentItemRepository;

    // Repository for loading the working revision of a scheduled content item to re-verify approval and media readiness.
    private final ContentRevisionRepository contentRevisionRepository;

    // Repository for writing an audit log entry when a scheduled item is automatically published.
    private final AuditEventRepository auditEventRepository;

    // Service for awarding +20 Campfire recognition points synchronously when a scheduled story goes live.
    private final RecognitionService recognitionService;

    // Deterministic clock abstraction (`PulseClock`) used to check `scheduledFor <= now` and `nextRun <= now`.
    private final PulseClock pulseClock;

    /**
     * Constructor injection of all repositories and services required by the background worker.
     */
    public OutboxWorkerService(OutboxEventRepository outboxEventRepository,
                               ContentItemRepository contentItemRepository,
                               ContentRevisionRepository contentRevisionRepository,
                               AuditEventRepository auditEventRepository,
                               RecognitionService recognitionService,
                               PulseClock pulseClock) {
        this.outboxEventRepository = outboxEventRepository;
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.auditEventRepository = auditEventRepository;
        this.recognitionService = recognitionService;
        this.pulseClock = pulseClock;
    }

    /**
     * Periodic background worker tick that:
     * 1. Releases any due `SCHEDULED` content items after re-verifying revision approval and media readiness.
     * 2. Processes any due `PENDING` transactional outbox events with retry/backoff handling.
     */
    // @Scheduled(fixedDelay = 10000) runs this method every 10,000 ms (10 seconds) after the previous execution completes.
    @Scheduled(fixedDelay = 10000)
    // @Transactional ensures scheduled publication updates, recognition awards, and outbox status transitions commit atomically.
    @Transactional
    public void processOutboxAndScheduledPublications() {
        // Read the current UTC instant from PulseClock.
        Instant now = pulseClock.now();

        // PART 1: Find all content items in `SCHEDULED` state whose `scheduledFor` timestamp is `<= now`.
        List<ContentItemEntity> dueScheduledItems = contentItemRepository
                .findByStatusAndScheduledForLessThanEqual("SCHEDULED", now);
        for (ContentItemEntity item : dueScheduledItems) {
            // Load the current revision of the scheduled content item.
            Optional<ContentRevisionEntity> revOpt = contentRevisionRepository
                    .findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion());
            // Re-verify that the revision exists and still holds a valid reviewer approval (`approvedBy != null`).
            if (revOpt.isPresent() && revOpt.get().getApprovedBy() != null) {
                ContentRevisionEntity rev = revOpt.get();
                // Re-verify media and transcript readiness if the revision is an audio item.
                boolean mediaReady = !rev.isHasAudio() || (rev.getMediaAssetKey() != null && rev.getTranscriptText() != null);
                if (mediaReady) {
                    // Lock in the published snapshot (`PUBLISHED`, `publishedVersion`, and `publishedAudienceId`).
                    String targetAud = rev.getAudienceId() != null ? rev.getAudienceId() : item.getAudienceId();
                    item.setStatus("PUBLISHED");
                    item.setPublishedVersion(rev.getVersionNumber());
                    item.setPublishedAudienceId(targetAud);
                    item.setAudienceId(targetAud);
                    item.setPublishedAt(now);
                    item.setUpdatedAt(now);
                    contentItemRepository.save(item);

                    // Award +20 Campfire recognition points synchronously now that the scheduled story is actually published!
                    recognitionService.awardOnPublication(item, rev);

                    // Record an audit event showing the background worker released the scheduled publication.
                    auditEventRepository.save(AuditEventEntity.create(
                            "SYSTEM_OUTBOX_WORKER",
                            "SCHEDULED_PUBLISH_RELEASED",
                            "CONTENT_ITEM",
                            item.getId(),
                            "Background worker rechecked approval & media readiness and published Revision " + rev.getVersionNumber()
                    ));
                }
            }
        }

        // PART 2: Poll all `PENDING` outbox events whose `nextRun <= now` in FIFO order (`createdAt ASC`).
        List<OutboxEventEntity> pendingEvents = outboxEventRepository
                .findByStatusAndNextRunLessThanEqualOrderByCreatedAtAsc("PENDING", now);
        for (OutboxEventEntity event : pendingEvents) {
            try {
                // Increment delivery attempt counter and mark the event as `PROCESSED`.
                event.setAttempts(event.getAttempts() + 1);
                event.setStatus("PROCESSED");
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                // If delivery fails, increment attempts, schedule a backoff retry (`now + 30s * attempts`),
                // and mark as `FAILED` if 5 attempts have been exhausted.
                event.setAttempts(event.getAttempts() + 1);
                event.setNextRun(now.plusSeconds(30L * event.getAttempts()));
                if (event.getAttempts() >= 5) {
                    event.setStatus("FAILED");
                }
                outboxEventRepository.save(event);
            }
        }
    }
}
