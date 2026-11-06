package com.mastek.pulse.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: OutboxEventRepository.java
 * Journey: Durable Outbox & Background Worker (Proposal Pages 14 & 15).
 * Why it is designed this way:
 *   Queries pending outbox events that are due for processing (`next_run <= now`)
 *   so the background worker can safely deliver notifications and execute
 *   scheduled publications with retry support.
 * ============================================================================
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {
    List<OutboxEventEntity> findByStatusAndNextRunLessThanEqualOrderByCreatedAtAsc(String status, Instant now);
}
