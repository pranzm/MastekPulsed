package com.mastek.pulse.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AudienceRepository.java
 * Journey: Audience Permission Enforcement ("Filter Before Rank" - Proposal Page 4).
 *
 * Why it is designed this way:
 *   Provides standard JPA CRUD methods for `AudienceEntity` (`audiences` table) plus
 *   a fast native SQL lookup against the `audience_members` join table so we always
 *   know the exact list of `audience_id`s a given employee is authorized to access
 *   BEFORE we ever query, rank, or summarize stories, updates, or audio streams.
 *
 * Key Annotations Explained for Interns:
 *   - `@Repository`: Registers this interface as a Spring Data repository bean.
 *   - `@Query(..., nativeQuery = true)`: Executes raw SQL directly against the
 *     `audience_members` join table without needing a separate JPA entity class
 *     just for the two-column membership mapping (`audience_id`, `user_id`).
 *   - `@Param("userId")`: Safely binds the `userId` method parameter to the `:userId`
 *     named bind variable in the SQL prepared statement (preventing SQL injection).
 * ============================================================================
 */
@Repository
public interface AudienceRepository extends JpaRepository<AudienceEntity, String> {

    /**
     * Queries the `audience_members` join table and returns every `audience_id`
     * that the specified `userId` is explicitly permitted to access.
     *
     * @param userId ID of the authenticated employee (e.g., `"usr_asha"`).
     * @return List of permitted audience IDs (e.g., `["aud_all", "aud_pilot", "aud_uk_eng"]`).
     */
    @Query(value = "SELECT audience_id FROM audience_members WHERE user_id = :userId", nativeQuery = true)
    List<String> findPermittedAudienceIdsByUserId(@Param("userId") String userId);
}
