-- ============================================================================
-- INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
-- ============================================================================
-- File: backend/src/main/resources/db/migration/V3__campfire_challenges_and_recognition.sql
-- Purpose: Flyway Version 3 Migration for Mastek Campfire (17 November 2026).
-- Implements Stories CF-002, CF-004, CF-005, CF-006, CF-007, and CF-010:
--   1. Adds `published_audience_id` to `content_items` and `audience_id` to
--      `content_revisions` so draft audience edits never broaden access to an
--      already published snapshot until the new revision is approved & published.
--   2. Creates `challenges` and `challenge_entries` (linking weekly prompts to
--      the existing `content_items` / `content_revisions` workflow).
--   3. Creates `appreciations`, `recognition_events` (immutable ledger with
--      unique `source_event_id` and `reversal_of`), `recognition_preferences`,
--      and `weekly_badges`.
--   4. Seeds the active challenge `ch_beyond_01` ("Beyond My Job Title") and
--      initial published challenge awards for Daniel (`story_01`), Elena (`story_06`),
--      and Sophie (`story_03`) while keeping Asha at 0 points before her submission!
-- ============================================================================

-- ============================================================================
-- 1. SEPARATE DRAFT AUDIENCE FROM IMMUTABLE PUBLISHED AUDIENCE SNAPSHOT (CF-010 / D2)
-- ============================================================================
-- Why this matters: Suppose `story_08` is PUBLISHED in `aud_uk_restricted`, and its author
-- creates Draft Revision 2 proposing a broader audience (`aud_pilot`). Until Revision 2 is
-- reviewed AND published, employee read endpoints MUST continue enforcing `published_audience_id`
-- (`aud_uk_restricted`) so non-members like Asha cannot read Revision 1 prematurely!
ALTER TABLE content_items ADD COLUMN published_audience_id VARCHAR(64) NULL REFERENCES audiences(id);
ALTER TABLE content_revisions ADD COLUMN audience_id VARCHAR(64) NULL REFERENCES audiences(id);

-- Backfill `published_audience_id` for all items already in PUBLISHED status from V2 seed data
UPDATE content_items
SET published_audience_id = audience_id
WHERE published_version IS NOT NULL AND status = 'PUBLISHED';

-- Backfill `audience_id` on every existing revision row to match its parent `content_items.audience_id`
UPDATE content_revisions cr
SET audience_id = (SELECT ci.audience_id FROM content_items ci WHERE ci.id = cr.content_id);

-- Ensure Elena (`usr_elena`) can author stories as a colleague (`CREATOR`) while
-- still NOT holding `REVIEWER` or `PUBLISHER` editorial authority!
UPDATE users
SET roles_csv = 'EMPLOYEE,CREATOR,ADMIN'
WHERE id = 'usr_elena';

-- ============================================================================
-- 2. WEEKLY CHALLENGES (CF-002)
-- ============================================================================
-- Represents a voluntary, time-boxed weekly storytelling prompt (e.g., "Beyond My Job Title").
CREATE TABLE challenges (
    -- Unique challenge identifier (e.g., 'ch_beyond_01')
    id VARCHAR(64) PRIMARY KEY,
    -- Display title of the weekly challenge
    title VARCHAR(160) NOT NULL,
    -- Guiding storytelling prompt shown to employees in the Campfire Challenge banner
    prompt VARCHAR(1000) NOT NULL,
    -- UTC timestamp when the challenge opens for entries
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- UTC timestamp when the challenge closes
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Timezone label for display and weekly window calculation (defaults to 'UTC')
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    -- Target audience (`REFERENCES audiences(id)`) allowed to view and submit to this challenge
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    -- Lifecycle state of the challenge: 'ACTIVE', 'CLOSED', or 'UPCOMING'
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    -- Creation timestamp of the challenge record
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Database invariant ensuring the challenge end time is strictly after its start time
    CONSTRAINT chk_challenge_dates CHECK (ends_at > starts_at)
);

-- ============================================================================
-- 3. CHALLENGE ENTRIES TIED TO EXISTING CONTENT WORKFLOW (CF-002)
-- ============================================================================
-- Links a user's challenge participation to a real `content_items` story so it flows
-- through the exact same Reviewer approval and Publisher release state machine.
CREATE TABLE challenge_entries (
    -- Unique primary key for the challenge entry record
    id VARCHAR(64) PRIMARY KEY,
    -- Target challenge (`REFERENCES challenges(id)`)
    challenge_id VARCHAR(64) NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    -- Participating colleague (`REFERENCES users(id)`)
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- 1-to-1 link to the underlying story in `content_items(id)`
    content_id VARCHAR(64) NOT NULL UNIQUE REFERENCES content_items(id) ON DELETE CASCADE,
    -- Client-supplied `Idempotency-Key` header value from `POST /api/v1/challenges/{id}/entries`
    idempotency_key VARCHAR(120) NOT NULL,
    -- SHA-256 hash of the normalized request payload (`title` + `bodyText` + `submitForReview`).
    -- If a client replays the same `Idempotency-Key` with a DIFFERENT payload hash, the API rejects
    -- it with `409 IDEMPOTENCY_CONFLICT`!
    payload_hash VARCHAR(128) NOT NULL,
    -- Timestamp when the entry was first created
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Each colleague may submit at most one story entry per weekly challenge
    CONSTRAINT uq_challenge_user UNIQUE (challenge_id, user_id),
    -- Guarantees idempotent replay safety per `(user_id, idempotency_key)`
    CONSTRAINT uq_challenge_entry_idem UNIQUE (user_id, idempotency_key)
);

-- ============================================================================
-- 4. COLLEAGUE APPRECIATIONS WITH EVENT-TIME TEAM SNAPSHOTS (CF-006)
-- ============================================================================
-- Records when a colleague appreciates a published story (`POST /api/v1/content/{id}/appreciations`).
-- Captures `actor_team_snapshot` and `recipient_team_snapshot` at event time so cross-team
-- badge calculations ("Connection Maker" for appreciating >= 3 distinct teams) remain stable.
CREATE TABLE appreciations (
    -- Unique primary key for the appreciation record
    id VARCHAR(64) PRIMARY KEY,
    -- Colleague giving the appreciation (`REFERENCES users(id)`)
    actor_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Published story being appreciated (`REFERENCES content_items(id)`)
    content_id VARCHAR(64) NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    -- Author of the story receiving the appreciation (`REFERENCES users(id)`)
    recipient_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- ISO Monday date string (e.g., '2026-11-16') of the UTC week when the appreciation occurred
    week_start VARCHAR(16) NOT NULL,
    -- Snapshot of the giver's `function_name` at the moment of appreciation
    actor_team_snapshot VARCHAR(64) NOT NULL,
    -- Snapshot of the receiver's `function_name` at the moment of appreciation
    recipient_team_snapshot VARCHAR(64) NOT NULL,
    -- UTC timestamp when the appreciation occurred
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Idempotency constraint: a user can appreciate a specific story at most once
    CONSTRAINT uq_appreciation_actor_content UNIQUE (actor_id, content_id),
    -- Hard DB guardrail: authors cannot appreciate their own stories!
    CONSTRAINT chk_no_self_appreciation CHECK (actor_id <> recipient_id)
);

-- ============================================================================
-- 5. IMMUTABLE RECOGNITION LEDGER WITH UNIQUE SOURCE KEYS & REVERSALS (CF-004, CF-005)
-- ============================================================================
-- Append-only points ledger. Points are NEVER mutated in-place; instead:
--   - Publishing a challenge story inserts a `+20` row (`reason = 'PUBLISHED_CONTRIBUTION'`).
--   - Withdrawing that story inserts a `-20` compensating row (`reason = 'WITHDRAWN_CONTRIBUTION_REVERSAL'`)
--     with `reversal_of` pointing to the original award row!
--   - Giving an appreciation inserts `+5` (up to 15/week cap) or `+0` once capped.
--   - Receiving an appreciation inserts `+5` (up to 25/week cap) or `+0` once capped.
CREATE TABLE recognition_events (
    -- Unique ledger row identifier
    id VARCHAR(64) PRIMARY KEY,
    -- Colleague whose weekly point balance is affected (`REFERENCES users(id)`)
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Optional link to the challenge if this event came from a challenge entry
    challenge_id VARCHAR(64) NULL REFERENCES challenges(id) ON DELETE SET NULL,
    -- Audience scope of the underlying content item (`REFERENCES audiences(id)`)
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    -- ISO Monday date string (e.g., '2026-11-16') partitioning weekly point totals and caps
    week_start VARCHAR(16) NOT NULL,
    -- Deterministic idempotency key (e.g., 'PUB:2026-11-16:usr_asha:story_99') preventing double-awards
    source_event_id VARCHAR(160) NOT NULL UNIQUE,
    -- Associated content item (`REFERENCES content_items(id)`)
    content_id VARCHAR(64) NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    -- Event classification: 'PUBLISHED_CONTRIBUTION', 'WITHDRAWN_CONTRIBUTION_REVERSAL',
    -- 'APPRECIATION_GIVEN', or 'APPRECIATION_RECEIVED'
    reason VARCHAR(64) NOT NULL,
    -- Signed integer point delta (+20, -20, +5, or 0 when weekly category cap is reached)
    points_delta INT NOT NULL,
    -- For appreciations, records the other colleague involved (giver or receiver)
    counterparty_user_id VARCHAR(64) NULL,
    -- For appreciations, records the counterparty's `function_name` team snapshot
    counterparty_team_snapshot VARCHAR(64) NULL,
    -- For withdrawal reversals, links 1-to-1 to the original `recognition_events(id)` being reversed
    reversal_of VARCHAR(64) NULL UNIQUE REFERENCES recognition_events(id),
    -- UTC timestamp when the ledger entry was recorded
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 6. LEADERBOARD OPT-OUT PREFERENCES (CF-007)
-- ============================================================================
-- Allows any colleague to hide their name from the weekly Campfire leaderboard
-- while still tracking their own private points and badges in `GET /api/v1/recognition/me`.
CREATE TABLE recognition_preferences (
    -- User ID (`PRIMARY KEY REFERENCES users(id)`)
    user_id VARCHAR(64) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    -- When FALSE, the user is excluded from `GET /api/v1/recognition/leaderboard`
    show_on_leaderboard BOOLEAN NOT NULL DEFAULT TRUE,
    -- Timestamp when the preference was last toggled
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 7. WEEKLY BADGES PROJECTION (CF-006, CF-007)
-- ============================================================================
-- Stores unlocked weekly badges per user and audience:
--   1. `FIRST_SPARK` ("First Spark"): 1+ published contribution in the week
--   2. `CONNECTION_MAKER` ("Connection Maker"): Appreciated published stories from >= 3 distinct teams
--   3. `COMMUNITY_FAVOURITE` ("Community Favourite"): Received appreciations from >= 5 distinct colleagues
CREATE TABLE weekly_badges (
    -- Unique badge award ID
    id VARCHAR(64) PRIMARY KEY,
    -- Colleague who earned the badge (`REFERENCES users(id)`)
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- ISO Monday date string (e.g., '2026-11-16') of the UTC week
    week_start VARCHAR(16) NOT NULL,
    -- Audience scope (`REFERENCES audiences(id)`)
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    -- Machine-readable badge code ('FIRST_SPARK', 'CONNECTION_MAKER', 'COMMUNITY_FAVOURITE')
    badge_code VARCHAR(64) NOT NULL,
    -- Human-readable badge title displayed in the UI
    badge_label VARCHAR(80) NOT NULL,
    -- Audit explanation of why the badge was unlocked
    qualifying_evidence VARCHAR(255) NOT NULL,
    -- UTC timestamp when the badge was awarded
    awarded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Prevents duplicate awards of the same badge in the same week and audience
    CONSTRAINT uq_weekly_badge UNIQUE (user_id, week_start, audience_id, badge_code)
);

-- ============================================================================
-- 8. SEED ACTIVE WEEKLY CHALLENGE ("Beyond My Job Title" - Week of 2026-11-16 UTC)
-- ============================================================================
INSERT INTO challenges (id, title, prompt, starts_at, ends_at, timezone, audience_id, status) VALUES
('ch_beyond_01',
 'Beyond My Job Title',
 'Share a hobby, a lesson, or someone who helped you beyond your immediate role. Participation is voluntary.',
 TIMESTAMP WITH TIME ZONE '2026-11-16 00:00:00+00',
 TIMESTAMP WITH TIME ZONE '2026-11-23 00:00:00+00',
 'UTC',
 'aud_pilot',
 'ACTIVE');

-- Seed published challenge entries for Daniel (`story_01`), Elena (`story_06`), and Sophie (`story_03`)
-- so Asha starts at 0 points and can appreciate 3 distinct teams (`Delivery`, `Platform IT`, `Healthcare Delivery`)!
INSERT INTO challenge_entries (id, challenge_id, user_id, content_id, idempotency_key, payload_hash) VALUES
('che_seed_daniel', 'ch_beyond_01', 'usr_daniel', 'story_01', 'idem-ch-seed-daniel', 'seed-hash-daniel'),
('che_seed_elena', 'ch_beyond_01', 'usr_elena', 'story_06', 'idem-ch-seed-elena', 'seed-hash-elena'),
('che_seed_sophie', 'ch_beyond_01', 'usr_sophie', 'story_03', 'idem-ch-seed-sophie', 'seed-hash-sophie');

-- Seed initial +20 `PUBLISHED_CONTRIBUTION` ledger rows for Daniel, Elena, and Sophie in week `2026-11-16`
INSERT INTO recognition_events (id, user_id, challenge_id, audience_id, week_start, source_event_id, content_id, reason, points_delta) VALUES
('rec_seed_daniel', 'usr_daniel', 'ch_beyond_01', 'aud_pilot', '2026-11-16', 'PUB:2026-11-16:usr_daniel:story_01', 'story_01', 'PUBLISHED_CONTRIBUTION', 20),
('rec_seed_elena', 'usr_elena', 'ch_beyond_01', 'aud_pilot', '2026-11-16', 'PUB:2026-11-16:usr_elena:story_06', 'story_06', 'PUBLISHED_CONTRIBUTION', 20),
('rec_seed_sophie', 'usr_sophie', 'ch_beyond_01', 'aud_pilot', '2026-11-16', 'PUB:2026-11-16:usr_sophie:story_03', 'story_03', 'PUBLISHED_CONTRIBUTION', 20);

-- Seed corresponding "First Spark" weekly badges for Daniel, Elena, and Sophie
INSERT INTO weekly_badges (id, user_id, week_start, audience_id, badge_code, badge_label, qualifying_evidence) VALUES
('bdg_seed_daniel', 'usr_daniel', '2026-11-16', 'aud_pilot', 'FIRST_SPARK', 'First Spark', 'Published story_01 in week 2026-11-16'),
('bdg_seed_elena', 'usr_elena', '2026-11-16', 'aud_pilot', 'FIRST_SPARK', 'First Spark', 'Published story_06 in week 2026-11-16'),
('bdg_seed_sophie', 'usr_sophie', '2026-11-16', 'aud_pilot', 'FIRST_SPARK', 'First Spark', 'Published story_03 in week 2026-11-16');

-- Seed default opt-in leaderboard visibility preferences (`show_on_leaderboard = TRUE`) for all 8 pilot users
INSERT INTO recognition_preferences (user_id, show_on_leaderboard) VALUES
('usr_asha', TRUE),
('usr_daniel', TRUE),
('usr_priya', TRUE),
('usr_marcus', TRUE),
('usr_elena', TRUE),
('usr_rohan', TRUE),
('usr_sophie', TRUE),
('usr_carlos', TRUE);
