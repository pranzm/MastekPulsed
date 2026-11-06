package com.mastek.pulse.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AiUsageLedgerRepository.java
 * Journey: AI Spend & Hard Budget Cap Monitoring (Proposal Page 5).
 * Why it is designed this way:
 *   Counts total AI actions against the 720-action budget cap.
 * ============================================================================
 */
@Repository
public interface AiUsageLedgerRepository extends JpaRepository<AiUsageLedgerEntity, String> {
}
