package com.mastek.pulse.productivity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ProductivityRepositories.java
 * Module: `productivity/` (Section 14 — Make me Productive, MP-03)
 *
 * WHAT: The four Spring Data repositories for the productivity module, grouped
 *       in one file as package-private interfaces (they are internal to this
 *       module — no other package may inject them, per ADR-007).
 * WHY:  EVERY finder takes `ownerId`. There is deliberately no
 *       `findById(habitId)` used by the service: looking up by id alone would
 *       make it easy to forget the owner check and leak another employee's data.
 * ============================================================================
 */
interface PersonalHabitRepository extends JpaRepository<PersonalHabitEntity, String> {

    List<PersonalHabitEntity> findByOwnerIdOrderByCreatedAtAscIdAsc(String ownerId);

    Optional<PersonalHabitEntity> findByIdAndOwnerId(String id, String ownerId);

    Optional<PersonalHabitEntity> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);

    long countByOwnerId(String ownerId);

    @Modifying
    @Query("DELETE FROM PersonalHabitEntity h WHERE h.ownerId = :ownerId")
    int deleteAllForOwner(@Param("ownerId") String ownerId);
}

interface HabitCheckinRepository extends JpaRepository<HabitCheckinEntity, String> {

    Optional<HabitCheckinEntity> findByHabitIdAndLocalDate(String habitId, LocalDate localDate);

    List<HabitCheckinEntity> findByOwnerIdAndLocalDateBetween(String ownerId, LocalDate from, LocalDate to);

    @Modifying
    @Query("DELETE FROM HabitCheckinEntity c WHERE c.habitId = :habitId AND c.ownerId = :ownerId")
    int deleteForHabit(@Param("habitId") String habitId, @Param("ownerId") String ownerId);

    @Modifying
    @Query("DELETE FROM HabitCheckinEntity c WHERE c.ownerId = :ownerId")
    int deleteAllForOwner(@Param("ownerId") String ownerId);
}

interface ProductivityPreferenceRepository extends JpaRepository<ProductivityPreferenceEntity, String> {
}

interface FocusSessionRepository extends JpaRepository<FocusSessionEntity, String> {

    Optional<FocusSessionEntity> findByOwnerIdAndClientSessionId(String ownerId, String clientSessionId);

    long countByOwnerIdAndKind(String ownerId, String kind);

    long countByOwnerIdAndKindAndCompletedAtGreaterThanEqual(String ownerId, String kind, Instant since);

    @Modifying
    @Query("DELETE FROM FocusSessionEntity s WHERE s.ownerId = :ownerId")
    int deleteAllForOwner(@Param("ownerId") String ownerId);
}
