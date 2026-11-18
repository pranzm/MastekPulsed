package com.mastek.pulse.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AiUsageLedgerRepository.java
 * Journey: AI Spend & Hard Budget Cap Monitoring (Proposal Page 5).
 *
 * Why it is designed this way:
 *   By extending Spring Data JPA's `JpaRepository<AiUsageLedgerEntity, String>`,
 *   Spring automatically generates the runtime implementation for:
 *   - `count()`: Executes `SELECT COUNT(*) FROM ai_usage_ledger` so `AiAssistanceService`
 *     can check whether total AI actions have reached the 50% (360), 80% (576), or
 *     100% (720) hard budget cap before invoking an AI model.
 *   - `save(entity)`: Inserts a new `AiUsageLedgerEntity` row whenever an AI action succeeds.
 *
 * Key Annotations Explained for Interns:
 *   - `@Repository`: Marks this interface as a Spring Data repository bean and
 *     translates low-level SQL/JDBC exceptions into Spring's `DataAccessException` hierarchy.
 * ============================================================================
 */
@Repository
public interface AiUsageLedgerRepository extends JpaRepository<AiUsageLedgerEntity, String> {
    // Inherits `count()`, `save()`, `findAll()`, and `findById()` directly from JpaRepository.
}
