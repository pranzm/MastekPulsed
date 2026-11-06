package com.mastek.pulse.connection;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: InvitationRepository.java
 * Journey: Accept Flow / One Transaction (Proposal Page 15).
 * Why it is designed this way:
 *   Look at `findByIdWithPessimisticLock(String id)`:
 *   It uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` which generates a database
 *   `SELECT ... FOR UPDATE` query.
 *   Why is this critical for 10,000+ users?
 *   If Daniel's invitation has 1 place remaining (`capacity=2, accepted_count=1`)
 *   and two requests are accepted at the exact same millisecond, PostgreSQL
 *   serializes the two transactions on the row lock—ensuring the first succeeds
 *   (`accepted_count -> 2`) and the second receives a clean `409 Conflict (Full capacity)`.
 * ============================================================================
 */
@Repository
public interface InvitationRepository extends JpaRepository<InvitationEntity, String> {

    Optional<InvitationEntity> findByStoryId(String storyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InvitationEntity i WHERE i.id = :id")
    Optional<InvitationEntity> findByIdWithPessimisticLock(@Param("id") String id);
}
