-- ============================================================================
-- INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
-- ============================================================================
-- File: backend/src/main/resources/db/migration/V4__make_me_productive.sql
-- Purpose: Flyway Version 4 — Section 14 "Make me Productive" (MP-03).
--
-- Adds four OWNER-SCOPED tables for the optional personal productivity space:
--   1. personal_habits          — employee-defined habits (name + schedule)
--   2. habit_checkins           — one row per (habit, local calendar day)
--   3. productivity_preferences — Pomodoro / reset timer durations per employee
--   4. focus_sessions           — optional completed timer history (client UUID dedupe)
--
-- Design rules (see docs/campfire/MAKE-ME-PRODUCTIVE-PLAN.md):
--   * Every row carries `owner_id`; the service ALWAYS filters by the
--     authenticated caller. Editorial/admin roles get NO bypass.
--   * `local_date` is stored as a DATE in the employee's own timezone so a
--     midnight/DST shift can never move a tick to the wrong day.
--   * These tables are never read by recognition, leaderboard, audit or AI code.
--   * Additive migration only — V1, V2 and V3 are never edited.
--
-- KNOWN GAP (ADR-013): ownership currently comes from the demo identity header
-- (`X-Pulse-User-Id`). Verified Entra/OIDC principal (MP-02) is deferred.
-- ============================================================================

-- 1. Personal habits ---------------------------------------------------------
CREATE TABLE personal_habits (
    -- Server-generated identifier (e.g., 'hab_1a2b3c4d')
    id VARCHAR(64) PRIMARY KEY,
    -- Owning employee; the only principal allowed to read/update/delete this row
    owner_id VARCHAR(64) NOT NULL REFERENCES users(id),
    -- Employee-chosen habit label (1-80 chars, validated again in Java)
    name VARCHAR(80) NOT NULL,
    -- Which weekdays the habit is due on
    schedule VARCHAR(16) NOT NULL,
    -- IANA timezone used to decide "today" for check-in validation
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    -- Optional caller-scoped Idempotency-Key so a retried POST creates one habit
    idempotency_key VARCHAR(120) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Optimistic lock counter: PATCH must send expectedVersion or get 409
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_habit_schedule CHECK (schedule IN ('daily', 'weekdays', 'weekends')),
    CONSTRAINT uq_habit_owner_idem UNIQUE (owner_id, idempotency_key)
);
CREATE INDEX idx_personal_habits_owner ON personal_habits(owner_id);

-- 2. Habit check-ins ---------------------------------------------------------
CREATE TABLE habit_checkins (
    id VARCHAR(64) PRIMARY KEY,
    -- Deleting a habit deletes its history (owner-controlled retention)
    habit_id VARCHAR(64) NOT NULL REFERENCES personal_habits(id) ON DELETE CASCADE,
    -- Denormalised owner for cheap owner-scoped range queries
    owner_id VARCHAR(64) NOT NULL REFERENCES users(id),
    -- Calendar day in the employee's timezone (NOT a UTC timestamp)
    local_date DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Idempotent upsert target: one row per habit per day
    CONSTRAINT uq_checkin_habit_day UNIQUE (habit_id, local_date)
);
CREATE INDEX idx_habit_checkins_owner_day ON habit_checkins(owner_id, local_date);

-- 3. Timer preferences -------------------------------------------------------
CREATE TABLE productivity_preferences (
    owner_id VARCHAR(64) PRIMARY KEY REFERENCES users(id),
    focus_minutes INTEGER NOT NULL DEFAULT 25,
    short_break_minutes INTEGER NOT NULL DEFAULT 5,
    long_break_minutes INTEGER NOT NULL DEFAULT 15,
    reset_minutes INTEGER NOT NULL DEFAULT 2,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_pref_focus CHECK (focus_minutes BETWEEN 1 AND 120),
    CONSTRAINT chk_pref_short CHECK (short_break_minutes BETWEEN 1 AND 60),
    CONSTRAINT chk_pref_long CHECK (long_break_minutes BETWEEN 1 AND 60),
    CONSTRAINT chk_pref_reset CHECK (reset_minutes BETWEEN 1 AND 30)
);

-- 4. Completed timer sessions (optional history) ------------------------------
CREATE TABLE focus_sessions (
    id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL REFERENCES users(id),
    -- Client-generated UUID; a retried POST with the same UUID is a no-op
    client_session_id VARCHAR(64) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    planned_seconds INTEGER NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_session_kind CHECK (kind IN ('focus', 'short', 'long', 'breath', 'mind')),
    CONSTRAINT chk_session_seconds CHECK (planned_seconds BETWEEN 60 AND 7200),
    CONSTRAINT uq_session_owner_client UNIQUE (owner_id, client_session_id)
);
CREATE INDEX idx_focus_sessions_owner ON focus_sessions(owner_id, completed_at);
