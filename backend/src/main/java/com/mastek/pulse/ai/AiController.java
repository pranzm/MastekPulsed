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
 * Why it is designed this way:
 *   Exposes `/api/v1/ai/assist` (and `/api/v1/ai/drafts`), `/api/v1/ai/budget`,
 *   and `/api/v1/ai/budget/simulate-cap` (so portal admins can demonstrate the
 *   50%, 80%, and 100% hard-cap automatic fallback behavior live during the Hackathon!).
 * ============================================================================
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final IdentityService identityService;
    private final AiAssistanceService aiAssistanceService;

    public AiController(IdentityService identityService, AiAssistanceService aiAssistanceService) {
        this.identityService = identityService;
        this.aiAssistanceService = aiAssistanceService;
    }

    @PostMapping({"/assist", "/drafts", "/summaries"})
    public Map<String, Object> requestAiAssistance(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        UserEntity caller = identityService.authenticate(userIdHeader);
        return aiAssistanceService.generateAssistance(caller, payload);
    }

    @GetMapping("/budget")
    public Map<String, Object> getBudgetStatus(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader) {
        identityService.authenticate(userIdHeader);
        return aiAssistanceService.getBudgetStatus();
    }

    @PostMapping("/budget/simulate-cap")
    public Map<String, Object> simulateCap(
            @RequestHeader(value = "X-Pulse-User-Id", required = false) String userIdHeader,
            @RequestBody Map<String, Object> payload) {
        identityService.authenticate(userIdHeader);
        Integer cap = payload.get("actionCap") instanceof Number n ? n.intValue() : null;
        aiAssistanceService.setRuntimeCapOverride(cap);
        return aiAssistanceService.getBudgetStatus();
    }
}
