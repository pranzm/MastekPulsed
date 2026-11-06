package com.mastek.pulse.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: AudienceEntity.java
 * Journey: Audience Permission Enforcement ("Filter Before Rank" - Proposal Page 4 & 14).
 * Why it is designed this way:
 *   Every story, update, Mastek Radio queue, and Culture Cast episode belongs to
 *   an `audience_id`. Before any content is ranked or returned to a user, we
 *   check that the user is a member of that audience in `audience_members`.
 * ============================================================================
 */
@Entity
@Table(name = "audiences")
public class AudienceEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "region_scope", nullable = false, length = 32)
    private String regionScope = "ALL";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRegionScope() { return regionScope; }
    public void setRegionScope(String regionScope) { this.regionScope = regionScope; }
}
