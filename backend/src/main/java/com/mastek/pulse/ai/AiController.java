package com.mastek.pulse.ai;

import com.mastek.pulse.common.IdentityService;
import com.mastek.pulse.common.UserEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AiController.java
 * Journey: Supporting Journey C ("Use optional AI drafting or summarization,
 *          review the result, and explicitly choose the next action" -
 *          Proposal Pages 4, 5, 7, 8).
 *
 * Why it is designed this way:
 *   Exposes the REST endpoints under `/api/v1/ai` for optional, bounded AI assistance
 *   and real-time budget governance:
 *   - `POST /api/v1/ai/assist` (plus aliases `/drafts` and `/summaries`): Generates
 *     a grounded AI suggestion alongside a deterministic non-AI `templateFallback`.
 *   - `GET /api/v1/ai/budget`: Returns current usage against the 720-action pilot cap
 *     and current alert level (`NORMAL`, `ALERT_50`, `ALERT_80`, `CAPPED_100`).
 *   - `POST /api/v1/ai/budget/simulate-cap`: Allows Publishing Portal admins and judges
 *     to dynamically simulate hitting the 50%, 80%, or 100% hard cap live during the
 *     Hackathon and observe automatic fallback to non-AI templates.
 *
 * Key Annotations Explained for Interns:
 *   - `@RestController`: Combines `@Controller` and `@ResponseBody`, meaning every
 *     method return value (`Map<String, Object>`) is automatically serialized to JSON
 *     by Jackson and written to the HTTP response body.
 *   - `@RequestMapping("/api/v1/ai")`: Prefixes all endpoint paths in this controller
 *     with `/api/v1/ai`.
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    // Service responsible for authenticating `X-Pulse-User-Id` and checking audience/role access.
    private final IdentityService identityService;

    // Service containing the core AI trust rules, refusal checks, budget cap logic, and template fallbacks.
    private final AiAssistanceService aiAssistanceService;

    /**
     * Constructor injection for controller dependencies.
     *
     * @param identityService     Authentication and authorization helper.
     * @param aiAssistanceService Core AI drafting, summarization, and budget cap service.
     */
    public AiController(IdentityService identityService, AiAssistanceService aiAssistanceService) {
        this.identityService = identityService;
        this.aiAssistanceService = aiAssistanceService;
    }

    /**
     * Generates an optional AI suggestion and a guaranteed non-AI template fallback.
     * Mapped to `/api/v1/ai/assist`, `/api/v1/ai/drafts`, and `/api/v1/ai/summaries`
     * so all client journeys (story polish, invitation draft, official update summary)
     * can call a clean semantic endpoint.
     *
     * HTTP Status Codes:
     *   - `200 OK`: Suggestion and fallback generated (or fallback-only if hard cap reached).
     *   - `401 Unauthorized`: Missing or invalid `X-Pulse-User-Id` header.
     *   - `403 Forbidden`: Caller is not a member of the referenced content item's audience.
     *   - `404 Not Found`: Referenced `contextId` does not exist or is not published.
     *   - `422 Unprocessable Entity`: Request violated the AI Trust Policy (e.g. asked for
     *     appraisal scores, salary, medical data, or private messages).
     *
     * @param userIdHeader Optional `X-Pulse-User-Id` HTTP header identifying the caller persona.
     * @param payload      JSON body containing `assistType`, `contextId`, and `userDraftText`.
     * @return JSON map with `aiSuggestion`, `templateFallback`, `aiAvailable`, and `budgetStatus`.
     */
    @PostMapping({"/assist", "/drafts", "/summaries"})
    public Map<String, Object> requestAiAssistance(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        // Authenticate the caller from the X-Pulse-User-Id header (throws 401 if invalid).
        UserEntity caller = identityService.authenticate(userIdHeader);
        // Delegate permission checks, refusal checks, budget ledger logging, and text generation to the service.
        return aiAssistanceService.generateAssistance(caller, payload);
    }

    /**
     * Returns current AI budget usage metrics (`actionsUsed`, `actionCap`, `percentUsed`,
     * `alertLevel`, `hardCapReached`) for the Publishing Portal governance dashboard.
     *
     * HTTP Status Codes:
     *   - `200 OK`: Returns current budget telemetry.
     *   - `401 Unauthorized`: Invalid caller identity.
     *
     * @param userIdHeader Optional `X-Pulse-User-Id` HTTP header identifying the caller persona.
     * @return JSON map representing current AI budget consumption.
     */
    @GetMapping("/budget")
    public Map<String, Object> getBudgetStatus(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        // Verify that the request comes from an authenticated user before returning operational metrics.
        identityService.authenticate(userIdHeader);
        // Query the ledger count and compute alert thresholds.
        return aiAssistanceService.getBudgetStatus();
    }

    /**
     * Dynamically overrides the runtime AI action cap so reviewers, admins, or automated tests
     * can simulate reaching 50%, 80%, or 100% of the AI budget cap without inserting 720 rows.
     * Passing `{"actionCap": null}` resets the cap back to the configured default (720).
     *
     * HTTP Status Codes:
     *   - `200 OK`: Cap override applied and updated budget status returned.
     *   - `401 Unauthorized`: Invalid caller identity.
     *
     * @param userIdHeader Optional `X-Pulse-User-Id` HTTP header identifying the caller persona.
     * @param payload      JSON body containing `actionCap` (Integer or null).
     * @return Updated budget status JSON map reflecting the new cap immediately.
     */
    @PostMapping("/budget/simulate-cap")
    public Map<String, Object> simulateCap(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        // Ensure caller is authenticated.
        identityService.authenticate(userIdHeader);
        // Safely extract `actionCap` if it is a JSON number, or null if omitted/reset.
        Integer cap = payload.get("actionCap") instanceof Number n ? n.intValue() : null;
        // Apply the runtime override in AiAssistanceService.
        aiAssistanceService.setRuntimeCapOverride(cap);
        // Return the recalculated budget status so the UI updates immediately.
        return aiAssistanceService.getBudgetStatus();
    }
}
