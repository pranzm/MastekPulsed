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
 * Why it is designed this way:
 *   Provides a native lookup of `audience_members` so we always know the exact
 *   list of `audience_id`s a given employee is authorized to access before we
 *   ever query or rank stories, updates, or audio streams.
 * ============================================================================
 */
@Repository
public interface AudienceRepository extends JpaRepository<AudienceEntity, String> {

    @Query(value = "SELECT audience_id FROM audience_members WHERE user_id = :userId", nativeQuery = true)
    List<String> findPermittedAudienceIdsByUserId(@Param("userId") String userId);
}
