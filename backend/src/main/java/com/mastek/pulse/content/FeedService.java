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
 *          - Proposal Pages 4, 6, 10, 14).
 * Why it is designed this way:
 *   Implements the exact 3-stage pipeline from Proposal Page 4:
 *   1. FILTER ("Permission and capacity"): Queries ONLY `PUBLISHED` items whose
 *      `audience_id` is in the caller's permitted audiences. Restricted items
 *      (like `story_08` in `aud_uk_restricted`) are filtered out BEFORE ranking!
 *   2. RANK ("Interests and intent"): Scores items deterministically based on
 *      matching explicit profile interests (`team learning`, `innovation`, `culture`)
 *      and whether the story has an OPEN invitation with remaining capacity.
 *   3. EXPLAIN ("Verified reasons only"): Builds a factual explanation grounded
 *      strictly in real profile fields and host capacity (e.g., "WHY THIS? Your
 *      interest: team learning · 15 min conversation / 2 places available") —
 *      never an invented percentage compatibility score!
 * ============================================================================
 */
@Service
public class FeedService {

    private final ContentItemRepository contentItemRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final AudienceRepository audienceRepository;
    private final IdentityService identityService;

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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPersonalizedFeed(UserEntity caller, String category, String query) {
        // STEP 1: FILTER BY AUDIENCE PERMISSIONS FIRST ("Audience filter before rank")
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        List<ContentItemEntity> publishedItems = contentItemRepository
                .findByAudienceIdInAndStatusOrderByPublishedAtDesc(permittedAudiences, "PUBLISHED");

        Map<String, AudienceEntity> audienceMap = new HashMap<>();
        audienceRepository.findAllById(permittedAudiences).forEach(a -> audienceMap.put(a.getId(), a));

        Map<String, UserEntity> authorMap = new HashMap<>();
        userRepository.findAll().forEach(u -> authorMap.put(u.getId(), u));

        List<String> userInterests = caller.getInterestsList().stream()
                .map(String::toLowerCase)
                .toList();

        List<ScoredCard> scoredCards = new ArrayList<>();

        for (ContentItemEntity item : publishedItems) {
            // Only STORIES and UPDATES appear in the main Pulse feed (Radio & Culture Cast are in Listen)
            if (!"STORY".equals(item.getContentType()) && !"UPDATE".equals(item.getContentType())) {
                continue;
            }
            if ("UPDATES".equalsIgnoreCase(category) && !"UPDATE".equals(item.getContentType())) {
                continue;
            }
            if (("LEARNING".equalsIgnoreCase(category) || "COMMUNITY".equalsIgnoreCase(category))
                    && !"STORY".equals(item.getContentType())) {
                continue;
            }

            int versionToRead = item.getPublishedVersion() != null ? item.getPublishedVersion() : item.getCurrentVersion();
            Optional<ContentRevisionEntity> revOpt = contentRevisionRepository
                    .findByContentIdAndVersionNumber(item.getId(), versionToRead);
            if (revOpt.isEmpty()) {
                continue;
            }
            ContentRevisionEntity rev = revOpt.get();

            // Optional category filter
            List<String> itemTags = rev.getTagsList().stream().map(String::toLowerCase).toList();
            if ("LEARNING".equalsIgnoreCase(category)
                    && !itemTags.contains("team learning") && !itemTags.contains("delivery excellence")) {
                continue;
            }
            if ("COMMUNITY".equalsIgnoreCase(category)
                    && !itemTags.contains("culture") && !itemTags.contains("inclusion") && !itemTags.contains("onboarding")) {
                continue;
            }

            // Optional search query filter
            if (query != null && !query.isBlank()) {
                String qLower = query.trim().toLowerCase();
                boolean matchesTitle = rev.getTitle().toLowerCase().contains(qLower);
                boolean matchesSummary = rev.getSummary().toLowerCase().contains(qLower);
                boolean matchesTags = itemTags.stream().anyMatch(t -> t.contains(qLower));
                if (!matchesTitle && !matchesSummary && !matchesTags) {
                    continue;
                }
            }

            // STEP 2 & 3: RANK BY EXPLICIT INTERESTS + CAPACITY, AND GENERATE FACTUAL EXPLANATION
            List<String> matchedInterests = itemTags.stream()
                    .filter(userInterests::contains)
                    .toList();

            Optional<InvitationEntity> invOpt = invitationRepository.findByStoryId(item.getId());
            UserEntity author = authorMap.get(item.getAuthorId());
            boolean hostPaused = author != null && author.isHostPaused();

            int score = matchedInterests.size() * 20;
            if (invOpt.isPresent() && invOpt.get().isOpenForRequests(hostPaused)) {
                score += 25;
                if (!item.getAuthorId().equals(caller.getId())) {
                    score += 10; // Boost cross-team stories with open invitation capacity
                }
            }

            String factualExplanation = buildFactualExplanation(matchedInterests, invOpt.orElse(null), hostPaused, item, rev);
            Map<String, Object> dto = toContentCardDto(item, rev, author, audienceMap.get(item.getAudienceId()), invOpt.orElse(null), factualExplanation);
            scoredCards.add(new ScoredCard(score, dto));
        }

        scoredCards.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return scoredCards.stream().map(ScoredCard::card).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStoryDetail(UserEntity caller, String storyId) {
        ContentItemEntity item = contentItemRepository.findById(storyId)
                .orElseThrow(() -> PulseException.notFound("404: Story not found: " + storyId));

        // Enforce strict Audience check first! Throws 403 if caller is not in item's audience.
        identityService.requireAudienceAccess(caller, item.getAudienceId());

        int versionToRead = item.getPublishedVersion() != null ? item.getPublishedVersion() : item.getCurrentVersion();
        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), versionToRead)
                .orElseThrow(() -> PulseException.notFound("404: Revision not found for story: " + storyId));

        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        AudienceEntity audience = audienceRepository.findById(item.getAudienceId()).orElse(null);
        InvitationEntity inv = invitationRepository.findByStoryId(item.getId()).orElse(null);

        List<String> userInterests = caller.getInterestsList().stream().map(String::toLowerCase).toList();
        List<String> matched = rev.getTagsList().stream().map(String::toLowerCase).filter(userInterests::contains).toList();
        boolean hostPaused = author != null && author.isHostPaused();

        String explanation = buildFactualExplanation(matched, inv, hostPaused, item, rev);
        return toContentCardDto(item, rev, author, audience, inv, explanation);
    }

    private String buildFactualExplanation(List<String> matchedInterests,
                                           InvitationEntity inv,
                                           boolean hostPaused,
                                           ContentItemEntity item,
                                           ContentRevisionEntity rev) {
        StringBuilder sb = new StringBuilder("WHY THIS? ");
        if (!matchedInterests.isEmpty()) {
            sb.append("Your interest: ").append(String.join(", ", matchedInterests));
        } else if ("UPDATE".equals(item.getContentType())) {
            sb.append("Approved company update for your audience");
        } else {
            sb.append("Shared with colleagues in your pilot audience");
        }

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

    public Map<String, Object> toContentCardDto(ContentItemEntity item,
                                                ContentRevisionEntity rev,
                                                UserEntity author,
                                                AudienceEntity audience,
                                                InvitationEntity inv,
                                                String factualExplanation) {
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
        map.put("audienceId", item.getAudienceId());
        map.put("audienceName", audience != null ? audience.getName() : item.getAudienceId());
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

    private record ScoredCard(int score, Map<String, Object> card) {}
}
