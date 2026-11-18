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
 * Module: `challenge/` (Weekly Campfire Connection Challenges)
 *
 * 1. WHAT THIS FILE DOES:
 *    Implements the business logic for discovering the active weekly Campfire
 *    challenge (`getCurrentChallenge`) and submitting or resubmitting an
 *    employee's story response (`submitEntry`).
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Page 16 ("Beyond My Job Title" Weekly Connection Challenge).
 *    - Implementation Story CF-002 & Slices D3 / D4.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Audience-First Security (`requireAudienceAccess`):
 *      Checks that the caller belongs to the challenge's `audience_id` before
 *      accepting any write or returning challenge data.
 *    - Deterministic Time Window Check (`challenge.isOpenAt(pulseClock.now())`):
 *      Rejects submissions outside `[startsAt, endsAt)` with `409 CHALLENGE_CLOSED`.
 *    - Input Length Guardrails (`422 Unprocessable Entity`):
 *      Enforces `title` between 5 and 100 chars and `bodyText` between 30 and
 *      1,200 chars so blank or oversized payloads never reach the review queue.
 *    - Pessimistic User Row Lock + Caller-Scoped Idempotency-Key:
 *      Locks the submitting user's row (`SELECT ... FOR UPDATE`) to serialize
 *      concurrent requests from the same user, then checks `Idempotency-Key`
 *      against `payloadHash` (SHA-256). Same key + same payload replays the
 *      existing entry; same key + modified payload throws `409 IDEMPOTENCY_CONFLICT`.
 *    - Unified Content & Revision Workflow:
 *      Creates a `ContentItemEntity` + `ContentRevisionEntity` (Revision 1) linked
 *      to `ChallengeEntryEntity`. Submitting earns 0 points (`pointsAwarded = 0`).
 *      If a reviewer requests changes (returning the story to `DRAFT`), the author
 *      can resubmit through this same endpoint, which creates Revision `N+1` and
 *      transitions the story back to `IN_REVIEW`.
 * ============================================================================
 */
@Service // Registers this class as a Spring-managed domain service singleton.
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

    /**
     * Constructor injecting all repositories and shared infrastructure services.
     */
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

    /**
     * Looks up the latest weekly challenge for the caller's permitted audiences
     * and attaches the caller's personal submission state (`myEntry`) if present.
     *
     * @param caller authenticated employee making the request
     * @return map representing `ChallengeDto` (or empty map if caller has no permitted challenges)
     */
    @Transactional(readOnly = true) // Uses a read-only transaction (optimizes Hibernate dirty-checking and JDBC read mode).
    public Map<String, Object> getCurrentChallenge(UserEntity caller) {
        // Step 1: Fetch the list of audience IDs the caller belongs to (Guardrail #1: Filter by audience first).
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        if (permittedAudiences.isEmpty()) {
            // If the user belongs to zero audiences, return an empty JSON object without querying challenges.
            return Map.of();
        }

        // Step 2: Query challenges restricted to the caller's permitted audiences, newest first.
        List<ChallengeEntity> challenges = challengeRepository.findByAudienceIdInOrderByStartsAtDesc(permittedAudiences);
        if (challenges.isEmpty()) {
            return Map.of();
        }

        // Step 3: Pick the most recent challenge and resolve its human-readable audience name.
        ChallengeEntity challenge = challenges.get(0);
        AudienceEntity aud = audienceRepository.findById(challenge.getAudienceId()).orElse(null);

        // Step 4: Check if the caller has already created an entry for this challenge.
        Optional<ChallengeEntryEntity> myEntryOpt = entryRepository.findByChallengeIdAndUserId(challenge.getId(), caller.getId());

        // Step 5: Assemble the response DTO map matching `ChallengeDto` in `openapi.yaml`.
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", challenge.getId());
        dto.put("title", challenge.getTitle());
        dto.put("prompt", challenge.getPrompt());
        dto.put("startsAt", challenge.getStartsAt().toString());
        dto.put("endsAt", challenge.getEndsAt().toString());
        dto.put("timezone", challenge.getTimezone());
        dto.put("weekStart", pulseClock.currentWeekStart()); // Monday 00:00 UTC week key (e.g., "2026-11-16")
        dto.put("audienceId", challenge.getAudienceId());
        dto.put("audienceName", aud != null ? aud.getName() : challenge.getAudienceId());
        // Dynamically compute whether the challenge is currently open using the injected PulseClock.
        dto.put("status", challenge.isOpenAt(pulseClock.now()) ? "ACTIVE" : "CLOSED");
        // Attach the caller's own entry summary (`ChallengeEntryDto`) or `null` if they haven't submitted yet.
        dto.put("myEntry", myEntryOpt.map(this::toEntryDto).orElse(null));
        return dto;
    }

    /**
     * Submits a new challenge story or resubmits an updated revision when a prior
     * entry was saved as `DRAFT` or returned by a reviewer via `REQUEST_CHANGES`.
     *
     * Runs inside a single ACID `@Transactional` boundary so `content_items`,
     * `content_revisions`, `challenge_entries`, and `audit_events` commit or roll
     * back together.
     *
     * @param caller         authenticated employee submitting the story
     * @param challengeId    target challenge ID (e.g., `"ch_beyond_01"`)
     * @param idempotencyKey client-supplied `Idempotency-Key` header
     * @param payload        JSON request map with `title`, `bodyText`, `submitForReview`
     * @return serialized `ChallengeEntryDto` map
     */
    @Transactional
    public Map<String, Object> submitEntry(UserEntity caller,
                                           String challengeId,
                                           String idempotencyKey,
                                           Map<String, Object> payload) {
        // Step 0: Verify the caller has the CREATOR role (403 Forbidden if missing).
        identityService.requireRole(caller, "CREATOR");

        // Step 1: Validate that the mandatory Idempotency-Key header is non-blank (422 if blank).
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PulseException.unprocessable("422: Idempotency-Key header is required.");
        }
        String trimmedKey = idempotencyKey.trim();

        // Step 2: Load the target challenge or return 404 Not Found.
        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> PulseException.notFound("404: Challenge not found: " + challengeId));

        // Step 3: Enforce audience membership before checking anything else (403 if excluded).
        identityService.requireAudienceAccess(caller, challenge.getAudienceId());

        // Step 4: Verify the challenge is currently within its active submission window (409 if closed/expired).
        if (!challenge.isOpenAt(pulseClock.now())) {
            throw PulseException.conflict(
                    "CHALLENGE_CLOSED",
                    "409: Challenge '" + challenge.getTitle() + "' is not open for submissions at " + pulseClock.now()
            );
        }

        // Step 5: Extract and validate `title` (5..100 chars) and `bodyText` (30..1200 chars).
        String rawTitle = payload.get("title") != null ? payload.get("title").toString().trim() : "";
        String rawBody = payload.get("bodyText") != null ? payload.get("bodyText").toString().trim() : "";
        // Default `submitForReview` to true when omitted so standard submissions go straight to IN_REVIEW.
        boolean submitForReview = payload.get("submitForReview") == null || Boolean.TRUE.equals(payload.get("submitForReview"));

        if (rawTitle.length() < 5 || rawTitle.length() > 100) {
            throw PulseException.unprocessable("422: Challenge story title must be between 5 and 100 characters.");
        }
        if (rawBody.length() < 30 || rawBody.length() > 1200) {
            throw PulseException.unprocessable("422: Challenge story bodyText must be between 30 and 1,200 characters.");
        }

        // Step 6: Compute a deterministic SHA-256 fingerprint of the request payload for idempotency verification.
        String payloadHash = sha256(challengeId + "|" + rawTitle + "|" + rawBody + "|" + submitForReview);

        // Step 7: Acquire a pessimistic write lock (`SELECT ... FOR UPDATE`) on the caller's UserEntity row.
        // Why? If the same user double-clicks "Submit" in two parallel browser tabs at the exact same millisecond,
        // locking their user row serializes the two transactions so the second transaction sees the first one's commit!
        userRepository.findByIdWithPessimisticLock(caller.getId());

        // Step 8: Check if this caller has already used this `Idempotency-Key`.
        Optional<ChallengeEntryEntity> existingByIdem = entryRepository
                .findByUserIdAndIdempotencyKey(caller.getId(), trimmedKey);
        if (existingByIdem.isPresent()) {
            ChallengeEntryEntity idemEntry = existingByIdem.get();
            // If the key was reused with a DIFFERENT title/body/challenge, reject with 409 IDEMPOTENCY_CONFLICT.
            if (!idemEntry.getPayloadHash().equals(payloadHash)) {
                throw PulseException.conflict(
                        "IDEMPOTENCY_CONFLICT",
                        "409: Idempotency-Key was already used with a different challenge submission payload."
                );
            }
            // Otherwise, this is a safe network retry of the exact same request—return the existing entry!
            return toEntryDto(idemEntry);
        }

        // Step 9: Build a 140-character preview summary from the story body for feed cards.
        String summary = rawBody.length() > 140 ? rawBody.substring(0, 140) + "..." : rawBody;

        // Step 10: Check if the caller already has an entry for this weekly challenge.
        Optional<ChallengeEntryEntity> existingForChallenge = entryRepository
                .findByChallengeIdAndUserId(challengeId, caller.getId());

        if (existingForChallenge.isPresent()) {
            // CASE A: Caller already has an entry for this challenge.
            ChallengeEntryEntity entry = existingForChallenge.get();
            ContentItemEntity item = contentItemRepository.findByIdWithPessimisticLock(entry.getContentId())
                    .orElseThrow(() -> PulseException.notFound("404: Linked content item not found."));

            // Resubmission is allowed ONLY when the story is currently in `DRAFT`
            // (for example, saved as a draft or returned by a reviewer via REQUEST_CHANGES).
            // If it is already IN_REVIEW, APPROVED, PUBLISHED, or WITHDRAWN, reject with 409 DUPLICATE_ENTRY.
            if (!"DRAFT".equals(item.getStatus())) {
                throw PulseException.conflict(
                        "DUPLICATE_ENTRY",
                        "409: One challenge story per person each week (current status: " + item.getStatus() + ")."
                );
            }

            // Create Revision N+1 with `approvedBy = null` so the reviewer must inspect the new revision.
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

            // Advance `currentVersion` on the parent ContentItemEntity and move status to IN_REVIEW (or keep DRAFT).
            item.setCurrentVersion(nextVer);
            item.setStatus(submitForReview ? "IN_REVIEW" : "DRAFT");
            item.setUpdatedAt(pulseClock.now());
            contentItemRepository.save(item);

            // Update the entry's latest idempotency key and payload hash.
            entry.setIdempotencyKey(trimmedKey);
            entry.setPayloadHash(payloadHash);
            entryRepository.save(entry);

            // Record an immutable audit trail event for the resubmission.
            auditEventRepository.save(AuditEventEntity.create(
                    caller.getId(),
                    "CHALLENGE_ENTRY_RESUBMITTED",
                    "CONTENT_REVISION",
                    newRev.getId(),
                    caller.getDisplayName() + " resubmitted Revision " + nextVer + " for challenge " + challengeId
            ));

            return toEntryDto(entry);
        }

        // CASE B: First-time submission by this employee for this challenge.
        // Step 11: Create the parent ContentItemEntity (status = IN_REVIEW or DRAFT, publishedVersion = null).
        String contentId = "story_ch_" + UUID.randomUUID().toString().substring(0, 8);
        ContentItemEntity item = new ContentItemEntity();
        item.setId(contentId);
        item.setAuthorId(caller.getId());
        item.setAudienceId(challenge.getAudienceId());
        item.setPublishedAudienceId(null); // Not published yet!
        item.setContentType("STORY");
        item.setCurrentVersion(1);
        item.setPublishedVersion(null);
        item.setStatus(submitForReview ? "IN_REVIEW" : "DRAFT");
        item.setCreatedAt(pulseClock.now());
        item.setUpdatedAt(pulseClock.now());
        contentItemRepository.save(item);

        // Step 12: Create Revision 1 (`ContentRevisionEntity`) holding the title, summary, and body text.
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

        // Step 13: Create the `ChallengeEntryEntity` linking (challengeId, userId) -> contentId.
        ChallengeEntryEntity entry = new ChallengeEntryEntity();
        entry.setId("che_" + UUID.randomUUID().toString().substring(0, 8));
        entry.setChallengeId(challengeId);
        entry.setUserId(caller.getId());
        entry.setContentId(contentId);
        entry.setIdempotencyKey(trimmedKey);
        entry.setPayloadHash(payloadHash);
        entry.setCreatedAt(pulseClock.now());
        entryRepository.save(entry);

        // Step 14: Record an immutable audit trail event in `audit_events`.
        auditEventRepository.save(AuditEventEntity.create(
                caller.getId(),
                submitForReview ? "CHALLENGE_ENTRY_SUBMITTED" : "CHALLENGE_DRAFT_SAVED",
                "CONTENT_ITEM",
                contentId,
                caller.getDisplayName() + " created Revision 1 (" + item.getStatus() + ") for challenge " + challengeId
        ));

        // Step 15: Return the newly created entry DTO (`pointsAwarded` will be 0 until publication).
        return toEntryDto(entry);
    }

    /**
     * Converts a `ChallengeEntryEntity` and its linked `ContentItemEntity` +
     * `ContentRevisionEntity` + net recognition ledger points into a JSON-ready map.
     *
     * @param entry persisted challenge entry entity
     * @return map matching `ChallengeEntryDto` in `openapi.yaml`
     */
    public Map<String, Object> toEntryDto(ChallengeEntryEntity entry) {
        // Step 1: Load the linked ContentItemEntity and its latest ContentRevisionEntity.
        ContentItemEntity item = contentItemRepository.findById(entry.getContentId()).orElseThrow();
        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion())
                .orElseThrow();

        // Step 2: Calculate net active PUBLISHED_CONTRIBUTION points awarded to this story.
        // If a story was published (+20) and later withdrawn (-20 via reversalOf), net pointsAwarded is 0.
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

        // Step 3: Populate the ordered response map.
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

    /**
     * Computes a hex-encoded SHA-256 hash of `input` so idempotency comparisons
     * are fixed-length and collision-resistant.
     *
     * @param input canonical pipe-delimited request payload string
     * @return 64-character lowercase hexadecimal SHA-256 digest
     */
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
            // Fallback (never reached on standard JVMs which always ship SHA-256).
            return Integer.toHexString(input.hashCode());
        }
    }
}
