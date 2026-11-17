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
        List<String> permittedAudiences = identityService.getPermittedAudiences(caller.getId());
        if (permittedAudiences.isEmpty()) {
            return List.of();
        }
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
            if (item.getPublishedVersion() == null || !"PUBLISHED".equals(item.getStatus())) {
                continue;
            }
            String pubAudienceId = item.getEffectivePublishedAudienceId();
            if (!permittedAudiences.contains(pubAudienceId)) {
                continue;
            }

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

            Optional<ContentRevisionEntity> revOpt = contentRevisionRepository
                    .findByContentIdAndVersionNumber(item.getId(), item.getPublishedVersion());
            if (revOpt.isEmpty()) {
                continue;
            }
            ContentRevisionEntity rev = revOpt.get();

            List<String> itemTags = rev.getTagsList().stream().map(String::toLowerCase).toList();
            if ("LEARNING".equalsIgnoreCase(category)
                    && !itemTags.contains("team learning") && !itemTags.contains("delivery excellence")) {
                continue;
            }
            if ("COMMUNITY".equalsIgnoreCase(category)
                    && !itemTags.contains("culture") && !itemTags.contains("inclusion") && !itemTags.contains("onboarding")) {
                continue;
            }

            if (query != null && !query.isBlank()) {
                String qLower = query.trim().toLowerCase();
                boolean matchesTitle = rev.getTitle().toLowerCase().contains(qLower);
                boolean matchesSummary = rev.getSummary().toLowerCase().contains(qLower);
                boolean matchesTags = itemTags.stream().anyMatch(t -> t.contains(qLower));
                if (!matchesTitle && !matchesSummary && !matchesTags) {
                    continue;
                }
            }

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
                    score += 10;
                }
            }

            String factualExplanation = buildFactualExplanation(matchedInterests, invOpt.orElse(null), hostPaused, item, rev);
            Map<String, Object> dto = toContentCardDto(item, rev, author, audienceMap.get(pubAudienceId), invOpt.orElse(null), factualExplanation);
            scoredCards.add(new ScoredCard(score, dto));
        }

        scoredCards.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return scoredCards.stream().map(ScoredCard::card).toList();
    }

    /**
     * Employee detail read: strictly requires PUBLISHED status and reads ONLY
     * `item.getPublishedVersion()` and `item.getEffectivePublishedAudienceId()`.
     * Never falls back to `currentVersion`!
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStoryDetail(UserEntity caller, String storyId) {
        ContentItemEntity item = contentItemRepository.findById(storyId)
                .orElseThrow(() -> PulseException.notFound("404: Story not found: " + storyId));

        identityService.requirePublishedVisibility(caller, item);

        int publishedVer = item.getPublishedVersion();
        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), publishedVer)
                .orElseThrow(() -> PulseException.notFound("404: Published revision not found for story: " + storyId));

        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        String pubAudienceId = item.getEffectivePublishedAudienceId();
        AudienceEntity audience = audienceRepository.findById(pubAudienceId).orElse(null);
        InvitationEntity inv = invitationRepository.findByStoryId(item.getId()).orElse(null);

        List<String> userInterests = caller.getInterestsList().stream().map(String::toLowerCase).toList();
        List<String> matched = rev.getTagsList().stream().map(String::toLowerCase).filter(userInterests::contains).toList();
        boolean hostPaused = author != null && author.isHostPaused();

        String explanation = buildFactualExplanation(matched, inv, hostPaused, item, rev);
        return toContentCardDto(item, rev, author, audience, inv, explanation);
    }

    /**
     * Distinct owner / scoped editor preview policy for unpublished drafts or revisions in review.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContentPreview(UserEntity caller, String contentId) {
        ContentItemEntity item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> PulseException.notFound("404: Content item not found: " + contentId));

        identityService.requirePreviewVisibility(caller, item);

        ContentRevisionEntity rev = contentRevisionRepository
                .findByContentIdAndVersionNumber(item.getId(), item.getCurrentVersion())
                .orElseThrow(() -> PulseException.notFound("404: Revision not found: " + contentId));

        UserEntity author = userRepository.findById(item.getAuthorId()).orElse(null);
        AudienceEntity audience = audienceRepository.findById(item.getAudienceId()).orElse(null);
        InvitationEntity inv = invitationRepository.findByStoryId(item.getId()).orElse(null);
        return toContentCardDto(item, rev, author, audience, inv, "Owner / Editorial Preview (Revision " + rev.getVersionNumber() + ")");
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
        String effectiveAudienceId = "PUBLISHED".equals(item.getStatus()) && rev.getVersionNumber() == (item.getPublishedVersion() != null ? item.getPublishedVersion() : -1)
                ? item.getEffectivePublishedAudienceId()
                : item.getAudienceId();

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
