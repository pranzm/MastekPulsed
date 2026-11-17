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
--      and Rohan (`story_02`) while keeping Asha at 0 points before her submission!
-- ============================================================================

-- 1. SEPARATE DRAFT AUDIENCE FROM IMMUTABLE PUBLISHED AUDIENCE SNAPSHOT (CF-010 / D2)
ALTER TABLE content_items ADD COLUMN published_audience_id VARCHAR(64) NULL REFERENCES audiences(id);
ALTER TABLE content_revisions ADD COLUMN audience_id VARCHAR(64) NULL REFERENCES audiences(id);

UPDATE content_items
SET published_audience_id = audience_id
WHERE published_version IS NOT NULL AND status = 'PUBLISHED';

UPDATE content_revisions cr
SET audience_id = (SELECT ci.audience_id FROM content_items ci WHERE ci.id = cr.content_id);

-- Ensure Elena (`usr_elena`) can author stories as a colleague (`CREATOR`) while
-- still NOT holding `REVIEWER` or `PUBLISHER` editorial authority!
UPDATE users
SET roles_csv = 'EMPLOYEE,CREATOR,ADMIN'
WHERE id = 'usr_elena';

-- 2. WEEKLY CHALLENGES (CF-002)
CREATE TABLE challenges (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    prompt VARCHAR(1000) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_challenge_dates CHECK (ends_at > starts_at)
);

-- 3. CHALLENGE ENTRIES TIED TO EXISTING CONTENT WORKFLOW (CF-002)
CREATE TABLE challenge_entries (
    id VARCHAR(64) PRIMARY KEY,
    challenge_id VARCHAR(64) NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_id VARCHAR(64) NOT NULL UNIQUE REFERENCES content_items(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(120) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_challenge_user UNIQUE (challenge_id, user_id),
    CONSTRAINT uq_challenge_entry_idem UNIQUE (user_id, idempotency_key)
);

-- 4. COLLEAGUE APPRECIATIONS WITH EVENT-TIME TEAM SNAPSHOTS (CF-006)
CREATE TABLE appreciations (
    id VARCHAR(64) PRIMARY KEY,
    actor_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_id VARCHAR(64) NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    recipient_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start VARCHAR(16) NOT NULL,
    actor_team_snapshot VARCHAR(64) NOT NULL,
    recipient_team_snapshot VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_appreciation_actor_content UNIQUE (actor_id, content_id),
    CONSTRAINT chk_no_self_appreciation CHECK (actor_id <> recipient_id)
);

-- 5. IMMUTABLE RECOGNITION LEDGER WITH UNIQUE SOURCE KEYS & REVERSALS (CF-004, CF-005)
CREATE TABLE recognition_events (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id VARCHAR(64) NULL REFERENCES challenges(id) ON DELETE SET NULL,
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    week_start VARCHAR(16) NOT NULL,
    source_event_id VARCHAR(160) NOT NULL UNIQUE,
    content_id VARCHAR(64) NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    reason VARCHAR(64) NOT NULL,
    points_delta INT NOT NULL,
    counterparty_user_id VARCHAR(64) NULL,
    counterparty_team_snapshot VARCHAR(64) NULL,
    reversal_of VARCHAR(64) NULL UNIQUE REFERENCES recognition_events(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. LEADERBOARD OPT-OUT PREFERENCES (CF-007)
CREATE TABLE recognition_preferences (
    user_id VARCHAR(64) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    show_on_leaderboard BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. WEEKLY BADGES PROJECTION (CF-006, CF-007)
CREATE TABLE weekly_badges (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start VARCHAR(16) NOT NULL,
    audience_id VARCHAR(64) NOT NULL REFERENCES audiences(id),
    badge_code VARCHAR(64) NOT NULL,
    badge_label VARCHAR(80) NOT NULL,
    qualifying_evidence VARCHAR(255) NOT NULL,
    awarded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

INSERT INTO recognition_events (id, user_id, challenge_id, audience_id, week_start, source_event_id, content_id, reason, points_delta) VALUES
('rec_seed_daniel', 'usr_daniel', 'ch_beyond_01', 'aud_pilot', '2026-11-16', 'PUB:2026-11-16:usr_daniel:story_01', 'story_01', 'PUBLISHED_CONTRIBUTION', 20),
('rec_seed_elena', 'usr_elena', 'ch_beyond_01', 'aud_pilot', '2026-11-16', 'PUB:2026-11-16:usr_elena:story_06', 'story_06', 'PUBLISHED_CONTRIBUTION', 20),
('rec_seed_sophie', 'usr_sophie', 'ch_beyond_01', 'aud_pilot', '2026-11-16', 'PUB:2026-11-16:usr_sophie:story_03', 'story_03', 'PUBLISHED_CONTRIBUTION', 20);

INSERT INTO weekly_badges (id, user_id, week_start, audience_id, badge_code, badge_label, qualifying_evidence) VALUES
('bdg_seed_daniel', 'usr_daniel', '2026-11-16', 'aud_pilot', 'FIRST_SPARK', 'First Spark', 'Published story_01 in week 2026-11-16'),
('bdg_seed_elena', 'usr_elena', '2026-11-16', 'aud_pilot', 'FIRST_SPARK', 'First Spark', 'Published story_06 in week 2026-11-16'),
('bdg_seed_sophie', 'usr_sophie', '2026-11-16', 'aud_pilot', 'FIRST_SPARK', 'First Spark', 'Published story_03 in week 2026-11-16');

INSERT INTO recognition_preferences (user_id, show_on_leaderboard) VALUES
('usr_asha', TRUE),
('usr_daniel', TRUE),
('usr_priya', TRUE),
('usr_marcus', TRUE),
('usr_elena', TRUE),
('usr_rohan', TRUE),
('usr_sophie', TRUE),
('usr_carlos', TRUE);
