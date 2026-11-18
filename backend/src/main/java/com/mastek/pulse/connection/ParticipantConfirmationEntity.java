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
 *
 * Why it is designed this way:
 *   Maps to `participant_confirmations` in `V1__init_schema.sql`.
 *   1. Stores `happened` (`YES`, `NO`, `NOT_YET`) and `useful` (`TRUE` / `FALSE` / `NULL`).
 *   2. Only the two participants (`requesterId` and `hostId`) can ever read or
 *      write confirmations for a `requestId`.
 *   3. Proposal Page 8 & 15 invariant: "Count a useful connection only when both
 *      confirm happened=true and useful=true. Skipped or missing feedback stays unknown."
 * ============================================================================
 */
// @Entity marks this class as a JPA persistent entity.
@Entity
// @Table maps this entity to the `participant_confirmations` table (`UNIQUE (request_id, user_id)` in SQL).
@Table(name = "participant_confirmations")
public class ParticipantConfirmationEntity {

    // Primary key of the confirmation row (e.g., `conf_01`).
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    // Foreign key referencing `connection_requests.id`.
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    // Foreign key referencing `users.id` (must be either the requester or the host of `request_id`).
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // Whether the conversation took place: `"YES"`, `"NO"`, or `"NOT_YET"`.
    @Column(name = "happened", nullable = false, length = 16)
    private String happened; // YES, NO, NOT_YET

    // Nullable boolean indicating whether the participant found the conversation useful (`true`/`false` when `happened == "YES"`).
    @Column(name = "useful")
    private Boolean useful;

    // UTC timestamp when the participant submitted or updated their confirmation.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** @return primary key of this confirmation record */
    public String getId() { return id; }
    /** @param id primary key of this confirmation record */
    public void setId(String id) { this.id = id; }

    /** @return ID of the parent {@link ConnectionRequestEntity} */
    public String getRequestId() { return requestId; }
    /** @param requestId ID of the parent {@link ConnectionRequestEntity} */
    public void setRequestId(String requestId) { this.requestId = requestId; }

    /** @return user ID of the participant (requester or host) who submitted this confirmation */
    public String getUserId() { return userId; }
    /** @param userId user ID of the participant submitting this confirmation */
    public void setUserId(String userId) { this.userId = userId; }

    /** @return `"YES"`, `"NO"`, or `"NOT_YET"` */
    public String getHappened() { return happened; }
    /** @param happened `"YES"`, `"NO"`, or `"NOT_YET"` */
    public void setHappened(String happened) { this.happened = happened; }

    /** @return `Boolean.TRUE`, `Boolean.FALSE`, or `null` if the conversation has not happened yet */
    public Boolean getUseful() { return useful; }
    /** @param useful whether the conversation was useful */
    public void setUseful(Boolean useful) { this.useful = useful; }

    /** @return UTC timestamp of the latest confirmation update */
    public Instant getUpdatedAt() { return updatedAt; }
    /** @param updatedAt UTC timestamp of the latest confirmation update */
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
