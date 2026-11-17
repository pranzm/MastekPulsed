# Campfire Integration Contract Specification (`NEW-CONTRACTS.md`)

> **🎓 Intern & AI Agent Guide:**
> This contract freezes the database schema (`V3__campfire_challenges_and_recognition.sql`), REST endpoints, DTO property names, idempotency rules, and error codes for **Mastek Campfire** challenges and community recognition (`CF-002` through `CF-007`).
> Existing `/api/v1` routes, `X-Pulse-User-Id` (gated to synthetic demo mode), and `com.mastek.pulse` Java packages are preserved for compatibility.

---

## 1. Database Records (`V3__campfire_challenges_and_recognition.sql`)

| Record / Table | Primary Fields & Types | Constraints & Invariants |
| :--- | :--- | :--- |
| **`content_items` / `content_revisions` (D2 Visibility Columns)** | `content_items.published_audience_id` (`VARCHAR(64) NULL`), `content_revisions.audience_id` (`VARCHAR(64) NULL`) | Separates the working/draft `audience_id` on an unapproved revision from the immutable `published_audience_id` of the currently live published snapshot. |
| **`challenges`** | `id`, `title`, `prompt`, `starts_at`, `ends_at`, `timezone` (`UTC`), `audience_id`, `status` (`ACTIVE`, `CLOSED`) | `CHECK (ends_at > starts_at)`. One active challenge (`ch_beyond_01` — *"Beyond My Job Title"*) for `aud_pilot`. |
| **`challenge_entries`** | `id`, `challenge_id`, `user_id`, `content_id` (`UNIQUE`), `idempotency_key`, `payload_hash`, `created_at` | `UNIQUE (challenge_id, user_id)` and `UNIQUE (user_id, idempotency_key)`. Ties a challenge submission to the existing `content_items` + `content_revisions` editorial workflow. |
| **`appreciations`** | `id`, `actor_id`, `content_id`, `recipient_id`, `week_start` (`YYYY-MM-DD`), `actor_team_snapshot`, `recipient_team_snapshot`, `occurred_at` | `UNIQUE (actor_id, content_id)` and `CHECK (actor_id <> recipient_id)`. Captures team snapshots at event time so later profile edits never retroactively alter badges. |
| **`recognition_events`** | `id`, `user_id`, `challenge_id`, `audience_id`, `week_start`, `source_event_id` (`UNIQUE`), `content_id`, `reason`, `points_delta`, `counterparty_user_id`, `counterparty_team_snapshot`, `reversal_of` (`UNIQUE`), `created_at` | Immutable ledger. `source_event_id` guarantees idempotent award creation across retries. Withdrawal inserts a compensating negative `points_delta` row with `reversal_of = original_event.id` without deleting history. |
| **`recognition_preferences`** | `user_id` (`PK`), `show_on_leaderboard` (`BOOLEAN DEFAULT TRUE`), `updated_at` | Privacy applied before ranking. Opting out hides the public row on `/recognition/leaderboard` while keeping `/recognition/me` intact. |
| **`weekly_badges`** | `id`, `user_id`, `week_start`, `audience_id`, `badge_code`, `badge_label`, `qualifying_evidence`, `awarded_at` | `UNIQUE (user_id, week_start, audience_id, badge_code)`. Recomputed deterministically from active weekly ledger state (`First Spark`, `Connection Maker`, `Community Favourite`). |

---

## 2. Scoring, Caps, Week Boundary & Reversal Rules

1. **Week Boundary:** Monday `00:00:00 UTC` (`weekStart` formatted as `YYYY-MM-DD`, e.g., `2026-11-16`). Injected `Clock` in Spring Boot enables deterministic boundary tests.
2. **Contribution Award (`+20` points):**
   - Awarded **only** when a Publisher releases an `APPROVED` revision (`status -> PUBLISHED`) for a story linked in `challenge_entries` (or matching the active challenge audience in the current week).
   - Submission (`IN_REVIEW`) and Reviewer Approval (`APPROVED`) earn **0 points**.
   - Capped at **1 published contribution (+20 points) per user per week**. Re-publishing a new revision of an already-awarded story in the same week adds **0 extra points**.
3. **Appreciation Awards (`+5` giver / `+5` receiver):**
   - Requires a `PUBLISHED` story whose `published_audience_id` the caller is permitted to read.
   - Self-appreciation (`actor_id == author_id`) is rejected with `422 VALIDATION_FAILED`.
   - Duplicate `(actor_id, content_id)` call is idempotent: returns `200 OK` with `alreadyAppreciated: true` and `0` additional points.
   - **Giving cap:** `+5` points for each of the first **3** distinct published stories appreciated by `actor_id` in `week_start` (max `15` points/week). Additional appreciations are recorded with `points_delta = 0`.
   - **Receiving cap:** `+5` points for each of the first **5** distinct colleagues (`actor_id`) appreciating `recipient_id`'s published stories in `week_start` (max `25` points/week).
   - **Total Weekly Cap:** Maximum **60 points/week** (`20` contribution + `15` giving + `25` receiving).
4. **Withdrawal & Deterministic Recomputation:**
   - When a published story is withdrawn (`POST /api/v1/content/{id}/withdraw`), all active `recognition_events` tied to `content_id` receive compensating `REVERSAL_WITHDRAWN` ledger rows (`reversal_of = event.id`, `points_delta = -original.points_delta`).
   - If an earlier capped appreciation was reversed, remaining active appreciations in that week are evaluated in deterministic `(occurred_at ASC, id ASC)` order so caps remain exact.

---

## 3. REST API Endpoints & Exact DTO Property Names

| Method & Path | Request / Headers | Response DTO & Status Codes |
| :--- | :--- | :--- |
| **`GET /api/v1/challenges/current`** | `X-Pulse-User-Id` (demo mode) | `200 OK` -> `ChallengeDto`: `{ id, title, prompt, startsAt, endsAt, timezone, weekStart, audienceId, audienceName, status, myEntry: ChallengeEntrySummaryDto \| null }`. Filters by caller's permitted audiences. |
| **`POST /api/v1/challenges/{id}/entries`** | Header: `Idempotency-Key` (required)<br>Body: `{ title, bodyText, submitForReview?: boolean }` | `201 Created` (or `200 OK` on idempotent replay with identical payload) -> `ChallengeEntryDto`: `{ entryId, challengeId, contentId, revisionNumber, status, title, summary, bodyText, audienceId, pointsAwarded: 0 }`.<br>Errors: `401`, `403 FORBIDDEN_ACCESS` (wrong audience), `409 CHALLENGE_CLOSED` / `DUPLICATE_ENTRY` / `IDEMPOTENCY_CONFLICT` (same key with different body), `422 VALIDATION_FAILED` (title < 5 or > 100 chars; body < 30 or > 1200 chars). |
| **`POST /api/v1/content/{id}/appreciations`** | Header: `Idempotency-Key` (optional/supported) | `200 OK` -> `AppreciationResultDto`: `{ appreciationId, contentId, actorId, recipientId, weekStart, alreadyAppreciated, giverPointsDelta, receiverPointsDelta, actorProgress: RecognitionProgressDto }`.<br>Errors: `401`, `403 FORBIDDEN_ACCESS`, `404 RESOURCE_NOT_FOUND` (unpublished or withdrawn story), `422 VALIDATION_FAILED` (self-appreciation). |
| **`GET /api/v1/recognition/me`** | Query: `?week=YYYY-MM-DD` (optional, defaults to current UTC Monday) | `200 OK` -> `RecognitionProgressDto`: `{ userId, displayName, weekStart, timezone, points, contributionPoints, givenCount, givingPoints, receivedDistinctCount, receivingPoints, distinctTeamsSupported, badges: string[], showOnLeaderboard: boolean }`. |
| **`GET /api/v1/recognition/leaderboard`** | Query: `?week=YYYY-MM-DD&audienceId=aud_pilot` | `200 OK` -> `LeaderboardResponseDto`: `{ weekStart, timezone, audienceId, entries: LeaderboardRowDto[] }` where each `LeaderboardRowDto` is `{ rank, userId, displayName, functionName, region, points, badges: string[] }`. Top 5 opted-in users (`showOnLeaderboard == true`) computed strictly from events in `audienceId`; tied scores share `rank`, ordered alphabetically by `displayName`. |
| **`PUT /api/v1/recognition/preferences`** | Body: `{ showOnLeaderboard: boolean }` | `200 OK` -> `RecognitionProgressDto` with updated `showOnLeaderboard`. Explicit idempotent state (`PUT`), never a blind toggle. |
