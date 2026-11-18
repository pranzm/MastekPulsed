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
 *
 * Why it is designed this way:
 *   Every story, official update, Mastek Radio queue, Culture Cast episode, and
 *   Campfire weekly challenge belongs to an explicit `audience_id` (such as
 *   `aud_all`, `aud_pilot`, `aud_uk_eng`, or `aud_leaders`).
 *   Before any content is ranked by interest or returned to a user—and before any
 *   text is passed to optional AI summarization—we verify that the calling user
 *   is a member of that audience in the `audience_members` join table.
 *
 * JPA Annotations Explained for Interns:
 *   - `@Entity`: Declares that this class represents a database table managed by JPA/Hibernate.
 *   - `@Table(name = "audiences")`: Maps this class to the `audiences` table in PostgreSQL.
 *   - `@Id`: Marks `id` as the primary key column (`audiences.id`).
 *   - `@Column(...)`: Specifies column name, nullability, and character length limits.
 * ============================================================================
 */
@Entity
@Table(name = "audiences")
public class AudienceEntity {

    /**
     * Unique identifier for the audience cohort (e.g., `"aud_all"`, `"aud_pilot"`,
     * `"aud_uk_eng"`, `"aud_leaders"`).
     * Mapped to `id VARCHAR(64) NOT NULL PRIMARY KEY`.
     */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /**
     * Human-readable display name shown in the Publishing Portal audience selector
     * and story badges (e.g., `"Pilot Cohort (UK & India Delivery)"`).
     * Mapped to `name VARCHAR(120) NOT NULL`.
     */
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /**
     * Explanation of who belongs to this audience cohort and what content is scoped to it.
     * Mapped to `description VARCHAR(500) NOT NULL`.
     */
    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /**
     * Geographic or organizational region scope for this audience (e.g., `"ALL"`, `"UK"`, `"IN"`).
     * Defaults to `"ALL"`.
     * Mapped to `region_scope VARCHAR(32) NOT NULL`.
     */
    @Column(name = "region_scope", nullable = false, length = 32)
    private String regionScope = "ALL";

    /** @return Unique audience ID (e.g., `"aud_pilot"`). */
    public String getId() { return id; }
    /** @param id Unique audience ID to set. */
    public void setId(String id) { this.id = id; }

    /** @return Human-readable audience name. */
    public String getName() { return name; }
    /** @param name Human-readable audience name to set. */
    public void setName(String name) { this.name = name; }

    /** @return Description of the audience cohort. */
    public String getDescription() { return description; }
    /** @param description Description of the audience cohort to set. */
    public void setDescription(String description) { this.description = description; }

    /** @return Region scope code (e.g., `"ALL"`, `"UK"`). */
    public String getRegionScope() { return regionScope; }
    /** @param regionScope Region scope code to set. */
    public void setRegionScope(String regionScope) { this.regionScope = regionScope; }
}
