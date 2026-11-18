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
 *
 * Why it is designed this way:
 *   Looks up a specific `versionNumber` for a `contentId`—allowing the Employee
 *   Feed to read `publishedVersion` while the Publishing Portal inspects
 *   `currentVersion` or full revision history.
 * ============================================================================
 */
// @Repository registers this interface as a Spring Data JPA repository bean.
@Repository
public interface ContentRevisionRepository extends JpaRepository<ContentRevisionEntity, String> {

    /**
     * Fetches the exact immutable revision snapshot matching `(content_id, version_number)`.
     *
     * @param contentId     ID of the parent {@link ContentItemEntity}
     * @param versionNumber 1-based revision number (e.g., `publishedVersion` or `currentVersion`)
     * @return matching {@link ContentRevisionEntity} if present
     */
    Optional<ContentRevisionEntity> findByContentIdAndVersionNumber(String contentId, int versionNumber);

    /**
     * Lists all revisions of a content item ordered from newest revision number to oldest.
     *
     * @param contentId ID of the parent {@link ContentItemEntity}
     * @return list of revisions ordered by `version_number DESC`
     */
    List<ContentRevisionEntity> findByContentIdOrderByVersionNumberDesc(String contentId);
}
