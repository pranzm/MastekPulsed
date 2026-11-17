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
 * Journey: Campfire Engagement, Transactional Recognition Ledger, Appreciation,
 *          Three Badges & Optional Community Champions Leaderboard
 *          (Proposal Pages 16, 17, 19 & Stories CF-004, CF-005, CF-006, CF-007).
 * Why it is designed this way:
 *   1. Single Authoritative Synchronous Award Path (D4):
 *      Called directly inside `PublishingWorkflowService.publishOrSchedule`
 *      within the same `@Transactional` boundary. Awards `+20` ONLY when a story
 *      is actually published (`status == PUBLISHED`), at most once per user/week.
 *      If publication rolls back, the award rolls back atomically.
 *   2. Pessimistic User Row Locking (`SELECT ... FOR UPDATE`):
 *      Locks involved user rows in deterministic ID order so concurrent
 *      appreciations or publications never exceed the weekly caps (`+20` contribution,
 *      `+15` giving for first 3 stories, `+25` receiving for first 5 distinct
 *      colleagues, `60` total max).
 *   3. Immutable Ledger & Compensating Withdrawal Reversals (CF-005):
 *      Withdrawing a story inserts negative `REVERSAL_WITHDRAWN` rows with
 *      `reversalOf = originalEvent.id` and deterministically promotes any
 *      previously capped appreciation in `(occurredAt ASC, id ASC)` order.
 *   4. Audience-Scoped Projections (CF-007 / CF-010):
 *      Leaderboard and progress projections filter strictly by `audienceId` so
 *      restricted-audience activity (`aud_uk_restricted`) never leaks!
 * ============================================================================
 */
@Service
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
     * Awards +20 contribution points when a STORY is published, at most once per
     * user per week. Safe across retries and republications.
     */
    @Transactional
    public int awardOnPublication(ContentItemEntity item, ContentRevisionEntity rev) {
        if (!"PUBLISHED".equals(item.getStatus()) || !"STORY".equals(item.getContentType())) {
            return 0;
        }

        // Lock author row to serialize concurrent publication awards
        userRepository.findByIdWithPessimisticLock(item.getAuthorId())
                .orElseThrow(() -> PulseException.notFound("404: Author not found: " + item.getAuthorId()));

        String weekStart = pulseClock.currentWeekStart();
        String pubAudienceId = item.getEffectivePublishedAudienceId();

        // Check net active PUBLISHED_CONTRIBUTION points for this user in weekStart
        List<RecognitionEventEntity> userEvents = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(item.getAuthorId(), weekStart);

        Set<String> reversedIds = userEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int activeContributionPoints = userEvents.stream()
                .filter(e -> "PUBLISHED_CONTRIBUTION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        if (activeContributionPoints >= 20) {
            syncWeeklyBadges(item.getAuthorId(), weekStart, pubAudienceId);
            return 0;
        }

        long priorContribCount = userEvents.stream()
                .filter(e -> "PUBLISHED_CONTRIBUTION".equals(e.getReason()))
                .count();

        String sourceEventId = priorContribCount == 0
                ? "PUB:" + weekStart + ":" + item.getAuthorId() + ":" + item.getId()
                : "PUB:" + weekStart + ":" + item.getAuthorId() + ":" + item.getId() + ":r" + priorContribCount;

        if (eventRepository.findBySourceEventId(sourceEventId).isPresent()) {
            return 0;
        }

        String challengeId = challengeEntryRepository.findByContentId(item.getId())
                .map(ChallengeEntryEntity::getChallengeId)
                .orElse("ch_beyond_01");

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

        syncWeeklyBadges(item.getAuthorId(), weekStart, pubAudienceId);
        return 20;
    }

    /**
     * Inserts compensating REVERSAL_WITHDRAWN ledger entries when a story is
     * withdrawn and deterministically promotes any capped appreciations in that week.
     */
    @Transactional
    public void reverseAwardsOnWithdrawal(ContentItemEntity item, String actorId) {
        List<RecognitionEventEntity> contentEvents = eventRepository.findByContentIdOrderByCreatedAtAscIdAsc(item.getId());
        Set<String> affectedUsers = new LinkedHashSet<>();
        Set<String> affectedWeeks = new LinkedHashSet<>();
        String pubAudienceId = item.getEffectivePublishedAudienceId();

        for (RecognitionEventEntity ev : contentEvents) {
            if (ev.getReversalOf() != null) {
                continue;
            }
            if (eventRepository.findByReversalOf(ev.getId()).isPresent()) {
                continue;
            }
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

        // Deterministically promote any earlier capped appreciations in affected weeks
        for (String wk : affectedWeeks) {
            reconcileCappedAppreciationsForWeek(wk, affectedUsers);
        }

        for (String uid : affectedUsers) {
            for (String wk : affectedWeeks) {
                syncWeeklyBadges(uid, wk, pubAudienceId);
            }
        }
    }

    /**
     * Records colleague appreciation on a published story (+5 giver up to 3/wk,
     * +5 receiver up to 5 distinct colleagues/wk). Idempotent on repeat calls.
     */
    @Transactional
    public Map<String, Object> appreciateStory(UserEntity caller, String contentId) {
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Story not found: " + contentId));

        // D2 / CF-006 Invariant: Only visible PUBLISHED stories can receive appreciation!
        identityService.requirePublishedVisibility(caller, item);

        if (item.getAuthorId().equals(caller.getId())) {
            throw PulseException.unprocessable("422: Self-appreciation is disallowed — appreciate a colleague's story.");
        }

        // Lock both users in deterministic ID order to prevent deadlocks and serialize cap checks
        String firstLockId = caller.getId().compareTo(item.getAuthorId()) < 0 ? caller.getId() : item.getAuthorId();
        String secondLockId = caller.getId().compareTo(item.getAuthorId()) < 0 ? item.getAuthorId() : caller.getId();
        userRepository.findByIdWithPessimisticLock(firstLockId);
        userRepository.findByIdWithPessimisticLock(secondLockId);

        UserEntity author = userRepository.findById(item.getAuthorId())
                .orElseThrow(() -> PulseException.notFound("404: Story author not found."));

        String weekStart = pulseClock.currentWeekStart();
        String pubAudienceId = item.getEffectivePublishedAudienceId();

        Optional<AppreciationEntity> existingOpt = appreciationRepository
                .findByActorIdAndContentId(caller.getId(), contentId);
        if (existingOpt.isPresent()) {
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

        int giverDelta = maybeAwardGiverPoints(appreciation, pubAudienceId);
        int receiverDelta = maybeAwardReceiverPoints(appreciation, pubAudienceId);

        syncWeeklyBadges(caller.getId(), weekStart, pubAudienceId);
        syncWeeklyBadges(author.getId(), weekStart, pubAudienceId);

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

    private int maybeAwardGiverPoints(AppreciationEntity appreciation, String audienceId) {
        String giverId = appreciation.getActorId();
        String weekStart = appreciation.getWeekStart();
        List<RecognitionEventEntity> giverEvents = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(giverId, weekStart);

        Set<String> reversedIds = giverEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int activeGivePoints = giverEvents.stream()
                .filter(e -> "GIVE_APPRECIATION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        if (activeGivePoints >= 15) {
            return 0;
        }

        String sourceKey = "GIVE:" + weekStart + ":" + giverId + ":" + appreciation.getId();
        if (eventRepository.findBySourceEventId(sourceKey).isPresent()) {
            return 0;
        }

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

    private int maybeAwardReceiverPoints(AppreciationEntity appreciation, String audienceId) {
        String recipientId = appreciation.getRecipientId();
        String weekStart = appreciation.getWeekStart();
        List<RecognitionEventEntity> recEvents = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(recipientId, weekStart);

        Set<String> reversedIds = recEvents.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<RecognitionEventEntity> activeReceives = recEvents.stream()
                .filter(e -> "RECEIVE_APPRECIATION".equals(e.getReason()) && !reversedIds.contains(e.getId()))
                .toList();

        int activeReceivePoints = activeReceives.stream()
                .mapToInt(RecognitionEventEntity::getPointsDelta)
                .sum();

        boolean sameColleagueAlreadyRewarded = activeReceives.stream()
                .anyMatch(e -> appreciation.getActorId().equals(e.getCounterpartyUserId()));

        if (activeReceivePoints >= 25 || sameColleagueAlreadyRewarded) {
            return 0;
        }

        String sourceKey = "RECV:" + weekStart + ":" + recipientId + ":" + appreciation.getActorId() + ":" + appreciation.getId();
        if (eventRepository.findBySourceEventId(sourceKey).isPresent()) {
            return 0;
        }

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

    private void reconcileCappedAppreciationsForWeek(String weekStart, Set<String> affectedUsers) {
        List<AppreciationEntity> weekApps = appreciationRepository.findByWeekStartOrderByOccurredAtAscIdAsc(weekStart);
        Map<String, ContentItemEntity> contentMap = new HashMap<>();
        contentItemRepository.findAll().forEach(c -> contentMap.put(c.getId(), c));

        for (AppreciationEntity app : weekApps) {
            ContentItemEntity story = contentMap.get(app.getContentId());
            if (story == null || !"PUBLISHED".equals(story.getStatus()) || story.getPublishedVersion() == null) {
                continue;
            }
            String pubAud = story.getEffectivePublishedAudienceId();
            if (maybeAwardGiverPoints(app, pubAud) > 0) {
                affectedUsers.add(app.getActorId());
            }
            if (maybeAwardReceiverPoints(app, pubAud) > 0) {
                affectedUsers.add(app.getRecipientId());
            }
        }
    }

    @Transactional
    public void syncWeeklyBadges(String userId, String weekStart, String audienceId) {
        UserProgressSummary summary = computeUserProgressSummary(userId, weekStart, List.of(audienceId));
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

        List<WeeklyBadgeEntity> existing = badgeRepository.findByUserIdAndWeekStart(userId, weekStart).stream()
                .filter(b -> audienceId.equals(b.getAudienceId()))
                .toList();

        Set<String> existingCodes = new HashSet<>();
        for (WeeklyBadgeEntity b : existing) {
            if (!desiredBadges.containsKey(b.getBadgeCode())) {
                badgeRepository.delete(b);
            } else {
                existingCodes.add(b.getBadgeCode());
            }
        }
        badgeRepository.flush();

        for (Map.Entry<String, String[]> entry : desiredBadges.entrySet()) {
            if (!existingCodes.contains(entry.getKey())) {
                saveBadge(userId, weekStart, audienceId, entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
            }
        }
    }

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

    @Transactional(readOnly = true)
    public Map<String, Object> getPersonalProgress(UserEntity caller, String requestedWeek) {
        String weekStart = (requestedWeek == null || requestedWeek.isBlank())
                ? pulseClock.currentWeekStart()
                : requestedWeek.trim();
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        UserProgressSummary summary = computeUserProgressSummary(caller.getId(), weekStart, permittedAudiences);

        boolean showOnLeaderboard = preferenceRepository.findById(caller.getId())
                .map(RecognitionPreferenceEntity::isShowOnLeaderboard)
                .orElse(true);

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

    @Transactional
    public Map<String, Object> updatePreferences(UserEntity caller, boolean showOnLeaderboard) {
        RecognitionPreferenceEntity pref = preferenceRepository.findById(caller.getId())
                .orElseGet(() -> {
                    RecognitionPreferenceEntity p = new RecognitionPreferenceEntity();
                    p.setUserId(caller.getId());
                    return p;
                });
        pref.setShowOnLeaderboard(showOnLeaderboard);
        pref.setUpdatedAt(pulseClock.now());
        preferenceRepository.save(pref);
        return getPersonalProgress(caller, pulseClock.currentWeekStart());
    }

    /**
     * Computes the Top-5 opted-in Community Champions leaderboard for a single
     * authorized `audienceId` and `weekStart` (Monday 00:00 UTC).
     * Never leaks restricted-audience activity!
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLeaderboard(UserEntity caller, String requestedWeek, String audienceId) {
        String targetAudience = (audienceId == null || audienceId.isBlank()) ? "aud_pilot" : audienceId.trim();
        identityService.requireAudienceAccess(caller, targetAudience);

        String weekStart = (requestedWeek == null || requestedWeek.isBlank())
                ? pulseClock.currentWeekStart()
                : requestedWeek.trim();

        Map<String, Boolean> optInMap = new HashMap<>();
        preferenceRepository.findAll().forEach(p -> optInMap.put(p.getUserId(), p.isShowOnLeaderboard()));

        List<UserEntity> allUsers = userRepository.findAll();
        List<LeaderboardCandidate> candidates = new ArrayList<>();

        for (UserEntity user : allUsers) {
            List<String> userAuds = audienceRepository.findPermittedAudienceIdsByUserId(user.getId());
            if (!userAuds.contains(targetAudience)) {
                continue;
            }
            if (!optInMap.getOrDefault(user.getId(), true)) {
                continue;
            }
            // Compute progress strictly within targetAudience so restricted activity never leaks!
            UserProgressSummary summary = computeUserProgressSummary(user.getId(), weekStart, List.of(targetAudience));
            candidates.add(new LeaderboardCandidate(user, summary.totalPoints(), summary.badges()));
        }

        // Sort by points DESC, then alphabetical by displayName ASC
        candidates.sort((a, b) -> {
            int cmp = Integer.compare(b.points(), a.points());
            if (cmp != 0) return cmp;
            return a.user().getDisplayName().compareToIgnoreCase(b.user().getDisplayName());
        });

        // Top 5 with shared rank for ties
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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("weekStart", weekStart);
        response.put("timezone", "UTC");
        response.put("audienceId", targetAudience);
        response.put("entries", entries);
        return response;
    }

    private UserProgressSummary computeUserProgressSummary(String userId, String weekStart, List<String> allowedAudiences) {
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            return new UserProgressSummary(0, 0, 0, 0, 0, 0, 0, List.of());
        }

        List<RecognitionEventEntity> events = eventRepository
                .findByUserIdAndWeekStartOrderByCreatedAtAscIdAsc(userId, weekStart)
                .stream()
                .filter(e -> allowedAudiences.contains(e.getAudienceId()))
                .toList();

        Set<String> reversedIds = events.stream()
                .map(RecognitionEventEntity::getReversalOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

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

        int receivedDistinct = (int) receivedApps.stream()
                .map(AppreciationEntity::getActorId)
                .collect(Collectors.toSet())
                .size();

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

        int total = Math.min(60, contrib + givingPts + receivingPts);
        return new UserProgressSummary(total, contrib, givenCount, givingPts, receivedDistinct, receivingPts, distinctTeams, badges);
    }

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

    private record LeaderboardCandidate(UserEntity user, int points, List<String> badges) {}
}
