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
 * Why it is designed this way:
 *   Allows the Publishing Portal to display an ordered history of all review,
 *   approval, publishing, withdrawal, and moderation actions.
 * ============================================================================
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    List<AuditEventEntity> findTop50ByOrderByCreatedAtDesc();
}
