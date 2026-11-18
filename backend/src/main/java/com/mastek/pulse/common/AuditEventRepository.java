package com.mastek.pulse.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AuditEventRepository.java
 * Journey: Publishing Portal Audit Log (Proposal Page 11).
 *
 * Why it is designed this way:
 *   Allows the Publishing Portal to display an ordered history of the 50 most
 *   recent review, approval, publishing, withdrawal, and moderation actions.
 *
 * Spring Data Derived Query Explained for Interns:
 *   - `findTop50ByOrderByCreatedAtDesc()`: Spring Data JPA parses this method name
 *     at startup and automatically generates the SQL query:
 *     `SELECT * FROM audit_events ORDER BY created_at DESC LIMIT 50`
 *     without requiring any manual SQL string!
 * ============================================================================
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    /**
     * Fetches the 50 most recent audit events ordered newest-first (`created_at DESC`)
     * for display in the Publishing Portal governance audit log.
     *
     * @return List of up to 50 `AuditEventEntity` rows in reverse chronological order.
     */
    List<AuditEventEntity> findTop50ByOrderByCreatedAtDesc();
}
