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
 *
 * Why it is designed this way:
 *   Provides standard JPA lookup (`findById`, `findAll`, `save`) plus
 *   `findByIdWithPessimisticLock` (`SELECT ... FOR UPDATE`).
 *   Why do we lock the `UserEntity` row during recognition point awards?
 *   When two concurrent requests award points to the same employee in the same UTC week
 *   (e.g., two colleagues appreciating the same story simultaneously, or simultaneous
 *   story publications), locking the recipient's `users` row with `PESSIMISTIC_WRITE`
 *   serializes the point-cap check (`SUM(points)`) and insert into `recognition_events`
 *   so an employee's weekly points NEVER exceed the hard caps (20 for story publication,
 *   15 for helpful appreciation, 25 for challenge participation, 60 total weekly cap).
 *
 * Key Annotations Explained for Interns:
 *   - `@Repository`: Registers this interface as a Spring Data JPA repository bean.
 *   - `@Lock(LockModeType.PESSIMISTIC_WRITE)`: Instructs Hibernate to append
 *     `FOR UPDATE` to the generated SQL `SELECT` statement, acquiring a row-level
 *     database lock in PostgreSQL (and H2) until the surrounding `@Transactional`
 *     method commits or rolls back.
 *   - `@Query("SELECT u FROM UserEntity u WHERE u.id = :id")`: JPQL query selecting
 *     the target `UserEntity` by primary key so `@Lock` can be applied to it.
 * ============================================================================
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /**
     * Loads a `UserEntity` by ID while acquiring a database-level `SELECT ... FOR UPDATE`
     * pessimistic write lock on that user's row for the duration of the current transaction.
     * Used by `RecognitionService` to serialize concurrent weekly point-cap checks.
     *
     * @param id Primary key `users.id` of the employee to lock.
     * @return `Optional<UserEntity>` containing the locked user row if found.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdWithPessimisticLock(@Param("id") String id);
}
