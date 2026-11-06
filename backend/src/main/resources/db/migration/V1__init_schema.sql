-- ============================================================================
-- INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
-- ============================================================================
-- File: backend/src/main/resources/db/migration/V1__init_schema.sql
-- Purpose: Flyway Version 1 Schema Migration for Mastek Pulse.
-- Supports Proposal Pages 5, 11, 14, and 15:
--   1. Separates `content_items` and `content_revisions` so approvals bind to
--      an exact revision (`version_number`), and any edit after approval can
--      safely create a new revision in `IN_REVIEW` without corrupting live data.
--   2. Enforces `CHECK (accepted_count <= capacity)` on `invitations` so host
--      capacity is protected at the database level as well as via row locks.
--   3. Enforces `UNIQUE (invitation_id, requester_id)` and
--      `UNIQUE (requester_id, idempotency_key)` on `connection_requests` so
--      network retries never create duplicate requests.
--   4. Enforces `UNIQUE (request_id, user_id)` on `participant_confirmations`
--      so bilateral usefulness is calculated strictly from the two participants.
-- ============================================================================

-- 1. USERS (Synthetic Employee Profiles in POC; No private messages or appraisal scores)
CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    region VARCHAR(32) NOT NULL,
    function_name VARCHAR(64) NOT NULL,
    roles_csv VARCHAR(255) NOT NULL,
    interests_csv VARCHAR(500) NOT NULL,
    notification_frequency VARCHAR(32) NOT NULL DEFAULT 'DAILY_DIGEST',
    preferred_language VARCHAR(16) NOT NULL DEFAULT 'en',
    host_paused BOOLEAN NOT NULL DEFAULT FALSE,
    weekly_host_minutes INT NOT NULL DEFAULT 25,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. AUDIENCES & MEMBERSHIP (Enforces "Audience Filter Before Rank")
CREATE TABLE audiences (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    region_scope VARCHAR(32) NOT NULL DEFAULT 'ALL'
);

CREATE TABLE audience_members (
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (audience_id, user_id)
);

-- 3. CONTENT ITEMS & VERSIONED REVISIONS
-- Why two tables? Approvals bind to an exact `version_number` in `content_revisions`.
CREATE TABLE content_items (
    id VARCHAR(64) PRIMARY KEY,
    author_id VARCHAR(64) NOT NULL REFERENCES users(id),
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    content_type VARCHAR(32) NOT NULL, -- STORY, UPDATE, CULTURE_CAST, RADIO_QUEUE
    status VARCHAR(32) NOT NULL,       -- DRAFT, IN_REVIEW, APPROVED, SCHEDULED, PUBLISHED, WITHDRAWN
    current_version INT NOT NULL DEFAULT 1,
    published_version INT NULL,
    scheduled_for TIMESTAMP WITH TIME ZONE NULL,
    expires_at TIMESTAMP WITH TIME ZONE NULL,
    published_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_revisions (
    id VARCHAR(64) PRIMARY KEY,
    content_id VARCHAR(64) NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    body_text TEXT NOT NULL,
    tags_csv VARCHAR(300) NOT NULL DEFAULT '',
    official_source VARCHAR(160) NULL,
    external_url VARCHAR(300) NULL,
    has_audio BOOLEAN NOT NULL DEFAULT FALSE,
    media_asset_key VARCHAR(200) NULL,
    duration_seconds INT NULL,
    transcript_text TEXT NULL,
    chapters_json TEXT NULL,
    related_story_id VARCHAR(64) NULL,
    reviewer_notes VARCHAR(500) NULL,
    approved_by VARCHAR(64) NULL REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_content_revision UNIQUE (content_id, version_number)
);

-- 4. INVITATIONS (Voluntary Colleague Connection Offers Attached to a Story)
CREATE TABLE invitations (
    id VARCHAR(64) PRIMARY KEY,
    story_id VARCHAR(64) NOT NULL UNIQUE REFERENCES content_items(id) ON DELETE CASCADE,
    host_id VARCHAR(64) NOT NULL REFERENCES users(id),
    topic_label VARCHAR(160) NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 15,
    capacity INT NOT NULL DEFAULT 2,
    accepted_count INT NOT NULL DEFAULT 0,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_invitation_capacity CHECK (accepted_count >= 0 AND accepted_count <= capacity)
);

-- 5. CONNECTION REQUESTS (State Machine: PENDING -> ACCEPTED | DECLINED | CANCELLED | EXPIRED)
CREATE TABLE connection_requests (
    id VARCHAR(64) PRIMARY KEY,
    invitation_id VARCHAR(64) NOT NULL REFERENCES invitations(id) ON DELETE CASCADE,
    requester_id VARCHAR(64) NOT NULL REFERENCES users(id),
    message_text VARCHAR(1000) NOT NULL,
    ai_assisted BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(120) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invitation_requester UNIQUE (invitation_id, requester_id),
    CONSTRAINT uq_requester_idempotency UNIQUE (requester_id, idempotency_key)
);

-- 6. PARTICIPANT CONFIRMATIONS (Bilateral Private Follow-Up Check-in)
-- Useful connection is counted ONLY when both requester and host confirm happened='YES' and useful=TRUE!
CREATE TABLE participant_confirmations (
    id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL REFERENCES connection_requests(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id),
    happened VARCHAR(16) NOT NULL, -- YES, NO, NOT_YET
    useful BOOLEAN NULL,           -- TRUE / FALSE when happened='YES', NULL otherwise
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_request_participant UNIQUE (request_id, user_id)
);

-- 7. PLAYBACK PROGRESS (Cross-Device Audio Resume between Employee Web and Mobile)
CREATE TABLE playback_progress (
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_id VARCHAR(64) NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    position_seconds INT NOT NULL DEFAULT 0,
    duration_seconds INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, content_id)
);

-- 8. AI USAGE LEDGER (Tracks 720-Action Four-Week Workload Budget & 50%/80%/100% Alerts)
CREATE TABLE ai_usage_ledger (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id),
    assist_type VARCHAR(32) NOT NULL, -- INVITATION_DRAFT, STORY_POLISH, UPDATE_SUMMARY
    context_id VARCHAR(64) NOT NULL,
    input_tokens INT NOT NULL DEFAULT 1000,
    output_tokens INT NOT NULL DEFAULT 500,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. AUDIT EVENTS & TRANSACTIONAL OUTBOX (Proposal Pages 11, 14, 15)
CREATE TABLE audit_events (
    id VARCHAR(64) PRIMARY KEY,
    actor_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    details VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE outbox_events (
    event_id VARCHAR(64) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSED, FAILED
    attempts INT NOT NULL DEFAULT 0,
    next_run TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
