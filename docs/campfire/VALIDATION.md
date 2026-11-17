# Validation — 17 November 2026

## 1. Full-Stack Backend + Database Verification (Java 21 + Spring Boot 3.3.5 + PostgreSQL 16.16)
- **Flyway Schema & Migration Parity (`V1`, `V2`, `V3`):**
  - Verified untouched `V1__init_schema.sql` and `V2__seed_pilot_data.sql` plus new additive `V3__campfire_challenges_and_recognition.sql` execute cleanly on both H2 (`MODE=PostgreSQL`) and live Docker PostgreSQL 16 (`postgres:16-alpine`, `PostgreSQL 16.16`).
- **Automated JUnit 5 Suite (`7/7` test methods passing on both H2 and PostgreSQL 16):**
  - `PulseIntegrationAndAiGatesTest` (`4/4` passing):
    - Primary connection journey (`POST /api/v1/requests`, `PATCH /api/v1/requests/{id}` with `SELECT ... FOR UPDATE` pessimistic capacity lock, bilateral usefulness check).
    - Role-scoped portal endpoints (`403` for ordinary employees on `/api/v1/portal/queue` and `/api/v1/portal/audit-log`), audience-scoped editorial actions, self-approval block (`403`), revision-bound review (`409` on stale revision or wrong state), draft edit isolation from published snapshot (`published_version` and `published_audience_id`), and `/api/v1/content/{id}/preview` vs published-only `/api/v1/content/{id}`.
    - Cross-device audio playback resume (`PUT /api/v1/listen/progress`).
    - 30-case synthetic AI evaluation gate (10 permission, 10 refusal, 10 faithfulness — 0 unauthorized disclosures, 100% faithfulness, published-only Saathi summary context, and 720-action budget cap).
  - `CampfireChallengeAndRecognitionTest` (`3/3` passing):
    - Weekly challenge read (`GET /api/v1/challenges/current`), idempotent challenge entry submission (`POST /api/v1/challenges/{id}/entries`), `0` points on submit and reviewer approval, `+20` points (`CONTRIBUTION_PUBLISHED`) awarded synchronously on `publishNow()` and scheduled `OutboxWorkerService` publication, idempotent replay protection, and `-20` reversal (`REVERSAL_WITHDRAWN`) when withdrawn.
    - Colleague appreciation (`POST /api/v1/content/{id}/appreciations`) blocking self/unpublished/duplicate appreciation, enforcing `+5` giver cap (`3` stories / `15` pts) and `+5` author cap (`5` distinct colleagues / `25` pts), unlocking `FIRST_SPARK`, `CONNECTION_MAKER` (with immutable event-time `giver_team`/`author_team` snapshot), and `COMMUNITY_FAVOURITE` (`5` distinct appreciators), and verifying opt-out (`PUT /api/v1/recognition/preferences`) hides the user from `GET /api/v1/recognition/leaderboard` while preserving private points in `GET /api/v1/recognition/me`.
    - UTC Monday `00:00:00Z` weekly reset boundary verified deterministically via `PulseClock`.

## 2. Frontend & Standalone Browser Engine Verification
- **Eleven Node tests pass (`npm --prefix web run test:campfire` — `11/11`):** publication award timing; role/revision enforcement; returned revisions; duplicate/self appreciation; summary visibility; giving cap; cross-team badge; withdrawal reversal; opt-out; UTC week boundary; duplicate/pending submissions; five-distinct-person recognition.
- **TypeScript & Vite Build (`npm --prefix web run build`):** Passed with zero TypeScript or bundler errors.
- **Explicit Transport & Loading Discipline (`CF-003`, `CF-011`):**
  - `web/src/api/apiClient.ts` supports explicit `demo` and `live` modes (`VITE_APP_MODE=demo|live` and top-bar switcher).
  - Live mode surfaces typed `PulseHttpError(status, code, message)` directly in the UI with a Retry action and never silently falls back to local synthetic state on HTTP errors.
  - Viewing the standalone `/campfire/index.html` surface makes zero background `/api/v1` calls.
  - Switching personas clears persona-scoped state and requests `/api/v1/portal/*` only when the authenticated persona holds a portal role (`CREATOR`, `REVIEWER`, `PUBLISHER`, `MODERATOR`, `ADMIN`).

## 3. Known Limitations & Remaining Scope
- The external `voice-service/` branch (Whisper/Piper/Azure Foundry/Content Safety) referenced in the developer handover was not present in the repository or archives (`CF-008`, `CF-009` remain blocked until that branch is supplied).
- Full multi-browser accessibility audit and physical mobile device testing remain deferred to pilot readiness.
