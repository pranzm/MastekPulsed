package com.mastek.pulse.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ContentRevisionRepository.java
 * Journey: Revision-Bound Review & Publishing (Proposal Pages 11 & 15).
 * Why it is designed this way:
 *   Looks up a specific `versionNumber` for a `contentId`—allowing the Employee
 *   Feed to read `publishedVersion` while the Publishing Portal inspects
 *   `currentVersion` or full revision history.
 * ============================================================================
 */
@Repository
public interface ContentRevisionRepository extends JpaRepository<ContentRevisionEntity, String> {

    Optional<ContentRevisionEntity> findByContentIdAndVersionNumber(String contentId, int versionNumber);

    List<ContentRevisionEntity> findByContentIdOrderByVersionNumberDesc(String contentId);
}
