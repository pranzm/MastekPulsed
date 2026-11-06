package com.mastek.pulse.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ContentItemRepository.java
 * Journey: Discovery Feed, Listen Tab & Publishing Portal.
 * Why it is designed this way:
 *   Provides query methods that filter by `audienceId IN (...)` FIRST ("Audience
 *   filter before rank" - Proposal Page 14) as well as querying `SCHEDULED`
 *   items due for background release.
 * ============================================================================
 */
@Repository
public interface ContentItemRepository extends JpaRepository<ContentItemEntity, String> {

    List<ContentItemEntity> findByAudienceIdInAndStatusOrderByPublishedAtDesc(List<String> audienceIds, String status);

    List<ContentItemEntity> findByAudienceIdInAndContentTypeAndStatusOrderByPublishedAtDesc(
            List<String> audienceIds, String contentType, String status);

    List<ContentItemEntity> findByStatusAndScheduledForLessThanEqual(String status, Instant now);

    List<ContentItemEntity> findAllByOrderByUpdatedAtDesc();
}
