package com.mastek.pulse.common;

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
 * File: UserRepository.java
 * Journey: Shared Identity & Atomic Recognition Cap Reservation (CF-005 / D4 / D5).
 * Why it is designed this way:
 *   Provides standard lookup plus `findByIdWithPessimisticLock` (`SELECT ... FOR UPDATE`)
 *   so concurrent publication or appreciation requests for the same user serialize
 *   atomically in PostgreSQL and never exceed weekly point caps (20 / 15 / 25 / 60).
 * ============================================================================
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdWithPessimisticLock(@Param("id") String id);
}
