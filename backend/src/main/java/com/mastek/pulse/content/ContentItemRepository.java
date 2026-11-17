package com.mastek.pulse.content;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ContentItemRepository.java
 * Journey: Discovery Feed, Listen Tab & Publishing Portal (CF-004, CF-010).
 * Why it is designed this way:
 *   1. Provides `findByIdWithPessimisticLock` (`SELECT ... FOR UPDATE`) so
 *      concurrent review, edit, publish, and withdraw actions on the same
 *      content item are serialized atomically at the database level.
 *   2. Queries published items by `publishedAudienceId` (or `audienceId` fallback)
 *      so audience edits on unapproved revisions never leak old published text.
 * ============================================================================
 */
@Repository
public interface ContentItemRepository extends JpaRepository<ContentItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContentItemEntity c WHERE c.id = :id")
    Optional<ContentItemEntity> findByIdWithPessimisticLock(@Param("id") String id);

    @Query("""
        SELECT c FROM ContentItemEntity c
        WHERE c.status = :status
          AND c.publishedVersion IS NOT NULL
          AND COALESCE(c.publishedAudienceId, c.audienceId) IN :audienceIds
        ORDER BY c.publishedAt DESC
        """)
    List<ContentItemEntity> findByAudienceIdInAndStatusOrderByPublishedAtDesc(
            @Param("audienceIds") List<String> audienceIds,
            @Param("status") String status);

    @Query("""
        SELECT c FROM ContentItemEntity c
        WHERE c.status = :status
          AND c.contentType = :contentType
          AND c.publishedVersion IS NOT NULL
          AND COALESCE(c.publishedAudienceId, c.audienceId) IN :audienceIds
        ORDER BY c.publishedAt DESC
        """)
    List<ContentItemEntity> findByAudienceIdInAndContentTypeAndStatusOrderByPublishedAtDesc(
            @Param("audienceIds") List<String> audienceIds,
            @Param("contentType") String contentType,
            @Param("status") String status);

    List<ContentItemEntity> findByStatusAndScheduledForLessThanEqual(String status, Instant now);

    List<ContentItemEntity> findAllByOrderByUpdatedAtDesc();
}
