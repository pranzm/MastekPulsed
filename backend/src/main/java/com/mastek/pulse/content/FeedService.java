package com.mastek.pulse.content;

import com.mastek.pulse.common.*;
import com.mastek.pulse.connection.InvitationEntity;
import com.mastek.pulse.connection.InvitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: FeedService.java
 * Journey: Primary Journey Step 1 ("Discover a story -> understand why it is relevant"
 *          - Proposal Pages 4, 6, 10, 14 + Campfire CF-010 / D2).
 *
 * Why it is designed this way:
 *   Implements the 3-stage pipeline from Proposal Page 4 and D2 Visibility Policy:
 *   1. FILTER ("Permission and capacity"): Queries ONLY `PUBLISHED` items whose
 *      immutable `published_audience_id` is in the caller's permitted audiences.
 *      Draft audience edits on an unapproved replacement revision never broaden
 *      access to the previously published revision!
 *   2. PUBLISHED SNAPSHOT ONLY: `getStoryDetail` strictly requires `PUBLISHED`
 *      status and `publishedVersion != null` (via `identityService.requirePublishedVisibility`).
 *      Unpublished drafts or withdrawn stories throw `404` on employee detail reads,
 *      while owners and scoped editors can use `getContentPreview` (`GET /api/v1/content/{id}/preview`).
 *   3. RANK & EXPLAIN: Ranks by explicit interests + open capacity and builds
 *      a grounded factual explanation ("WHY THIS? Your interest: team learning").
 * ============================================================================
 */
// @Service registers this class as a Spring business service bean.
@Service
public class FeedService {

    // Repository for querying `content_items` filtered by audience and status.
    private final ContentItemRepository contentItemRepository;

    // Repository for loading the exact `publishedVersion` or `currentVersion` revision text.
    private final ContentRevisionRepository contentRevisionRepository;

    // Repository for looking up voluntary colleague conversation invitations attached to stories.
    private final InvitationRepository invitationRepository;

    // Repository for resolving story author profile metadata and host pause status.
    private final UserRepository userRepository;

    // Repository for resolving human-readable audience names (e.g., "Delivery & Engineering Pilot Cohort").
    private final AudienceRepository audienceRepository;

    // Service enforcing audience membership, published visibility, and preview visibility guardrails.
    private final IdentityService identityService;

    /**
     * Constructor injection of all repositories and services required by the feed pipeline.
     */
    public FeedService(ContentItemRepository contentItemRepository,
                       ContentRevisionRepository contentRevisionRepository,
                       InvitationRepository invitationRepository,
                       UserRepository userRepository,
                       AudienceRepository audienceRepository,
                       IdentityService identityService) {
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.audienceRepository = audienceRepository;
        this.identityService = identityService;
    }

    /**
     * Executes the 3-stage Discovery Feed pipeline (`Filter -> Rank -> Explain`) for the authenticated caller.
     *
     * @param caller   authenticated employee viewing the feed
     * @param category optional category tab (`ALL`, `LEARNING`, `COMMUNITY`, `UPDATES`)
     * @param query    optional keyword search filter
     * @return list of ranked content card DTO maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPersonalizedFeed(UserEntity caller, String category, String query) {
        // STAGE 1 (FILTER BY AUDIENCE FIRST): Resolve which audiences this caller belongs to.
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        if (permittedAudiences.isEmpty()) {
            // If the user has no permitted audiences, return an empty feed immediately.
            return List.of();
        }
        // Query PostgreSQL for `PUBLISHED` items whose effective published audience is in `permittedAudiences`.
        List<ContentItemEntity> publishedItems = contentItemRepository
                .findByAudienceIdInAndStatusOrderByPublishedAtDesc(permittedAudiences, "PUBLISHED");

        // Preload audience entities into a lookup map to avoid N+1 queries when building DTOs.
        Map<String, AudienceEntity> audienceMap = new HashMap<>();
        audienceRepository.findAllById(permittedAudiences).forEach(a -> audienceMap.put(a.getId(), a));

        // Preload author user entities into a lookup map.
        Map<String, UserEntity> authorMap = new HashMap<>();
        userRepository.findAll().forEach(u -> authorMap.put(u.getId(), u));

        // Normalize the caller's explicit profile interests to lowercase for case-insensitive tag matching.
        List<String> userInterests = caller.getInterestsList().stream()
                .map(String::toLowerCase)
                .toList();

        // Accumulator list of `(score, cardDto)` pairs for Stage 2 ranking.
        List<ScoredCard> scoredCards = new ArrayList<>();

        // Iterate through each candidate published item.
        for (ContentItemEntity item : publishedItems) {
            // Defensive check: ensure `publishedVersion` is non-null and status is `PUBLISHED`.
            if (item.getPublishedVersion() == null || !"PUBLISHED".equals(item.getStatus())) {
                continue;
            }
            // Verify that the immutable `EffectivePublishedAudienceId` is permitted for the caller.
            String pubAudienceId = item.getEffectivePublishedAudienceId();
            if (!permittedAudiences.contains(pubAudienceId)) {
                continue;
            }

            // The Discovery Feed displays written `STORY` and `UPDATE` items (audio episodes live in the Listen tab).
            if (!"STORY".equals(item.getContentType()) && !"UPDATE".equals(item.getContentType())) {
                continue;
            }
            // Apply optional category tab filters (`UPDATES`, `LEARNING`, `COMMUNITY`).
            if ("UPDATES".equalsIgnoreCase(category) && !"UPDATE".equals(item.getContentType())) {
                continue;
            }
            if (("LEARNING".equalsIgnoreCase(category) || "COMMUNITY".equalsIgnoreCase(category))
                    && !"STORY".equals(item.getContentType())) {
                continue;
            }

            // Load the exact `publishedVersion` snapshot (never `currentVersion`, which might be an unapproved draft edit!).
            Optional<ContentRevisionEntity> revOpt = contentRevisionRepository
                    .findByContentIdAndVersionNumber(item.getId(), item.getPublishedVersion());
            if (revOpt.isEmpty()) {
                continue;
            }
            ContentRevisionEntity rev = revOpt.get();

            // Normalize the revision's tags to lowercase and apply category tag filters if requested.
            List<String> itemTags = rev.getTagsList().stream().map(String::toLowerCase).toList();
            if ("LEARNING".equalsIgnoreCase(category)
                    && !itemTags.contains("team learning") && !itemTags.contains("delivery excellence")) {
                continue;
            }
            if ("COMMUNITY".equalsIgnoreCase(category)
                    && !itemTags.contains("culture") && !itemTags.contains("inclusion") && !itemTags.contains("onboarding")) {
                continue;
            }

            // Apply optional keyword search filter across title, summary, and tags.
            if (query != null && !query.isBlank()) {
                String qLower = query.trim().toLowerCase();
                boolean matchesTitle = rev.getTitle().toLowerCase().contains(qLower);
                boolean matchesSummary = rev.getSummary().toLowerCase().contains(qLower);
                boolean matchesTags = itemTags.stream().anyMatch(t -> t.contains(qLower));
                if (!matchesTitle && !matchesSummary && !matchesTags) {
                    continue;
                }
            }

            // STAGE 2 (RANK BY EXPLICIT INTERESTS & OPEN CAPACITY):
            // Find which of the item's tags match the caller's explicit profile interests.
            List<String> matchedInterests = itemTags.stream()
                    .filter(userInterests::contains)
                    .toList();

            // Look up if this story has an optional colleague invitation and whether the host is available.
            Optional<InvitationEntity> invOpt = invitationRepository.findByStoryId(item.getId());
            UserEntity author = authorMap.get(item.getAuthorId());
            boolean hostPaused = author != null && author.isHostPaused();

            // Calculate relevance score: +20 per matched interest, +25 if an open invitation is available, +10 if hosted by another colleague.
            int score = matchedInterests.size() * 20;
            if (invOpt.isPresent() && invOpt.get().isOpenForRequests(hostPaused)) {
                score += 25;
                if (!item.getAuthorId().equals(caller.getId())) {
                    score += 10;
                }
            }

            // STAGE 3 (EXPLAIN WITH GROUNDED FACTS):
            // Build the transparent `"WHY THIS?"` explanation string from factual database fields.
            String factualExplanation = buildFactualExplanation(matchedInterests, invOpt.orElse(null), hostPaused, item, rev);
            Map<String, Object> dto = toContentCardDto(item, rev, author, audienceMap.get(pubAudienceId), invOpt.orElse(null), factualExplanation);
            scoredCards.add(new ScoredCard(score, dto));
        }

        // Sort cards descending by computed relevance score (ties preserve `publishedAt DESC` order from SQL).
        scoredCards.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return scoredCards.stream().map(ScoredCard::card).toList();
    }

    /**
     * Employee detail read: strictly requires `PUBLISHED` status and reads ONLY
     * `item.getPublishedVersion()` and `item.getEffectivePublishedAudienceId()`.
     * Never falls back to `currentVersion`!
     *
     * @param caller  authenticated employee requesting the story
     * @param storyId ID of the story
     * @return published story card DTO map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStoryDetail(UserEntity caller, String storyId) {
        // Step 1: Look up the content item or throw 404 Not Found.
        ContentItemEntity item = contentItemRepository.findById(storyId)
                .orElseThrow(() -> PulseException.notFound("404: Story not found: " + storyId));

        // Step 2: Enforce published visibility (throws 404 if not PUBLISHED, or 403 if caller lacks access to `publishedAudienceId`).
        identityService.requirePublishedVisibility(caller, item);

        // Step 3: Load the exact `publishedVersion` revision snapshot.
        int publishedVer = item.getPublishedVersion();
        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), publishedVer)
                .orElseThrow(() -> PulseException.notFound("404: Published revision not found for story: " + storyId));

        // Step 4: Load author, published audience, and optional invitation metadata.
        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        String pubAudienceId = item.getEffectivePublishedAudienceId();
        AudienceEntity audience = audienceRepository.findById(pubAudienceId).orElse(null);
        InvitationEntity inv = invitationRepository.findByStoryId(item.getId()).orElse(null);

        // Step 5: Compute matched interests and build the grounded `"WHY THIS?"` explanation.
        List<String> userInterests = caller.getInterestsList().stream().map(String::toLowerCase).toList();
        List<String> matched = rev.getTagsList().stream().map(String::toLowerCase).filter(userInterests::contains).toList();
        boolean hostPaused = author != null && author.isHostPaused();

        String explanation = buildFactualExplanation(matched, inv, hostPaused, item, rev);
        return toContentCardDto(item, rev, author, audience, inv, explanation);
    }

    /**
     * Distinct owner / scoped editor preview policy for unpublished drafts or revisions in review.
     * Reads `item.getCurrentVersion()` after verifying `identityService.requirePreviewVisibility(caller, item)`.
     *
     * @param caller    authenticated author or scoped portal editor
     * @param contentId ID of the content item to preview
     * @return preview card DTO map for `currentVersion`
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContentPreview(UserEntity caller, String contentId) {
        // Step 1: Fetch the content item or throw 404 Not Found.
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        // Step 2: Verify the caller is either the item's author or a scoped portal editor for `item.getAudienceId()`.
        identityService.requirePreviewVisibility(caller, item);

        // Step 3: Load the latest working revision (`currentVersion`) so the author/editor can inspect unapproved changes.
        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Revision not found: " + contentId));

        // Step 4: Load author, working audience, and optional invitation, and return the preview DTO.
        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        AudienceEntity audience = audienceRepository.findById(item.getAudienceId()).orElse(null);
        InvitationEntity inv = invitationRepository.findByStoryId(item.getId()).orElse(null);
        return toContentCardDto(item, rev, author, audience, inv, "Owner / Editorial Preview (Revision " + rev.getVersionNumber() + ")");
    }

    /**
     * Builds the factual `"WHY THIS?"` line required by Proposal Page 4 & Page 6.
     * Grounded strictly in matched user interests, audience membership, and live invitation capacity.
     */
    private String buildFactualExplanation(List<String> matchedInterests,
                                           InvitationEntity inv,
                                           boolean hostPaused,
                                           ContentItemEntity item,
                                           ContentRevisionEntity rev) {
        StringBuilder sb = new StringBuilder("WHY THIS? ");
        // Part 1: Explain why the story/update is relevant to the employee.
        if (!matchedInterests.isEmpty()) {
            sb.append("Your interest: ").append(String.join(", ", matchedInterests));
        } else if ("UPDATE".equals(item.getContentType())) {
            sb.append("Approved company update for your audience");
        } else {
            sb.append("Shared with colleagues in your pilot audience");
        }

        // Part 2: If the story has an invitation offer, explain its live status and remaining places.
        if (inv != null) {
            String status = inv.computeStatusLabel(hostPaused);
            if ("OPEN".equals(status)) {
                sb.append(" · ").append(inv.getDurationMinutes()).append(" min conversation / ")
                  .append(inv.getRemainingPlaces()).append(" places available");
            } else if ("FULL".equals(status)) {
                sb.append(" · Invitation full (").append(inv.getCapacity()).append(" places filled)");
            } else if ("PAUSED".equals(status)) {
                sb.append(" · Host has paused new invitations this week");
            } else {
                sb.append(" · Invitation expired");
            }
        }
        return sb.toString();
    }

    /**
     * Maps a {@link ContentItemEntity}, its {@link ContentRevisionEntity}, author, audience, and optional
     * {@link InvitationEntity} into the standard JSON card DTO consumed by Employee Web and Mobile.
     */
    public Map<String, Object> toContentCardDto(ContentItemEntity item,
                                                ContentRevisionEntity rev,
                                                UserEntity author,
                                                AudienceEntity audience,
                                                InvitationEntity inv,
                                                String factualExplanation) {
        // If we are rendering the live published revision, expose `EffectivePublishedAudienceId`; otherwise expose the working `audienceId`.
        String effectiveAudienceId = "PUBLISHED".equals(item.getStatus()) && rev.getVersionNumber() == (item.getPublishedVersion() != null ? item.getPublishedVersion() : -1)
                ? item.getEffectivePublishedAudienceId()
                : item.getAudienceId();

        // Populate core story/update metadata and revision snapshot fields.
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("contentType", item.getContentType());
        map.put("title", rev.getTitle());
        map.put("summary", rev.getSummary());
        map.put("bodyText", rev.getBodyText());
        map.put("officialSource", rev.getOfficialSource());
        map.put("externalUrl", rev.getExternalUrl());
        map.put("authorId", item.getAuthorId());
        map.put("authorName", author != null ? author.getDisplayName() : item.getAuthorId());
        map.put("authorFunction", author != null ? author.getFunctionName() : "Colleague");
        map.put("authorRegion", author != null ? author.getRegion() : "GLOBAL");
        map.put("audienceId", effectiveAudienceId);
        map.put("audienceName", audience != null ? audience.getName() : effectiveAudienceId);
        map.put("tags", rev.getTagsList());
        map.put("status", item.getStatus());
        map.put("currentVersion", item.getCurrentVersion());
        map.put("publishedVersion", item.getPublishedVersion());
        map.put("publishedAt", item.getPublishedAt() != null ? item.getPublishedAt().toString() : null);
        map.put("factualExplanation", factualExplanation);
        map.put("hasAudio", rev.isHasAudio());
        map.put("durationSeconds", rev.getDurationSeconds());
        map.put("transcriptText", rev.getTranscriptText());
        map.put("signedAudioUrl", rev.isHasAudio()
                ? "/api/v1/media/stream/" + item.getId() + "?sig=signed-demo-token&exp=900"
                : null);

        // Attach the voluntary colleague invitation sub-object if present.
        if (inv != null) {
            boolean hostPaused = author != null && author.isHostPaused();
            Map<String, Object> invMap = new LinkedHashMap<>();
            invMap.put("id", inv.getId());
            invMap.put("hostId", inv.getHostId());
            invMap.put("hostName", author != null ? author.getDisplayName() : inv.getHostId());
            invMap.put("hostRegion", author != null ? author.getRegion() : "GLOBAL");
            invMap.put("hostFunction", author != null ? author.getFunctionName() : "Colleague");
            invMap.put("topicLabel", inv.getTopicLabel());
            invMap.put("durationMinutes", inv.getDurationMinutes());
            invMap.put("capacity", inv.getCapacity());
            invMap.put("acceptedCount", inv.getAcceptedCount());
            invMap.put("remainingPlaces", inv.getRemainingPlaces());
            invMap.put("paused", inv.isPaused() || hostPaused);
            invMap.put("expiresAt", inv.getExpiresAt().toString());
            invMap.put("statusLabel", inv.computeStatusLabel(hostPaused));
            map.put("invitation", invMap);
        } else {
            map.put("invitation", null);
        }
        return map;
    }

    /**
     * Internal value record pairing a numeric relevance score with a serialized content card DTO.
     */
    private record ScoredCard(int score, Map<String, Object> card) {}
}
