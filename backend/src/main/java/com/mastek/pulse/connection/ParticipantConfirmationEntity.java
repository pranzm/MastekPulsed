package com.mastek.pulse.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: ParticipantConfirmationEntity.java
 * Journey: Primary Journey Step 5 ("Both participants record private feedback"
 *          - Proposal Pages 2, 8, 12, 15).
 * Why it is designed this way:
 *   Maps to `participant_confirmations` in `V1__init_schema.sql`.
 *   1. Stores `happened` (`YES`, `NO`, `NOT_YET`) and `useful` (`TRUE` / `FALSE` / `NULL`).
 *   2. Only the two participants (`requesterId` and `hostId`) can ever read or
 *      write confirmations for a `requestId`.
 *   3. Proposal Page 8 & 15 invariant: "Count a useful connection only when both
 *      confirm happened=true and useful=true. Skipped or missing feedback stays unknown."
 * ============================================================================
 */
@Entity
@Table(name = "participant_confirmations")
public class ParticipantConfirmationEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "happened", nullable = false, length = 16)
    private String happened; // YES, NO, NOT_YET

    @Column(name = "useful")
    private Boolean useful;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getHappened() { return happened; }
    public void setHappened(String happened) { this.happened = happened; }

    public Boolean getUseful() { return useful; }
    public void setUseful(Boolean useful) { this.useful = useful; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
