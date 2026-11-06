package com.mastek.pulse.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AiUsageLedgerEntity.java
 * Journey: Supporting Journey C ("Use optional AI drafting or summarization,
 *          review the result, and explicitly choose the next action" -
 *          Proposal Pages 4 & 5).
 * Why it is designed this way:
 *   Page 5 of the proposal specifies an illustrative 4-week workload of
 *   720 AI actions (60 people x 4 weeks x 3 actions; ~1,000 input + 500 output tokens)
 *   and requires: "Alert at 50%, 80%, and 100%; disable optional AI at the cap."
 *   Every AI call logs a row here so we enforce the hard cap deterministically.
 * ============================================================================
 */
@Entity
@Table(name = "ai_usage_ledger")
public class AiUsageLedgerEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "assist_type", nullable = false, length = 32)
    private String assistType; // INVITATION_DRAFT, STORY_POLISH, UPDATE_SUMMARY

    @Column(name = "context_id", nullable = false, length = 64)
    private String contextId;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens = 1000;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens = 500;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static AiUsageLedgerEntity create(String userId, String assistType, String contextId) {
        AiUsageLedgerEntity e = new AiUsageLedgerEntity();
        e.setId("ai_" + UUID.randomUUID().toString().substring(0, 8));
        e.setUserId(userId);
        e.setAssistType(assistType);
        e.setContextId(contextId);
        e.setInputTokens(1000);
        e.setOutputTokens(500);
        e.setCreatedAt(Instant.now());
        return e;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAssistType() { return assistType; }
    public void setAssistType(String assistType) { this.assistType = assistType; }

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
