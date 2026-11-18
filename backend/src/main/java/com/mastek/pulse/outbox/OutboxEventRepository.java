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
 *
 * Why it is designed this way:
 *   Queries pending outbox events that are due for processing (`next_run <= now`)
 *   so the background worker can safely deliver notifications and execute
 *   scheduled publications with retry support.
 * ============================================================================
 */
// @Repository registers this interface as a Spring Data JPA repository bean.
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    /**
     * Finds all outbox events in the given `status` (typically `"PENDING"`) whose `nextRun`
     * timestamp is due (`<= now`), ordered oldest-first (`createdAt ASC`) for FIFO delivery.
     *
     * @param status target outbox status (`"PENDING"`)
     * @param now    current UTC timestamp
     * @return FIFO list of due {@link OutboxEventEntity} records
     */
    List<OutboxEventEntity> findByStatusAndNextRunLessThanEqualOrderByCreatedAtAsc(String status, Instant now);
}
