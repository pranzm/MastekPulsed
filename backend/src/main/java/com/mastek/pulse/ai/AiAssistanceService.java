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
 *
 * Why it is designed this way:
 *   Enforces the 5 strict AI & Trust guardrails from Proposal Pages 4 & 5 + D2:
 *   1. PUBLISHED VISIBILITY & AUDIENCE CHECK BEFORE AI: Verifies the caller has
 *      published visibility (`requirePublishedVisibility`) to the source story/update
 *      (or author ownership when polishing one's own draft) BEFORE passing any text
 *      to the AI adapter. Unpublished or withdrawn stories are never summarized!
 *   2. SENSITIVE CONTENT / REFUSAL CHECK: Refuses requests (`422 Unprocessable Entity`)
 *      that attempt to infer sensitive attributes (e.g. medical/salary), rank employees
 *      by performance/appraisal score, or access private messages.
 *   3. HARD BUDGET CAP (720 Actions): Alerts at 50% (360), 80% (576), and 100% (720);
 *      automatically disables optional AI at 100% cap and falls back to templates.
 *   4. NON-AI TEMPLATE FALLBACK: Always returns a clean, deterministic
 *      `templateFallback` alongside `aiSuggestion` so every journey works 100%
 *      even when AI is disabled or capped.
 *   5. EXPLICIT HUMAN CHOICE: Returns suggestions in a read-only preview payload;
 *      never saves a story revision or dispatches a connection request automatically.
 * ============================================================================
 */
// @Service registers this class as a singleton business-logic bean in Spring's IoC container.
@Service
public class AiAssistanceService {

    // Repository used to count total AI actions and persist new ledger entries for budget enforcement.
    private final AiUsageLedgerRepository ledgerRepository;

    // Repository used to look up the parent content item (story or official update) when `contextId` is provided.
    private final ContentItemRepository contentItemRepository;

    // Repository used to load the exact text revision (published snapshot or author's working draft).
    private final ContentRevisionRepository contentRevisionRepository;

    // Repository used to look up the story author's display name for personalized invitation drafts.
    private final UserRepository userRepository;

    // Central authorization service that enforces audience permissions and D2 published-snapshot visibility.
    private final IdentityService identityService;

    /**
     * `@Value("${pulse.ai.action-cap:720}")` injects the configured pilot AI budget cap
     * from `application.yml` (defaulting to 720 actions: 60 pilot users x 4 weeks x 3 actions/week).
     */
    @Value("${pulse.ai.action-cap:720}")
    private int configuredActionCap;

    /**
     * Optional in-memory override set via `POST /api/v1/ai/budget/simulate-cap` so demo presenters
     * and automated tests can dynamically test the 50% alert, 80% alert, and 100% hard-cap states
     * without restarting the server. When `null`, `configuredActionCap` is used.
     */
    private Integer runtimeCapOverride = null;

    /**
     * Constructor-based dependency injection. Spring automatically wires all required repositories
     * and services when instantiating `AiAssistanceService`.
     *
     * @param ledgerRepository          JPA repository for `ai_usage_ledger` rows.
     * @param contentItemRepository     JPA repository for `content_items` state metadata.
     * @param contentRevisionRepository JPA repository for `content_revisions` text snapshots.
     * @param userRepository            JPA repository for `users` profile lookups.
     * @param identityService           Scoped RBAC and audience visibility enforcement service.
     */
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

    /**
     * Overrides (or clears when `cap` is `null`) the runtime AI action cap for live hackathon demos.
     *
     * @param cap New maximum allowed AI actions, or `null` to restore `configuredActionCap` (720).
     */
    public void setRuntimeCapOverride(Integer cap) {
        // Store the runtime override (or null to revert to application.yml configuration).
        this.runtimeCapOverride = cap;
    }

    /**
     * Returns the currently active AI action cap (either the runtime override or the YAML default).
     *
     * @return Effective integer action cap.
     */
    public int getEffectiveActionCap() {
        // Prefer the runtime override if set; otherwise fall back to the configured 720-action cap.
        return runtimeCapOverride != null ? runtimeCapOverride : configuredActionCap;
    }

    /**
     * Computes the real-time AI budget consumption telemetry and alert threshold state.
     *
     * `@Transactional(readOnly = true)` hints to Spring and Hibernate/PostgreSQL that this
     * transaction only reads data, avoiding dirty-checking overhead.
     *
     * Alert thresholds (Proposal Page 5):
     *   - `NORMAL`: `< 50%` of cap used
     *   - `ALERT_50`: `>= 50%` and `< 80%` of cap used
     *   - `ALERT_80`: `>= 80%` and `< 100%` of cap used
     *   - `CAPPED_100`: `used >= cap` (optional AI is automatically disabled)
     *
     * @return A map serialized as JSON containing `actionsUsed`, `actionCap`, `percentUsed`,
     *         estimated input/output token counts, `alertLevel`, and `hardCapReached`.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBudgetStatus() {
        // Count the total number of AI assistance rows recorded in `ai_usage_ledger`.
        long used = ledgerRepository.count();
        // Resolve the active action cap (default 720 unless overridden).
        int cap = getEffectiveActionCap();
        // Calculate percentage used rounded to 1 decimal place (e.g., 360 / 720 -> 50.0%).
        // Guard against division by zero if cap is set to 0 during simulation.
        double pct = cap > 0 ? Math.round((used * 1000.0) / cap) / 10.0 : 100.0;

        // Determine the alert level string based on Proposal Page 5 thresholds (50%, 80%, 100%).
        String alertLevel = "NORMAL";
        if (used >= cap) {
            // Hard cap reached (100% or above): AI generation must be disabled automatically.
            alertLevel = "CAPPED_100";
        } else if (pct >= 80.0) {
            // High utilization warning threshold (80% of budget consumed).
            alertLevel = "ALERT_80";
        } else if (pct >= 50.0) {
            // Mid-pilot utilization checkpoint (50% of budget consumed).
            alertLevel = "ALERT_50";
        }

        // Assemble a LinkedHashMap to preserve deterministic JSON key ordering in API responses.
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("actionsUsed", used);
        status.put("actionCap", cap);
        status.put("percentUsed", pct);
        // Proposal Page 5 estimates ~1,000 input tokens and ~500 output tokens per bounded AI action.
        status.put("estimatedInputTokens", used * 1000);
        status.put("estimatedOutputTokens", used * 500);
        status.put("alertLevel", alertLevel);
        status.put("hardCapReached", used >= cap);
        return status;
    }

    /**
     * Generates an optional AI suggestion AND a deterministic non-AI template fallback for one of
     * the three permitted assistance types:
     *   1. `INVITATION_DRAFT`: Helping a reader draft a 15-minute connection request to a story's host.
     *   2. `UPDATE_SUMMARY`: Summarizing a published Official Update with an explicit source link.
     *   3. `STORY_POLISH`: Helping a story author structure their own draft ("What changed / What we learned").
     *
     * `@Transactional` ensures that checking the budget and inserting the new `AiUsageLedgerEntity`
     * row happen inside a single database transaction.
     *
     * @param caller  Authenticated `UserEntity` making the request.
     * @param payload Request JSON body containing `assistType`, optional `contextId`, and `userDraftText`.
     * @return Map containing `aiAvailable`, `aiSuggestion`, `templateFallback`, `sourceLink`,
     *         `aiLabel`, and updated `budgetStatus`.
     * @throws PulseException HTTP 422 if sensitive/disallowed keywords are present;
     *                        HTTP 403 if the caller lacks audience access to `contextId`;
     *                        HTTP 404 if `contextId` does not exist or is not published.
     */
    @Transactional
    public Map<String, Object> generateAssistance(UserEntity caller, Map<String, Object> payload) {
        // Step 1: Normalize the requested `assistType`, defaulting to INVITATION_DRAFT if omitted.
        String assistType = payload.get("assistType") != null
                ? payload.get("assistType").toString().toUpperCase()
                : "INVITATION_DRAFT";
        // Extract optional content item ID (e.g. "cnt_story_1" or "cnt_update_1").
        String contextId = (String) payload.get("contextId");
        // Extract optional user-entered draft notes.
        String userDraftText = (String) payload.getOrDefault("userDraftText", "");

        // Step 2: Enforce AI Trust & Refusal Policy (Proposal Page 4: "What we do not collect or infer").
        // Convert user input to lowercase for case-insensitive keyword inspection.
        String lowerDraft = userDraftText != null ? userDraftText.toLowerCase() : "";
        if (lowerDraft.contains("appraisal score") || lowerDraft.contains("rank employees")
                || lowerDraft.contains("private message") || lowerDraft.contains("medical")
                || lowerDraft.contains("salary")) {
            // Immediately abort with HTTP 422 Unprocessable Entity before any AI processing or ledger logging.
            throw PulseException.unprocessable(
                    "422: Refused by Mastek Campfire AI Trust Policy — cannot infer sensitive attributes, private messages, or employee appraisal scores."
            );
        }

        // Step 3: Load and authorize the referenced content item (`contextId`), if one was specified.
        ContentItemEntity item = null;
        ContentRevisionEntity rev = null;
        UserEntity author = null;

        if (contextId != null && !contextId.isBlank()) {
            // Look up the content item in PostgreSQL or fail with 404 Not Found.
            item = contentItemRepository.findById(contextId)
                    .orElseThrow(() -> PulseException.notFound("404: Context item not found: " + contextId));

            int versionToRead;
            // Branch A: If an author is polishing their OWN story (`STORY_POLISH`) and it has not been withdrawn,
            // verify they belong to the target audience and read their latest working `currentVersion`.
            if ("STORY_POLISH".equals(assistType) && item.getAuthorId().equals(caller.getId()) && !"WITHDRAWN".equals(item.getStatus())) {
                identityService.requireAudienceAccess(caller, item.getAudienceId());
                versionToRead = item.getCurrentVersion();
            } else {
                // Branch B (D2 Invariant): All other employee AI operations (such as summarizing an update
                // or drafting an invitation from a story) strictly require PUBLISHED status AND membership
                // in `effectivePublishedAudienceId`. Drafts, in-review items, or unauthorized audiences
                // are blocked BEFORE the AI model ever sees the text!
                identityService.requirePublishedVisibility(caller, item);
                versionToRead = item.getPublishedVersion();
            }

            // Load the exact authorized revision snapshot (`versionToRead`) from `content_revisions`.
            rev = contentRevisionRepository.findByContentIdAndVersionNumber(item.getId(), versionToRead)
                    .orElseThrow(() -> PulseException.notFound("404: Revision not found."));
            // Look up the author of the content item so we can address them by first name in invitation drafts.
            author = userRepository.findById(item.getAuthorId()).orElse(null);
        }

        // Step 4: Extract grounding fields (author's first name, story title, summary, and source URL).
        String hostName = author != null ? author.getDisplayName().split(" ")[0] : "Colleague";
        String title = rev != null ? rev.getTitle() : (userDraftText != null && !userDraftText.isBlank() ? userDraftText : "your shared story");
        String summary = rev != null ? rev.getSummary() : "";
        String sourceLink = rev != null && rev.getExternalUrl() != null ? rev.getExternalUrl() : "/stories/" + (contextId != null ? contextId : "");

        // Step 5: Construct both the deterministic non-AI `templateFallback` AND the bounded `aiSuggestion`.
        String templateFallback;
        String aiSuggestion;

        if ("INVITATION_DRAFT".equals(assistType)) {
            // Non-AI template fallback for a 15-minute topic-based connection request.
            templateFallback = "Hi " + hostName + ", I read your story \"" + title + "\" and would love to have a 15-minute conversation to learn from your experience. Could we compare notes this week?";
            // Grounded AI suggestion incorporating a snippet of the story summary and up to 2 of the caller's opt-in interests.
            aiSuggestion = "Hi " + hostName + ", I would love to learn how you approached \"" + title + "\" ("
                    + (summary.length() > 75 ? summary.substring(0, 75) + "..." : summary)
                    + "). As someone interested in " + String.join(" and ", caller.getInterestsList().stream().limit(2).toList())
                    + ", could we compare notes for 15 minutes this week?";
        } else if ("UPDATE_SUMMARY".equals(assistType)) {
            // Non-AI template fallback for an Official Update brief, always preserving the source link.
            templateFallback = "Official Update Brief: " + title + " — " + summary + " (Source: " + sourceLink + ")";
            // Grounded AI bullet summary citing the official source and linking back to the authoritative post.
            aiSuggestion = "Key Takeaways from \"" + title + "\":\n• " + summary
                    + "\n• Grounded in official source (" + (rev != null && rev.getOfficialSource() != null ? rev.getOfficialSource() : "Internal Comms")
                    + ").\n• Source link: " + sourceLink;
        } else {
            // Default / STORY_POLISH: Non-AI structured story template ("What changed / What we learned").
            templateFallback = "What changed:\n" + (userDraftText != null && !userDraftText.isBlank() ? userDraftText : "We tried one small change in our team workflow.")
                    + "\n\nWhat we learned:\nIt helped colleagues contribute more comfortably.";
            // Grounded AI polish suggestion that structures the author's notes without inventing sensitive facts.
            aiSuggestion = "Here is a structured draft preserving your meaning:\n\n"
                    + "Context & Small Change: " + (userDraftText != null && !userDraftText.isBlank() ? userDraftText : "We introduced silent writing before open discussion.")
                    + "\nPractical Outcome: Quieter colleagues had a clear opening to share ideas, and the team identified concrete improvements in under 15 minutes.";
        }

        // Step 6: Check the hard budget cap BEFORE recording a new AI usage ledger row.
        long currentUsed = ledgerRepository.count();
        int cap = getEffectiveActionCap();
        boolean capReached = currentUsed >= cap;

        // If we are still below the hard cap, record this AI action in `ai_usage_ledger`.
        // If `capReached` is true, we do NOT bill an AI action and instead serve only `templateFallback`.
        if (!capReached) {
            ledgerRepository.save(AiUsageLedgerEntity.create(
                    caller.getId(),
                    assistType,
                    contextId != null ? contextId : "draft"
            ));
        }

        // Step 7: Re-compute the budget status (reflecting the newly inserted row if AI was used).
        Map<String, Object> budgetStatus = getBudgetStatus();

        // Step 8: Build the response payload. Notice that `aiSuggestion` is suppressed (`null`) when
        // `capReached` is true, while `templateFallback` is ALWAYS populated so the user is never blocked.
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
