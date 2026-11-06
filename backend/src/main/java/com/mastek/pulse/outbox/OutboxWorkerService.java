package com.mastek.pulse.outbox;

import com.mastek.pulse.common.AuditEventEntity;
import com.mastek.pulse.common.AuditEventRepository;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import com.mastek.pulse.content.ContentRevisionRepository;
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
 * Journey: Background Worker / Durable Outbox (Proposal Pages 11, 14, 15).
 * Why it is designed this way:
 *   Page 11 & 14 state:
 *   - "Scheduled publish: recheck approval, audience + media; emit once."
 *   - "Scheduling rechecks approval and media readiness; failures enter a visible retry queue."
 *   - "Worker delivers a generic notification. Retry safely; the app reads current state from the API."
 *   Every 10 seconds, this background worker checks for due `SCHEDULED` content
 *   and processes `PENDING` outbox events with retry backoff.
 * ============================================================================
 */
@Service
public class OutboxWorkerService {

    private final OutboxEventRepository outboxEventRepository;
    private final ContentItemRepository contentItemRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final AuditEventRepository auditEventRepository;

    public OutboxWorkerService(OutboxEventRepository outboxEventRepository,
                               ContentItemRepository contentItemRepository,
                               ContentRevisionRepository contentRevisionRepository,
                               AuditEventRepository auditEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void processOutboxAndScheduledPublications() {
        Instant now = Instant.now();

        // 1. Check scheduled content items whose release time has arrived
        List<ContentItemEntity> dueScheduledItems = contentItemRepository
                .findByStatusAndScheduledForLessThanEqual("SCHEDULED", now);
        for (ContentItemEntity item : dueScheduledItems) {
            Optional<ContentRevisionEntity> revOpt = contentRevisionRepository
                    .findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion());
            if (revOpt.isPresent() && revOpt.get().getApprovedBy() != null) {
                ContentRevisionEntity rev = revOpt.get();
                boolean mediaReady = !rev.isHasAudio() || (rev.getMediaAssetKey() != null && rev.getTranscriptText() != null);
                if (mediaReady) {
                    item.setStatus("PUBLISHED");
                    item.setPublishedVersion(rev.getVersionNumber());
                    item.setPublishedAt(now);
                    item.setUpdatedAt(now);
                    contentItemRepository.save(item);

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

        // 2. Process pending transactional outbox events
        List<OutboxEventEntity> pendingEvents = outboxEventRepository
                .findByStatusAndNextRunLessThanEqualOrderByCreatedAtAsc("PENDING", now);
        for (OutboxEventEntity event : pendingEvents) {
            try {
                event.setAttempts(event.getAttempts() + 1);
                event.setStatus("PROCESSED");
                outboxEventRepository.save(event);
            } catch (Exception ex) {
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
