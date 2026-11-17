package com.mastek.pulse.recognition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: WeeklyBadgeRepository.java
 * Journey: Weekly Badges Projection Lookup & Synchronization (Story CF-006).
 * ============================================================================
 */
@Repository
public interface WeeklyBadgeRepository extends JpaRepository<WeeklyBadgeEntity, String> {

    List<WeeklyBadgeEntity> findByUserIdAndWeekStart(String userId, String weekStart);

    void deleteByUserIdAndWeekStartAndAudienceId(String userId, String weekStart, String audienceId);
}
