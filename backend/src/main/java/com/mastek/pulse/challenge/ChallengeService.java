package com.mastek.pulse.challenge;

import com.mastek.pulse.common.*;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import com.mastek.pulse.content.ContentRevisionRepository;
import com.mastek.pulse.recognition.RecognitionEventEntity;
import com.mastek.pulse.recognition.RecognitionEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ChallengeService.java
 * Journey: Campfire Weekly Challenge Discovery & Submission ("Beyond My Job Title"
 *          - Proposal Page 16 & Story CF-002 / D3 / D4).
 * Why it is designed this way:
 *   1. Enforces audience permissions (`requireAudienceAccess`) and active time
 *      window (`startsAt <= now < endsAt` via injected `PulseClock`) server-side.
 *   2. Validates `title` (5–100 chars) and `bodyText` (30–1,200 chars) (`422`).
 *   3. Enforces caller-scoped `Idempotency-Key` with payload hash verification:
 *      replaying the same key with the same body returns the existing entry,
 *      while replaying the same key with a changed body throws `409 IDEMPOTENCY_CONFLICT`.
 *   4. Ties each challenge entry (`UNIQUE(challenge_id, user_id)`) directly to
 *      `content_items` + `content_revisions`. Submitting earns 0 points; if a
 *      reviewer requests changes (`DRAFT`), resubmitting increments `currentVersion`
 *      and clears prior approval.
 * ============================================================================
 */
@Service
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeEntryRepository entryRepository;
    private final ContentItemRepository contentItemRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final RecognitionEventRepository recognitionEventRepository;
    private final AudienceRepository audienceRepository;
    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final IdentityService identityService;
    private final PulseClock pulseClock;

    public ChallengeService(ChallengeRepository challengeRepository,
                            ChallengeEntryRepository entryRepository,
                            ContentItemRepository contentItemRepository,
                            ContentRevisionRepository contentRevisionRepository,
                            RecognitionEventRepository recognitionEventRepository,
                            AudienceRepository audienceRepository,
                            UserRepository userRepository,
                            AuditEventRepository auditEventRepository,
                            IdentityService identityService,
                            PulseClock pulseClock) {
        this.challengeRepository = challengeRepository;
        this.entryRepository = entryRepository;
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.recognitionEventRepository = recognitionEventRepository;
        this.audienceRepository = audienceRepository;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.identityService = identityService;
        this.pulseClock = pulseClock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentChallenge(UserEntity caller) {
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        if (permittedAudiences.isEmpty()) {
            return Map.of();
        }

        List<ChallengeEntity> challenges = challengeRepository.findByAudienceIdInOrderByStartsAtDesc(permittedAudiences);
        if (challenges.isEmpty()) {
            return Map.of();
        }

        ChallengeEntity challenge = challenges.get(0);
        AudienceEntity aud = audienceRepository.findById(challenge.getAudienceId()).orElse(null);
        Optional<ChallengeEntryEntity> myEntryOpt = entryRepository.findByChallengeIdAndUserId(challenge.getId(), caller.getId());

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", challenge.getId());
        dto.put("title", challenge.getTitle());
        dto.put("prompt", challenge.getPrompt());
        dto.put("startsAt", challenge.getStartsAt().toString());
        dto.put("endsAt", challenge.getEndsAt().toString());
        dto.put("timezone", challenge.getTimezone());
        dto.put("weekStart", pulseClock.currentWeekStart());
        dto.put("audienceId", challenge.getAudienceId());
        dto.put("audienceName", aud != null ? aud.getName() : challenge.getAudienceId());
        dto.put("status", challenge.isOpenAt(pulseClock.now()) ? "ACTIVE" : "CLOSED");
        dto.put("myEntry", myEntryOpt.map(this::toEntryDto).orElse(null));
        return dto;
    }

    @Transactional
    public Map<String, Object> submitEntry(UserEntity caller,
                                           String challengeId,
                                           String idempotencyKey,
                                           Map<String, Object> payload) {
        identityService.requireRole(caller, "CREATOR");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PulseException.unprocessable("422: Idempotency-Key header is required.");
        }
        String trimmedKey = idempotencyKey.trim();

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> PulseException.notFound("404: Challenge not found: " + challengeId));

        // 1. Check audience access first (403 if excluded)
        identityService.requireAudienceAccess(caller, challenge.getAudienceId());

        // 2. Check active challenge window (409 if closed/expired)
        if (!challenge.isOpenAt(pulseClock.now())) {
            throw PulseException.conflict(
                    "CHALLENGE_CLOSED",
                    "409: Challenge '" + challenge.getTitle() + "' is not open for submissions at " + pulseClock.now()
            );
        }

        String rawTitle = payload.get("title") != null ? payload.get("title").toString().trim() : "";
        String rawBody = payload.get("bodyText") != null ? payload.get("bodyText").toString().trim() : "";
        boolean submitForReview = payload.get("submitForReview") == null || Boolean.TRUE.equals(payload.get("submitForReview"));

        if (rawTitle.length() < 5 || rawTitle.length() > 100) {
            throw PulseException.unprocessable("422: Challenge story title must be between 5 and 100 characters.");
        }
        if (rawBody.length() < 30 || rawBody.length() > 1200) {
            throw PulseException.unprocessable("422: Challenge story bodyText must be between 30 and 1,200 characters.");
        }

        String payloadHash = sha256(challengeId + "|" + rawTitle + "|" + rawBody + "|" + submitForReview);

        // 3. Lock user row to serialize concurrent submissions by the same user
        userRepository.findByIdWithPessimisticLock(caller.getId());

        // 4. Check caller-scoped Idempotency-Key
        Optional<ChallengeEntryEntity> existingByIdem = entryRepository
                .findByUserIdAndIdempotencyKey(caller.getId(), trimmedKey);
        if (existingByIdem.isPresent()) {
            ChallengeEntryEntity idemEntry = existingByIdem.get();
            if (!idemEntry.getPayloadHash().equals(payloadHash)) {
                throw PulseException.conflict(
                        "IDEMPOTENCY_CONFLICT",
                        "409: Idempotency-Key was already used with a different challenge submission payload."
                );
            }
            return toEntryDto(idemEntry);
        }

        String summary = rawBody.length() > 140 ? rawBody.substring(0, 140) + "..." : rawBody;
        Optional<ChallengeEntryEntity> existingForChallenge = entryRepository
                .findByChallengeIdAndUserId(challengeId, caller.getId());

        if (existingForChallenge.isPresent()) {
            ChallengeEntryEntity entry = existingForChallenge.get();
            ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(entry.getContentId())
                    .orElseThrow(() -> PulseException.notFound("404: Linked content item not found."));

            // Resubmission is allowed ONLY when the item is in DRAFT (e.g. saved draft or returned via REQUEST_CHANGES)
            if (!"DRAFT".equals(item.getStatus())) {
                throw PulseException.conflict(
                        "DUPLICATE_ENTRY",
                        "409: One challenge story per person each week (current status: " + item.getStatus() + ")."
                );
            }

            int nextVer = item.getCurrentVersion() + 1;
            ContentRevisionEntity newRev = new ContentRevisionEntity();
            newRev.setId("rev_" + UUID.randomUUID().toString().substring(0, 8));
            newRev.setContentId(item.getId());
            newRev.setVersionNumber(nextVer);
            newRev.setAudienceId(challenge.getAudienceId());
            newRev.setTitle(rawTitle);
            newRev.setSummary(summary);
            newRev.setBodyText(rawBody);
            newRev.setTagsCsv("campfire challenge,culture,team learning");
            newRev.setApprovedBy(null);
            newRev.setApprovedAt(null);
            newRev.setCreatedAt(pulseClock.now());
            contentRevisionRepository.save(newRev);

            item.setCurrentVersion(nextVer);
            item.setStatus(submitForReview ? "IN_REVIEW" : "DRAFT");
            item.setUpdatedAt(pulseClock.now());
            contentItemRepository.save(item);

            entry.setIdempotencyKey(trimmedKey);
            entry.setPayloadHash(payloadHash);
            entryRepository.save(entry);

            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(),
                    "CHALLENGE_ENTRY_RESUBMITTED",
                    "CONTENT_REVISION",
                    newRev.getId(),
                    caller.getDisplayName() + " resubmitted Revision " + nextVer + " for challenge " + challengeId
            ));

            return toEntryDto(entry);
        }

        // First-time submission for this challenge
        String contentId = "story_ch_" + UUID.randomUUID().toString().substring(0, 8);
        ContentItemEntity item = new ContentItemEntity();
        item.setId(contentId);
        item.setAuthorId(caller.getId());
        item.setAudienceId(challenge.getAudienceId());
        item.setPublishedAudienceId(null);
        item.setContentType("STORY");
        item.setCurrentVersion(1);
        item.setPublishedVersion(null);
        item.setStatus(submitForReview ? "IN_REVIEW" : "DRAFT");
        item.setCreatedAt(pulseClock.now());
        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);

        ContentRevisionEntity rev = new ContentRevisionEntity();
        rev.setId("rev_" + UUID.randomUUID().toString().substring(0, 8));
        rev.setContentId(contentId);
        rev.setVersionNumber(1);
        rev.setAudienceId(challenge.getAudienceId());
        rev.setTitle(rawTitle);
        rev.setSummary(summary);
        rev.setBodyText(rawBody);
        rev.setTagsCsv("campfire challenge,culture,team learning");
        rev.setCreatedAt(pulseClock.now());
        contentRevisionRepository.save(rev);

        ChallengeEntryEntity entry = new ChallengeEntryEntity();
        entry.setId("che_" + UUID.randomUUID().toString().substring(0, 8));
        entry.setChallengeId(challengeId);
        entry.setUserId(caller.getId());
        entry.setContentId(contentId);
        entry.setIdempotencyKey(trimmedKey);
        entry.setPayloadHash(payloadHash);
        entry.setCreatedAt(pulseClock.now());
        entryRepository.save(entry);

        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(),
                submitForReview ? "CHALLENGE_ENTRY_SUBMITTED" : "CHALLENGE_DRAFT_SAVED",
                "CONTENT_ITEM",
                contentId,
                caller.getDisplayName() + " created Revision 1 (" + item.getStatus() + ") for challenge " + challengeId
        ));

        return toEntryDto(entry);
    }

    public Map<String, Object> toEntryDto(ChallengeEntryEntity entry) {
        ContentItemEntity item = contentItemRepository.findById(entry.getContentId()).orElseThrow();
        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion())
                .orElseThrow();

        List<RecognitionEventEntity> contentEvents = recognitionEventRepository
                .findByContentIdOrderByCreatedAtAscIdAsc(item.getId());
        Set<String> reversedIds = contentEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int pointsAwarded = contentEvents.stream()
                .filter(e -> "PUBLISHED_CONTRIBUTION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("entryId", entry.getId());
        dto.put("challengeId", entry.getChallengeId());
        dto.put("contentId", item.getId());
        dto.put("revisionNumber", item.getCurrentVersion());
        dto.put("status", item.getStatus());
        dto.put("title", rev.getTitle());
        dto.put("summary", rev.getSummary());
        dto.put("bodyText", rev.getBodyText());
        dto.put("audienceId", item.getAudienceId());
        dto.put("reviewerNotes", rev.getReviewerNotes());
        dto.put("pointsAwarded", Math.max(0, pointsAwarded));
        return dto;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
