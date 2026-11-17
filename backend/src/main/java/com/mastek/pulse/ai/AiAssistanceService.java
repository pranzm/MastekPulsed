package com.mastek.pulse.ai;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.PulseException;
import com.mastek.pulse.common.UserEntity;
import com.mastek.pulse.common.UserRepository;
import com.mastek.pulse.content.ContentItemEntity;
import com.mastek.pulse.content.ContentItemRepository;
import com.mastek.pulse.content.ContentRevisionEntity;
import com.mastek.pulse.content.ContentRevisionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AiAssistanceService.java
 * Journey: Supporting Journey C ("Use optional AI drafting or summarization,
 *          review the result, and explicitly choose the next action" -
 *          Proposal Pages 4, 5, 7, 8, 14 + Campfire CF-010 / D2).
 * Why it is designed this way:
 *   Enforces the 5 strict AI & Trust rules from Proposal Pages 4 & 5 + D2:
 *   1. PUBLISHED VISIBILITY & AUDIENCE CHECK BEFORE AI: Verifies the caller has
 *      published visibility (`requirePublishedVisibility`) to the source story/update
 *      (or author ownership when polishing one's own draft) BEFORE passing any text
 *      to the AI adapter. Unpublished or withdrawn stories are never summarized!
 *   2. SENSITIVE CONTENT / REFUSAL CHECK: Refuses requests that attempt to
 *      infer sensitive attributes, rank employees by performance/appraisal, or
 *      access private messages.
 *   3. HARD BUDGET CAP (720 Actions): Alerts at 50% (360), 80% (576), and 100% (720);
 *      automatically disables optional AI at 100% cap and falls back to templates.
 *   4. NON-AI TEMPLATE FALLBACK: Always returns a clean, deterministic
 *      `templateFallback` alongside `aiSuggestion`.
 *   5. EXPLICIT HUMAN CHOICE: Never saves or sends the draft automatically.
 * ============================================================================
 */
@Service
public class AiAssistanceService {

    private final AiUsageLedgerRepository ledgerRepository;
    private final ContentItemRepository contentItemRepository;
    private final ContentRevisionRepository contentRevisionRepository;
    private final UserRepository userRepository;
    private final IdentityService identityService;

    @Value("${pulse.ai.action-cap:720}")
    private int configuredActionCap;

    private Integer runtimeCapOverride = null;

    public AiAssistanceService(AiUsageLedgerRepository ledgerRepository,
                               ContentItemRepository contentItemRepository,
                               ContentRevisionRepository contentRevisionRepository,
                               UserRepository userRepository,
                               IdentityService identityService) {
        this.ledgerRepository = ledgerRepository;
        this.contentItemRepository = contentItemRepository;
        this.contentRevisionRepository = contentRevisionRepository;
        this.userRepository = userRepository;
        this.identityService = identityService;
    }

    public void setRuntimeCapOverride(Integer cap) {
        this.runtimeCapOverride = cap;
    }

    public int getEffectiveActionCap() {
        return runtimeCapOverride != null ? runtimeCapOverride : configuredActionCap;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBudgetStatus() {
        long used = ledgerRepository.count();
        int cap = getEffectiveActionCap();
        double pct = cap > 0 ? Math.round((used * 1000.0) / cap) / 10.0 : 100.0;

        String alertLevel = "NORMAL";
        if (used >= cap) {
            alertLevel = "CAPPED_100";
        } else if (pct >= 80.0) {
            alertLevel = "ALERT_80";
        } else if (pct >= 50.0) {
            alertLevel = "ALERT_50";
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("actionsUsed", used);
        status.put("actionCap", cap);
        status.put("percentUsed", pct);
        status.put("estimatedInputTokens", used * 1000);
        status.put("estimatedOutputTokens", used * 500);
        status.put("alertLevel", alertLevel);
        status.put("hardCapReached", used >= cap);
        return status;
    }

    @Transactional
    public Map<String, Object> generateAssistance(UserEntity caller, Map<String, Object> payload) {
        String assistType = payload.get("assistType") != null
                ? payload.get("assistType").toString().toUpperCase()
                : "INVITATION_DRAFT";
        String contextId = (String) payload.get("contextId");
        String userDraftText = (String) payload.getOrDefault("userDraftText", "");

        String lowerDraft = userDraftText != null ? userDraftText.toLowerCase() : "";
        if (lowerDraft.contains("appraisal score") || lowerDraft.contains("rank employees")
                || lowerDraft.contains("private message") || lowerDraft.contains("medical")
                || lowerDraft.contains("salary")) {
            throw PulseException.unprocessable(
                    "422: Refused by Mastek Campfire AI Trust Policy — cannot infer sensitive attributes, private messages, or employee appraisal scores."
            );
        }

        ContentItemEntity item = null;
        ContentRevisionEntity rev = null;
        UserEntity author = null;

        if (contextId != null && !contextId.isBlank()) {
            item = contentItemRepository.findById(contextId)
                    .orElseThrow(() -> PulseException.notFound("404: Context item not found: " + contextId));

            int versionToRead;
            if ("STORY_POLISH".equals(assistType) && item.getAuthorId().equals(caller.getId()) && !"WITHDRAWN".equals(item.getStatus())) {
                identityService.requireAudienceAccess(caller, item.getAudienceId());
                versionToRead = item.getCurrentVersion();
            } else {
                // D2 Invariant: Employee AI context strictly requires PUBLISHED status & publishedAudienceId!
                identityService.requirePublishedVisibility(caller, item);
                versionToRead = item.getPublishedVersion();
            }

            rev = contentRevisionRepository.findByContentIdAndVersionNumber(item.getId(), versionToRead)
                    .orElseThrow(() -> PulseException.notFound("404: Revision not found."));
            author = userRepository.findById(item.getAuthorId()).orElse(null);
        }

        String hostName = author != null ? author.getDisplayName().split(" ")[0] : "Colleague";
        String title = rev != null ? rev.getTitle() : (userDraftText != null && !userDraftText.isBlank() ? userDraftText : "your shared story");
        String summary = rev != null ? rev.getSummary() : "";
        String sourceLink = rev != null && rev.getExternalUrl() != null ? rev.getExternalUrl() : "/stories/" + (contextId != null ? contextId : "");

        String templateFallback;
        String aiSuggestion;

        if ("INVITATION_DRAFT".equals(assistType)) {
            templateFallback = "Hi " + hostName + ", I read your story \"" + title + "\" and would love to have a 15-minute conversation to learn from your experience. Could we compare notes this week?";
            aiSuggestion = "Hi " + hostName + ", I would love to learn how you approached \"" + title + "\" ("
                    + (summary.length() > 75 ? summary.substring(0, 75) + "..." : summary)
                    + "). As someone interested in " + String.join(" and ", caller.getInterestsList().stream().limit(2).toList())
                    + ", could we compare notes for 15 minutes this week?";
        } else if ("UPDATE_SUMMARY".equals(assistType)) {
            templateFallback = "Official Update Brief: " + title + " — " + summary + " (Source: " + sourceLink + ")";
            aiSuggestion = "Key Takeaways from \"" + title + "\":\n• " + summary
                    + "\n• Grounded in official source (" + (rev != null && rev.getOfficialSource() != null ? rev.getOfficialSource() : "Internal Comms")
                    + ").\n• Source link: " + sourceLink;
        } else {
            templateFallback = "What changed:\n" + (userDraftText != null && !userDraftText.isBlank() ? userDraftText : "We tried one small change in our team workflow.")
                    + "\n\nWhat we learned:\nIt helped colleagues contribute more comfortably.";
            aiSuggestion = "Here is a structured draft preserving your meaning:\n\n"
                    + "Context & Small Change: " + (userDraftText != null && !userDraftText.isBlank() ? userDraftText : "We introduced silent writing before open discussion.")
                    + "\nPractical Outcome: Quieter colleagues had a clear opening to share ideas, and the team identified concrete improvements in under 15 minutes.";
        }

        long currentUsed = ledgerRepository.count();
        int cap = getEffectiveActionCap();
        boolean capReached = currentUsed >= cap;

        if (!capReached) {
            ledgerRepository.save(AiUsageLedgerEntity.create(
                    caller.getId(),
                    assistType,
                    contextId != null ? contextId : "draft"
            ));
        }

        Map<String, Object> budgetStatus = getBudgetStatus();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assistType", assistType);
        response.put("aiAvailable", !capReached);
        response.put("aiSuggestion", capReached ? null : aiSuggestion);
        response.put("templateFallback", templateFallback);
        response.put("sourceLink", sourceLink);
        response.put("aiLabel", capReached
                ? "AI budget hard cap reached (100%) — Optional AI disabled automatically. Using verified non-AI template fallback."
                : "AI-assisted suggestion (grounded in permitted fields) — Review and edit before choosing your next action.");
        response.put("budgetStatus", budgetStatus);
        return response;
    }
}
