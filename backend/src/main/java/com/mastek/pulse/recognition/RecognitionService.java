package com.mastek.pulse.recognition;

import com.mastek.pulse.challenge.ChallengeEntryEntity;
import com.mastek.pulse.challenge.ChallengeEntryRepository;
import com.mastek.pulse.common.*;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: RecognitionService.java
 * Module: `recognition/` (Campfire Recognition, Appreciation, Badges & Leaderboard)
 *
 * 1. WHAT THIS FILE DOES:
 *    This is the core domain service for Mastek Campfire's recognition and
 *    gamification engine. It manages:
 *      - Awarding `+20` contribution points when a story is published (`awardOnPublication`)
 *      - Reversing points via compensating ledger rows when a story is withdrawn (`reverseAwardsOnWithdrawal`)
 *      - Recording colleague appreciations (`+5` giver up to 3/week, `+5` receiver up to
 *        5 distinct colleagues/week) (`appreciateStory`)
 *      - Synchronizing the three weekly badges (`First Spark`, `Connection Maker`,
 *        `Community Favourite`) (`syncWeeklyBadges`)
 *      - Projecting personal weekly progress (`getPersonalProgress`) and the optional
 *        Top-5 Community Champions leaderboard (`getLeaderboard`)
 *
 * 2. WHICH USER JOURNEY / PROPOSAL REQUIREMENT IT SUPPORTS:
 *    - Proposal Pages 16, 17, 19 (Point System, 60-Point Weekly Cap, Three Badges,
 *      Community Champions Leaderboard with Opt-Out).
 *    - Implementation Stories CF-004, CF-005, CF-006, CF-007 & Slices D4 / D5.
 *
 * 3. WHY IT IS DESIGNED THIS WAY (KEY ENGINEERING PATTERNS FOR INTERNS):
 *    - Single Authoritative Synchronous Award Path (Slice D4):
 *      `awardOnPublication` is called directly inside `PublishingWorkflowService.publishOrSchedule`
 *      within the exact same `@Transactional` boundary. If publication succeeds,
 *      the `+20` ledger row commits with it; if publication fails or rolls back,
 *      zero points are written.
 *    - Deadlock-Free Pessimistic Locking (`SELECT ... FOR UPDATE`):
 *      When user A appreciates user B's story, both users' weekly caps are checked
 *      and updated. To prevent database deadlocks if A appreciates B while B
 *      simultaneously appreciates A, we always lock the two `UserEntity` rows in
 *      lexicographical ID order (`firstLockId < secondLockId`).
 *    - Immutable Ledger + Deterministic Cap Promotion on Withdrawal:
 *      When a story is withdrawn, we never delete ledger history. Instead, we insert
 *      a negative `REVERSAL_WITHDRAWN` row (`reversalOf = originalEvent.id`) and run
 *      `reconcileCappedAppreciationsForWeek` in strict `(occurredAt ASC, id ASC)`
 *      order so any 4th given appreciation or 6th received appreciation that was
 *      previously capped at `0` points is deterministically promoted to `+5`!
 *    - Strict Audience Isolation:
 *      All leaderboard and personal progress queries filter by `audienceId` so
 *      activity inside a restricted audience (`aud_uk_restricted`) never leaks
 *      into general pilot rankings (`aud_pilot`).
 * ============================================================================
 */
@Service // Registers this class as a Spring-managed transactional service bean.
public class RecognitionService {

    private final RecognitionEventRepository eventRepository;
    private final AppreciationRepository appreciationRepository;
    private final RecognitionPreferenceRepository preferenceRepository;
    private final WeeklyBadgeRepository badgeRepository;
    private final ChallengeEntryRepository challengeEntryRepository;
    private final ContentItemRepository contentItemRepository;
    private final UserRepository userRepository;
    private final AudienceRepository audienceRepository;
    private final IdentityService identityService;
    private final PulseClock pulseClock;

    /**
     * Constructor-based dependency injection for all recognition repositories and helpers.
     */
    public RecognitionService(RecognitionEventRepository eventRepository,
                              AppreciationRepository appreciationRepository,
                              RecognitionPreferenceRepository preferenceRepository,
                              WeeklyBadgeRepository badgeRepository,
                              ChallengeEntryRepository challengeEntryRepository,
                              ContentItemRepository contentItemRepository,
                              UserRepository userRepository,
                              AudienceRepository audienceRepository,
                              IdentityService identityService,
                              PulseClock pulseClock) {
        this.eventRepository = eventRepository;
        this.appreciationRepository = appreciationRepository;
        this.preferenceRepository = preferenceRepository;
        this.badgeRepository = badgeRepository;
        this.challengeEntryRepository = challengeEntryRepository;
        this.contentItemRepository = contentItemRepository;
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
        this.identityService = identityService;
        this.pulseClock = pulseClock;
    }

    /**
     * Awards `+20` contribution points when a `STORY` transitions to `PUBLISHED`,
     * capped at `+20` net contribution points per user per week (Monday 00:00 UTC).
     *
     * Intern Note on Re-Publication After Withdrawal:
     *   Suppose Asha publishes Story 1 (+20), then a moderator withdraws it (-20 reversal,
     *   net = 0), and later Asha publishes a fixed revision in the same week.
     *   Because we check `activeContributionPoints` (excluding reversed events) and
     *   append `:r<priorContribCount>` to `sourceEventId` after a reversal, Asha can
     *   legitimately earn back her `+20` upon re-publication without ever exceeding `+20` net!
     *
     * @param item the `ContentItemEntity` that just transitioned to `PUBLISHED`
     * @param rev  the approved `ContentRevisionEntity` being published
     * @return `20` if a new contribution award was written, or `0` if ineligible / already capped
     */
    @Transactional
    public int awardOnPublication(ContentItemEntity item, ContentRevisionEntity rev) {
        // Guard 1: Only PUBLISHED items of type STORY earn the +20 challenge contribution reward.
        // Updates, Radio queues, Culture Cast episodes, or SCHEDULED/IN_REVIEW items earn 0.
        if (!"PUBLISHED".equals(item.getStatus()) || !"STORY".equals(item.getContentType())) {
            return 0;
        }

        // Step 1: Acquire a pessimistic write lock (`SELECT ... FOR UPDATE`) on the author's UserEntity row.
        // This serializes concurrent publication transactions for the same author.
        userRepository.findByIdWithPessimisticLock(item.getAuthorId())
                .orElseThrow(() -> PulseException.notFound("404: Author not found: " + item.getAuthorId()));

        // Step 2: Resolve the current Monday 00:00 UTC week key and the story's immutable published audience.
        String weekStart = pulseClock.currentWeekStart();
        String pubAudienceId = item.getEffectivePublishedAudienceId();

        // Step 3: Load all ledger events for this author in the current week.
        List<RecognitionEventEntity> userEvents = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(item.getAuthorId(), weekStart);

        // Step 4: Collect the IDs of all events that have been reversed by a `REVERSAL_WITHDRAWN` entry.
        Set<String> reversedIds = userEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 5: Sum net active PUBLISHED_CONTRIBUTION points (ignoring reversed events).
        int activeContributionPoints = userEvents.stream()
                .filter(e -> "PUBLISHED_CONTRIBUTION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        // Guard 2: Weekly contribution cap is +20 per user per week. If already at 20, award 0.
        if (activeContributionPoints >= 20) {
            syncWeeklyBadges(item.getAuthorId(), weekStart, pubAudienceId);
            return 0;
        }

        // Step 6: Build a deterministic `sourceEventId`. If a prior contribution in this week was
        // withdrawn/reversed, suffix `:r<priorContribCount>` so re-publication gets a fresh unique key.
        long priorContribCount = userEvents.stream()
                .filter(e -> "PUBLISHED_CONTRIBUTION".equals(e.getReason()))
                .count();

        String sourceEventId = priorContribCount == 0
                ? "PUB:" + weekStart + ":" + item.getAuthorId() + ":" + item.getId()
                : "PUB:" + weekStart + ":" + item.getAuthorId() + ":" + item.getId() + ":r" + priorContribCount;

        // Guard 3: Idempotency check against `source_event_id` unique index.
        if (eventRepository.findBySourceEventId(sourceEventId).isPresent()) {
            return 0;
        }

        // Step 7: Look up the associated challenge ID if this story was submitted via `challenge_entries`.
        String challengeId = challengeEntryRepository.findByContentId(item.getId())
                .map(ChallengeEntryEntity::getChallengeId)
                .orElse("ch_beyond_01");

        // Step 8: Persist the +20 immutable ledger row in `recognition_events`.
        RecognitionEventEntity award = new RecognitionEventEntity();
        award.setId("rec_" + UUID.randomUUID().toString().substring(0, 8));
        award.setUserId(item.getAuthorId());
        award.setChallengeId(challengeId);
        award.setAudienceId(pubAudienceId);
        award.setWeekStart(weekStart);
        award.setSourceEventId(sourceEventId);
        award.setContentId(item.getId());
        award.setReason("PUBLISHED_CONTRIBUTION");
        award.setPointsDelta(20);
        award.setCreatedAt(pulseClock.now());
        eventRepository.save(award);

        // Step 9: Synchronize the author's weekly badges (awards `First Spark`!).
        syncWeeklyBadges(item.getAuthorId(), weekStart, pubAudienceId);
        return 20;
    }

    /**
     * Inserts compensating `REVERSAL_WITHDRAWN` ledger entries when a published story
     * is withdrawn, then deterministically promotes any capped appreciations in that
     * week and resynchronizes affected users' badges.
     *
     * @param item    the `ContentItemEntity` being withdrawn
     * @param actorId user ID of the publisher/moderator performing the withdrawal
     */
    @Transactional
    public void reverseAwardsOnWithdrawal(ContentItemEntity item, String actorId) {
        // Step 1: Load all recognition ledger entries ever created for this story (`contentId`).
        List<RecognitionEventEntity> contentEvents = eventRepository.findByContentIdOrderByCreatedAtAscIdAsc(item.getId());
        Set<String> affectedUsers = new LinkedHashSet<>();
        Set<String> affectedWeeks = new LinkedHashSet<>();
        String pubAudienceId = item.getEffectivePublishedAudienceId();

        // Step 2: For each positive award row that hasn't been reversed yet, insert a negative reversal row.
        for (RecognitionEventEntity ev : contentEvents) {
            // Skip rows that are themselves reversals.
            if (ev.getReversalOf() != null) {
                continue;
            }
            // Skip rows that already have a reversal row pointing to them.
            if (eventRepository.findByReversalOf(ev.getId()).isPresent()) {
                continue;
            }

            // Create the compensating negative ledger entry (`-ev.getPointsDelta()`).
            RecognitionEventEntity rev = new RecognitionEventEntity();
            rev.setId("rec_rev_" + UUID.randomUUID().toString().substring(0, 8));
            rev.setUserId(ev.getUserId());
            rev.setChallengeId(ev.getChallengeId());
            rev.setAudienceId(ev.getAudienceId());
            rev.setWeekStart(ev.getWeekStart());
            rev.setSourceEventId("REV:" + ev.getId());
            rev.setContentId(ev.getContentId());
            rev.setReason("REVERSAL_WITHDRAWN");
            rev.setPointsDelta(-ev.getPointsDelta());
            rev.setCounterpartyUserId(ev.getCounterpartyUserId());
            rev.setCounterpartyTeamSnapshot(ev.getCounterpartyTeamSnapshot());
            rev.setReversalOf(ev.getId());
            rev.setCreatedAt(pulseClock.now());
            eventRepository.save(rev);

            affectedUsers.add(ev.getUserId());
            affectedWeeks.add(ev.getWeekStart());
        }

        // Step 3: If reversing an appreciation freed up a slot under a giver's (+15) or receiver's (+25)
        // weekly cap, deterministically promote the next eligible appreciation in `(occurredAt ASC, id ASC)` order!
        for (String wk : affectedWeeks) {
            reconcileCappedAppreciationsForWeek(wk, affectedUsers);
        }

        // Step 4: Re-evaluate weekly badges for all affected users (revoking badges whose criteria are no longer met).
        for (String uid : affectedUsers) {
            for (String wk : affectedWeeks) {
                syncWeeklyBadges(uid, wk, pubAudienceId);
            }
        }
    }

    /**
     * Records a colleague's appreciation on a published story, enforces weekly
     * giving (`+5` up to 3 stories = `+15`) and receiving (`+5` up to 5 distinct
     * colleagues = `+25`) caps, and updates badges for both participants.
     *
     * @param caller    authenticated employee clicking "Appreciate"
     * @param contentId ID of the published story being appreciated
     * @return map matching `AppreciationResultDto` in `openapi.yaml`
     */
    @Transactional
    public Map<String, Object> appreciateStory(UserEntity caller, String contentId) {
        // Step 1: Load the target story or throw 404 Not Found.
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Story not found: " + contentId));

        // Step 2: Enforce published visibility! Draft, In-Review, Scheduled, Withdrawn,
        // or stories outside the caller's permitted audiences CANNOT be appreciated (throws 403/404).
        identityService.requirePublishedVisibility(caller, item);

        // Step 3: Reject self-appreciation (an author cannot appreciate their own story -> 422).
        if (item.getAuthorId().equals(caller.getId())) {
            throw PulseException.unprocessable("422: Self-appreciation is disallowed — appreciate a colleague's story.");
        }

        // Step 4: Lock BOTH the giver and the author UserEntity rows in deterministic lexicographical ID order!
        // Intern Tip: Always acquiring locks in `min(idA, idB)` then `max(idA, idB)` order makes circular
        // wait (deadlock) mathematically impossible even under heavy concurrent appreciation traffic.
        String firstLockId = caller.getId().compareTo(item.getAuthorId()) < 0 ? caller.getId() : item.getAuthorId();
        String secondLockId = caller.getId().compareTo(item.getAuthorId()) < 0 ? item.getAuthorId() : caller.getId();
        userRepository.findByIdWithPessimisticLock(firstLockId);
        userRepository.findByIdWithPessimisticLock(secondLockId);

        // Step 5: Load the story author's entity so we can snapshot their team (`functionName`).
        UserEntity author = userRepository.findById(item.getAuthorId())
                .orElseThrow(() -> PulseException.notFound("404: Story author not found."));

        String weekStart = pulseClock.currentWeekStart();
        String pubAudienceId = item.getEffectivePublishedAudienceId();

        // Step 6: Idempotency check — has `caller` already appreciated `contentId`?
        Optional<AppreciationEntity> existingOpt = appreciationRepository
                .findByActorIdAndContentId(caller.getId(), contentId);
        if (existingOpt.isPresent()) {
            // Safe replay: return `alreadyAppreciated = true` with 0 delta points.
            AppreciationEntity existing = existingOpt.get();
            Map<String, Object> progress = getPersonalProgress(caller, weekStart);
            return Map.of(
                    "appreciationId", existing.getId(),
                    "contentId", contentId,
                    "actorId", caller.getId(),
                    "recipientId", author.getId(),
                    "weekStart", existing.getWeekStart(),
                    "alreadyAppreciated", true,
                    "giverPointsDelta", 0,
                    "receiverPointsDelta", 0,
                    "actorProgress", progress
            );
        }

        // Step 7: Persist the new AppreciationEntity with immutable point-in-time team snapshots.
        AppreciationEntity appreciation = new AppreciationEntity();
        appreciation.setId("app_" + UUID.randomUUID().toString().substring(0, 8));
        appreciation.setActorId(caller.getId());
        appreciation.setContentId(contentId);
        appreciation.setRecipientId(author.getId());
        appreciation.setWeekStart(weekStart);
        appreciation.setActorTeamSnapshot(caller.getFunctionName());
        appreciation.setRecipientTeamSnapshot(author.getFunctionName());
        appreciation.setOccurredAt(pulseClock.now());
        appreciationRepository.save(appreciation);

        // Step 8: Evaluate weekly caps and write ledger rows for giver (+5 or 0) and receiver (+5 or 0).
        int giverDelta = maybeAwardGiverPoints(appreciation, pubAudienceId);
        int receiverDelta = maybeAwardReceiverPoints(appreciation, pubAudienceId);

        // Step 9: Synchronize weekly badges for both the giver (`Connection Maker`) and author (`Community Favourite`).
        syncWeeklyBadges(caller.getId(), weekStart, pubAudienceId);
        syncWeeklyBadges(author.getId(), weekStart, pubAudienceId);

        // Step 10: Build and return the `AppreciationResultDto` response map.
        Map<String, Object> progress = getPersonalProgress(caller, weekStart);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appreciationId", appreciation.getId());
        result.put("contentId", contentId);
        result.put("actorId", caller.getId());
        result.put("recipientId", author.getId());
        result.put("weekStart", weekStart);
        result.put("alreadyAppreciated", false);
        result.put("giverPointsDelta", giverDelta);
        result.put("receiverPointsDelta", receiverDelta);
        result.put("actorProgress", progress);
        return result;
    }

    /**
     * Awards `+5` giving points to `appreciation.actorId` if they have earned fewer
     * than `15` net giving points (3 appreciated stories) in `appreciation.weekStart`.
     *
     * @param appreciation persisted appreciation event
     * @param audienceId   published audience ID of the appreciated story
     * @return `5` if awarded, or `0` if weekly giving cap (`+15`) was already reached
     */
    private int maybeAwardGiverPoints(AppreciationEntity appreciation, String audienceId) {
        String giverId = appreciation.getActorId();
        String weekStart = appreciation.getWeekStart();

        // Step 1: Load the giver's ledger events for this week.
        List<RecognitionEventEntity> giverEvents = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(giverId, weekStart);

        // Step 2: Identify any events that were reversed by a story withdrawal.
        Set<String> reversedIds = giverEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 3: Sum net active `GIVE_APPRECIATION` points for this week.
        int activeGivePoints = giverEvents.stream()
                .filter(e -> "GIVE_APPRECIATION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        // Guard 1: Giver cap is +15 per week (first 3 stories * 5 pts).
        if (activeGivePoints >= 15) {
            return 0;
        }

        // Guard 2: Idempotency check so the same appreciation never gets rewarded twice.
        String sourceKey = "GIVE:" + weekStart + ":" + giverId + ":" + appreciation.getId();
        if (eventRepository.findBySourceEventId(sourceKey).isPresent()) {
            return 0;
        }

        // Step 4: Insert the +5 `GIVE_APPRECIATION` ledger entry.
        RecognitionEventEntity ev = new RecognitionEventEntity();
        ev.setId("rec_" + UUID.randomUUID().toString().substring(0, 8));
        ev.setUserId(giverId);
        ev.setChallengeId("ch_beyond_01");
        ev.setAudienceId(audienceId);
        ev.setWeekStart(weekStart);
        ev.setSourceEventId(sourceKey);
        ev.setContentId(appreciation.getContentId());
        ev.setReason("GIVE_APPRECIATION");
        ev.setPointsDelta(5);
        ev.setCounterpartyUserId(appreciation.getRecipientId());
        ev.setCounterpartyTeamSnapshot(appreciation.getRecipientTeamSnapshot());
        ev.setCreatedAt(pulseClock.now());
        eventRepository.save(ev);
        return 5;
    }

    /**
     * Awards `+5` receiving points to `appreciation.recipientId` (the story author)
     * if they have earned fewer than `25` net receiving points in `weekStart` AND
     * have not already received active points from `appreciation.actorId` this week.
     *
     * @param appreciation persisted appreciation event
     * @param audienceId   published audience ID of the appreciated story
     * @return `5` if awarded, or `0` if capped (`+25`) or same colleague already rewarded
     */
    private int maybeAwardReceiverPoints(AppreciationEntity appreciation, String audienceId) {
        String recipientId = appreciation.getRecipientId();
        String weekStart = appreciation.getWeekStart();

        // Step 1: Load the recipient's ledger events for this week.
        List<RecognitionEventEntity> recEvents = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(recipientId, weekStart);

        // Step 2: Identify any events that were reversed by a story withdrawal.
        Set<String> reversedIds = recEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 3: Filter to active (unreversed) `RECEIVE_APPRECIATION` events.
        List<RecognitionEventEntity> activeReceives = recEvents.stream()
                .filter(e -> "RECEIVE_APPRECIATION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .toList();

        int activeReceivePoints = activeReceives.stream()
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        // Step 4: Check if this specific colleague (`actorId`) has already generated an active
        // +5 receive reward for `recipientId` in this week ("+5 per distinct person").
        boolean sameColleagueAlreadyRewarded = activeReceives.stream()
                .anyMatch(e -> appreciation.getActorId().equals(e.getCounterpartyUserId()));

        // Guard 1: Receiver cap is +25 per week (5 distinct colleagues * 5 pts).
        if (activeReceivePoints >= 25 || sameColleagueAlreadyRewarded) {
            return 0;
        }

        // Guard 2: Idempotency check on `sourceKey`.
        String sourceKey = "RECV:" + weekStart + ":" + recipientId + ":" + appreciation.getActorId() + ":" + appreciation.getId();
        if (eventRepository.findBySourceEventId(sourceKey).isPresent()) {
            return 0;
        }

        // Step 5: Insert the +5 `RECEIVE_APPRECIATION` ledger entry.
        RecognitionEventEntity ev = new RecognitionEventEntity();
        ev.setId("rec_" + UUID.randomUUID().toString().substring(0, 8));
        ev.setUserId(recipientId);
        ev.setChallengeId("ch_beyond_01");
        ev.setAudienceId(audienceId);
        ev.setWeekStart(weekStart);
        ev.setSourceEventId(sourceKey);
        ev.setContentId(appreciation.getContentId());
        ev.setReason("RECEIVE_APPRECIATION");
        ev.setPointsDelta(5);
        ev.setCounterpartyUserId(appreciation.getActorId());
        ev.setCounterpartyTeamSnapshot(appreciation.getActorTeamSnapshot());
        ev.setCreatedAt(pulseClock.now());
        eventRepository.save(ev);
        return 5;
    }

    /**
     * Re-scans all appreciations in `weekStart` in strict `(occurredAt ASC, id ASC)`
     * order after a story withdrawal. If a previously capped appreciation (e.g., the
     * 4th story a user appreciated that week) is still on a currently `PUBLISHED`
     * story and a slot opened up under the cap, it is deterministically promoted to `+5`.
     *
     * @param weekStart     Monday 00:00 UTC week key
     * @param affectedUsers mutable set to record any users whose points changed during promotion
     */
    private void reconcileCappedAppreciationsForWeek(String weekStart, Set<String> affectedUsers) {
        // Step 1: Load all appreciations for the week in deterministic chronological order.
        List<AppreciationEntity> weekApps = appreciationRepository.findByWeekStartOrderByOccurredAtAscIdAsc(weekStart);
        Map<String, ContentItemEntity> contentMap = new HashMap<>();
        contentItemRepository.findAll().forEach(c -> contentMap.put(c.getId(), c));

        // Step 2: Walk each appreciation; skip any appreciation whose story is no longer PUBLISHED.
        for (AppreciationEntity app : weekApps) {
            ContentItemEntity story = contentMap.get(app.getContentId());
            if (story == null || !"PUBLISHED".equals(story.getStatus()) || story.getPublishedVersion() == null) {
                continue;
            }
            String pubAud = story.getEffectivePublishedAudienceId();
            // If the giver or receiver now has room under their weekly cap, award the +5 ledger entry!
            if (maybeAwardGiverPoints(app, pubAud) > 0) {
                affectedUsers.add(app.getActorId());
            }
            if (maybeAwardReceiverPoints(app, pubAud) > 0) {
                affectedUsers.add(app.getRecipientId());
            }
        }
    }

    /**
     * Synchronizes the `weekly_badges` table for `userId` in `weekStart` and `audienceId`
     * so persisted badges match the user's current active progress:
     *   - `FIRST_SPARK`         ("First Spark"):         `contributionPoints >= 20`
     *   - `CONNECTION_MAKER`    ("Connection Maker"):    `distinctTeamsSupported >= 3`
     *   - `COMMUNITY_FAVOURITE` ("Community Favourite"): `receivedDistinctCount >= 5`
     *
     * @param userId     employee's user ID
     * @param weekStart  Monday 00:00 UTC week key
     * @param audienceId audience scope ID
     */
    @Transactional
    public void syncWeeklyBadges(String userId, String weekStart, String audienceId) {
        // Step 1: Compute the user's current progress within `audienceId`.
        UserProgressSummary summary = computeUserProgressSummary(userId, weekStart, List.of(audienceId));

        // Step 2: Determine which of the 3 milestone badges the user currently qualifies for.
        Map<String, String[]> desiredBadges = new LinkedHashMap<>();
        if (summary.contributionPoints() >= 20) {
            desiredBadges.put("FIRST_SPARK", new String[]{"First Spark", "Published challenge story in week " + weekStart});
        }
        if (summary.distinctTeamsSupported() >= 3) {
            desiredBadges.put("CONNECTION_MAKER", new String[]{"Connection Maker", "Appreciated published stories across " + summary.distinctTeamsSupported() + " teams"});
        }
        if (summary.receivedDistinctCount() >= 5) {
            desiredBadges.put("COMMUNITY_FAVOURITE", new String[]{"Community Favourite", "Received appreciation from " + summary.receivedDistinctCount() + " distinct colleagues"});
        }

        // Step 3: Load existing persisted badges for this user, week, and audience.
        List<WeeklyBadgeEntity> existing = badgeRepository.findByUserIdAndWeekStart(userId, weekStart).stream()
                .filter(b -> audienceId.equals(b.getAudienceId()))
                .toList();

        // Step 4: Delete any badge the user no longer qualifies for (e.g., after a story withdrawal).
        Set<String> existingCodes = new HashSet<>();
        for (WeeklyBadgeEntity b : existing) {
            if (!desiredBadges.containsKey(b.getBadgeCode())) {
                badgeRepository.delete(b);
            } else {
                existingCodes.add(b.getBadgeCode());
            }
        }
        badgeRepository.flush();

        // Step 5: Insert any newly earned badge that isn't persisted yet.
        for (Map.Entry<String, String[]> entry : desiredBadges.entrySet()) {
            if (!existingCodes.contains(entry.getKey())) {
                saveBadge(userId, weekStart, audienceId, entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
            }
        }
    }

    /**
     * Helper that constructs and persists a new `WeeklyBadgeEntity`.
     */
    private void saveBadge(String userId, String weekStart, String audienceId, String code, String label, String evidence) {
        WeeklyBadgeEntity b = new WeeklyBadgeEntity();
        b.setId("bdg_" + UUID.randomUUID().toString().substring(0, 8));
        b.setUserId(userId);
        b.setWeekStart(weekStart);
        b.setAudienceId(audienceId);
        b.setBadgeCode(code);
        b.setBadgeLabel(label);
        b.setQualifyingEvidence(evidence);
        b.setAwardedAt(pulseClock.now());
        badgeRepository.save(b);
    }

    /**
     * Computes and returns the caller's personal recognition summary (`GET /api/v1/recognition/me`)
     * across their permitted audiences for `requestedWeek`.
     *
     * @param caller        authenticated employee
     * @param requestedWeek optional Monday 00:00 UTC week key (`"YYYY-MM-DD"`)
     * @return map matching `PersonalRecognitionDto` in `openapi.yaml`
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPersonalProgress(UserEntity caller, String requestedWeek) {
        // Step 1: Default `weekStart` to the current Monday 00:00 UTC week if not specified.
        String weekStart = (requestedWeek == null || requestedWeek.isBlank())
                ? pulseClock.currentWeekStart()
                : requestedWeek.trim();

        // Step 2: Filter strictly to the audiences `caller` is permitted to access.
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        UserProgressSummary summary = computeUserProgressSummary(caller.getId(), weekStart, permittedAudiences);

        // Step 3: Look up the caller's leaderboard opt-in preference (defaults to `true`).
        boolean showOnLeaderboard = preferenceRepository.findById(caller.getId())
                .map(RecognitionPreferenceEntity::isShowOnLeaderboard)
                .orElse(true);

        // Step 4: Assemble the response map matching `PersonalRecognitionDto`.
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("userId", caller.getId());
        dto.put("displayName", caller.getDisplayName());
        dto.put("weekStart", weekStart);
        dto.put("timezone", "UTC");
        dto.put("points", summary.totalPoints());
        dto.put("contributionPoints", summary.contributionPoints());
        dto.put("givenCount", summary.givenCount());
        dto.put("givingPoints", summary.givingPoints());
        dto.put("receivedDistinctCount", summary.receivedDistinctCount());
        dto.put("receivingPoints", summary.receivingPoints());
        dto.put("distinctTeamsSupported", summary.distinctTeamsSupported());
        dto.put("badges", summary.badges());
        dto.put("showOnLeaderboard", showOnLeaderboard);
        return dto;
    }

    /**
     * Updates the caller's leaderboard opt-in preference (`PUT /api/v1/recognition/preferences`)
     * and returns their updated personal progress DTO.
     *
     * @param caller            authenticated employee
     * @param showOnLeaderboard `true` to appear on the Top-5 leaderboard, `false` to hide
     * @return updated `PersonalRecognitionDto` map
     */
    @Transactional
    public Map<String, Object> updatePreferences(UserEntity caller, boolean showOnLeaderboard) {
        // Upsert the user's RecognitionPreferenceEntity row.
        RecognitionPreferenceEntity pref = preferenceRepository.findById(caller.getId())
                .orElseGet(() -> {
                    RecognitionPreferenceEntity p = new RecognitionPreferenceEntity();
                    p.setUserId(caller.getId());
                    return p;
                });
        pref.setShowOnLeaderboard(showOnLeaderboard);
        pref.setUpdatedAt(pulseClock.now());
        preferenceRepository.save(pref);

        // Return the caller's personal progress (which remains intact even when opted out!).
        return getPersonalProgress(caller, pulseClock.currentWeekStart());
    }

    /**
     * Computes the Top-5 opted-in Community Champions leaderboard for a single
     * authorized `audienceId` and `weekStart` (Monday 00:00 UTC).
     *
     * Guardrails Enforced:
     *   1. Caller must belong to `targetAudience` (`403 Forbidden` otherwise).
     *   2. Only users who belong to `targetAudience` and have `showOnLeaderboard == true`
     *      are included as candidates.
     *   3. Points and badges are computed strictly from activity inside `List.of(targetAudience)`
     *      so restricted-audience stories (`aud_uk_restricted`) never affect `aud_pilot` rankings!
     *   4. Tied scores share the same `rank` number and are ordered alphabetically by `displayName`.
     *
     * @param caller        authenticated employee requesting the leaderboard
     * @param requestedWeek optional Monday 00:00 UTC week key
     * @param audienceId    target audience ID (defaults to `"aud_pilot"`)
     * @return map matching `LeaderboardDto` in `openapi.yaml`
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLeaderboard(UserEntity caller, String requestedWeek, String audienceId) {
        // Step 1: Resolve target audience and verify the caller is a member of that audience (403 if not).
        String targetAudience = (audienceId == null || audienceId.isBlank()) ? "aud_pilot" : audienceId.trim();
        identityService.requireAudienceAccess(caller, targetAudience);

        String weekStart = (requestedWeek == null || requestedWeek.isBlank())
                ? pulseClock.currentWeekStart()
                : requestedWeek.trim();

        // Step 2: Load all leaderboard opt-in/opt-out preferences into a fast lookup map.
        Map<String, Boolean> optInMap = new HashMap<>();
        preferenceRepository.findAll().forEach(p -> optInMap.put(p.getUserId(), p.isShowOnLeaderboard()));

        // Step 3: Evaluate every opted-in member of `targetAudience`.
        List<UserEntity> allUsers = userRepository.findAll();
        List<LeaderboardCandidate> candidates = new ArrayList<>();

        for (UserEntity user : allUsers) {
            List<String> userAuds = audienceRepository.findPermittedAudienceIdsByUserId(user.getId());
            if (!userAuds.contains(targetAudience)) {
                continue;
            }
            if (!optInMap.getOrDefault(user.getId(), true)) {
                // User opted out of public leaderboard display—skip!
                continue;
            }
            // Compute progress strictly within `targetAudience` so restricted activity never leaks!
            UserProgressSummary summary = computeUserProgressSummary(user.getId(), weekStart, List.of(targetAudience));
            candidates.add(new LeaderboardCandidate(user, summary.totalPoints(), summary.badges()));
        }

        // Step 4: Sort candidates by `points DESC`, breaking ties alphabetically by `displayName ASC`.
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(b.points(), a.points());
            if (cmp != 0) return cmp;
            return a.user().getDisplayName().compareToIgnoreCase(b.user().getDisplayName());
        });

        // Step 5: Take at most the Top 5 and assign competition ranks (tied scores share rank).
        List<Map<String, Object>> entries = new ArrayList<>();
        int currentRank = 1;
        for (int i = 0; i < Math.min(5, candidates.size()); i++) {
            LeaderboardCandidate c = candidates.get(i);
            if (i > 0 && c.points() < candidates.get(i - 1).points()) {
                currentRank = i + 1;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", currentRank);
            row.put("userId", c.user().getId());
            row.put("displayName", c.user().getDisplayName());
            row.put("functionName", c.user().getFunctionName());
            row.put("region", c.user().getRegion());
            row.put("points", c.points());
            row.put("badges", c.badges());
            entries.add(row);
        }

        // Step 6: Build and return the `LeaderboardDto` response map.
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("weekStart", weekStart);
        response.put("timezone", "UTC");
        response.put("audienceId", targetAudience);
        response.put("entries", entries);
        return response;
    }

    /**
     * Pure projection helper that aggregates a user's weekly points, counts, and
     * badges strictly over events and published stories belonging to `allowedAudiences`.
     *
     * @param userId           employee's user ID
     * @param weekStart        Monday 00:00 UTC week key
     * @param allowedAudiences permitted audience IDs to include in the projection
     * @return immutable `UserProgressSummary` record
     */
    private UserProgressSummary computeUserProgressSummary(String userId, String weekStart, List<String> allowedAudiences) {
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            return new UserProgressSummary(0, 0, 0, 0, 0, 0, 0, List.of());
        }

        // Step 1: Load all ledger events for `userId` in `weekStart` and filter by `allowedAudiences`.
        List<RecognitionEventEntity> events = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(userId, weekStart)
                .stream()
                .filter(e -> allowedAudiences.contains(e.getAudienceId()))
                .toList();

        // Step 2: Collect IDs of events reversed by a `REVERSAL_WITHDRAWN` entry.
        Set<String> reversedIds = events.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 3: Compute capped category subtotals (Contribution <= 20, Giving <= 15, Receiving <= 25).
        int contrib = events.stream()
                .filter(e -> "PUBLISHED_CONTRIBUTION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();
        contrib = Math.max(0, Math.min(20, contrib));

        int givingPts = events.stream()
                .filter(e -> "GIVE_APPRECIATION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();
        givingPts = Math.max(0, Math.min(15, givingPts));

        int receivingPts = events.stream()
                .filter(e -> "RECEIVE_APPRECIATION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();
        receivingPts = Math.max(0, Math.min(25, receivingPts));

        // Step 4: Load active PUBLISHED stories in `allowedAudiences` to count valid appreciations and badges.
        Map<String, ContentItemEntity> contentMap = new HashMap<>();
        contentItemRepository.findAll().forEach(c -> contentMap.put(c.getId(), c));

        List<AppreciationEntity> givenApps = appreciationRepository
                .findByActorIdAndWeekStartOrderByOccurredAtAscIdAsc(userId, weekStart)
                .stream()
                .filter(a -> {
                    ContentItemEntity c = contentMap.get(a.getContentId());
                    return c != null
                            && "PUBLISHED".equals(c.getStatus())
                            && c.getPublishedVersion() != null
                            && allowedAudiences.contains(c.getEffectivePublishedAudienceId());
                })
                .toList();

        int givenCount = givenApps.size();
        // Count distinct author teams (`recipientTeamSnapshot`) for the `Connection Maker` badge.
        int distinctTeams = (int) givenApps.stream()
                .map(AppreciationEntity::getRecipientTeamSnapshot)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();

        List<AppreciationEntity> receivedApps = appreciationRepository
                .findByRecipientIdAndWeekStartOrderByOccurredAtAscIdAsc(userId, weekStart)
                .stream()
                .filter(a -> {
                    ContentItemEntity c = contentMap.get(a.getContentId());
                    return c != null
                            && "PUBLISHED".equals(c.getStatus())
                            && c.getPublishedVersion() != null
                            && allowedAudiences.contains(c.getEffectivePublishedAudienceId());
                })
                .toList();

        // Count distinct appreciating colleagues (`actorId`) for the `Community Favourite` badge.
        int receivedDistinct = (int) receivedApps.stream()
                .map(AppreciationEntity::getActorId)
                .collect(Collectors.toSet())
                .size();

        // Step 5: Evaluate the 3 Campfire weekly badges.
        List<String> badges = new ArrayList<>();
        if (contrib >= 20) {
            badges.add("First Spark");
        }
        if (distinctTeams >= 3) {
            badges.add("Connection Maker");
        }
        if (receivedDistinct >= 5) {
            badges.add("Community Favourite");
        }

        // Step 6: Enforce the overall 60-point weekly ceiling (`20 + 15 + 25 = 60`).
        int total = Math.min(60, contrib + givingPts + receivingPts);
        return new UserProgressSummary(total, contrib, givenCount, givingPts, receivedDistinct, receivingPts, distinctTeams, badges);
    }

    /**
     * Internal value record holding a user's computed weekly recognition metrics.
     */
    private record UserProgressSummary(
            int totalPoints,
            int contributionPoints,
            int givenCount,
            int givingPoints,
            int receivedDistinctCount,
            int receivingPoints,
            int distinctTeamsSupported,
            List<String> badges
    ) {}

    /**
     * Internal value record representing an opted-in leaderboard candidate before sorting.
     */
    private record LeaderboardCandidate(UserEntity user, int points, List<String> badges) {}
}
