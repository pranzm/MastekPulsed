# Mastek Campfire — AI-DLC Phase 2C: Complete Project Code Flow & ASCII Architecture Diagrams

> **🎓 Intern & AI Agent Guide — How to Use This Document:**
> This document is the **visual map of the entire Mastek Campfire codebase**. Every diagram is written in **pure ASCII** so it renders identically in terminals, IDEs, `git diff`, GitHub PRs, and AI agent context windows (Claude Code, OpenAI Codex, GitHub Copilot).
>
> **Legend used across all diagrams:**
> - `[Class.method()]` — Exact Java class / TypeScript function owning that step
> - `==>` — Happy-path execution or return value
> - `--x` — Guardrail rejection with exact HTTP status (`401`, `403`, `404`, `409`, `422`)
> - `+--- TX (@Transactional) ---+` — Atomic PostgreSQL transaction boundary (everything inside commits or rolls back together)
> - `[LOCK: SELECT ... FOR UPDATE]` — Pessimistic row-level database lock (`LockModeType.PESSIMISTIC_WRITE`)

---

## Table of Contents
1. [Master System Architecture & Data Flow Overview](#1-master-system-architecture--data-flow-overview)
2. [Complete Codebase File & Dependency Map](#2-complete-codebase-file--dependency-map)
3. [Frontend Startup, Role-Aware Loading & Explicit `LIVE` vs `DEMO` Transport Flow](#3-frontend-startup-role-aware-loading--explicit-live-vs-demo-transport-flow)
4. [Database Startup, Flyway (`V1`->`V2`->`V3`) & Dual H2/PostgreSQL 16 Flow](#4-database-startup-flyway-v1-v2-v3--dual-h2postgresql-16-flow)
5. [Published Snapshot Isolation vs. Mutable Drafts (`CF-004` & `CF-010`)](#5-published-snapshot-isolation-vs-mutable-drafts-cf-004--cf-010)
6. [Campfire Weekly Challenge -> Human Editorial Review -> `+20` Publication & `-20` Withdrawal Flow (`CF-002`, `CF-004`, `CF-005`)](#6-campfire-weekly-challenge---human-editorial-review---20-publication--20-withdrawal-flow-cf-002-cf-004-cf-005)
7. [Colleague Appreciation (`+5`/`+5`), Team Snapshots, Badges & Opt-In Leaderboard Flow (`CF-006`, `CF-007`)](#7-colleague-appreciation-55-team-snapshots-badges--opt-in-leaderboard-flow-cf-006-cf-007)
8. [Primary Connection Journey: Filter-Rank-Explain -> Idempotent Invite -> Locked Accept -> Bilateral Check-In](#8-primary-connection-journey-filter-rank-explain---idempotent-invite---locked-accept---bilateral-check-in)
9. [Audio Streaming (Mastek Radio & Culture Cast) & Cross-Device Playback Resume Flow](#9-audio-streaming-mastek-radio--culture-cast--cross-device-playback-resume-flow)
10. [Saathi AI Assistance, Published-Only Summaries, 30-Case Trust Gate & 720-Action Budget Cap](#10-saathi-ai-assistance-published-only-summaries-30-case-trust-gate--720-action-budget-cap)
11. [Complete PostgreSQL 16 Entity-Relationship (ER) Diagram (`V1` + `V2` + `V3` — 19 Tables)](#11-complete-postgresql-16-entity-relationship-er-diagram-v1--v2--v3--19-tables)
12. [End-to-End 4-Persona Sequence Diagram (Asha, Priya, Marcus, Daniel)](#12-end-to-end-4-persona-sequence-diagram-asha-priya-marcus-daniel)

---

## 1. Master System Architecture & Data Flow Overview

```text
+================================================================================================================+
|                                  PRESENTATION LAYER (4 Client Surfaces)                                        |
|                                                                                                                |
|  +-------------------------+  +-------------------------+  +------------------------+  +--------------------+  |
|  | 1. Employee Web         |  | 2. Publishing Portal    |  | 3. Standalone Campfire |  | 4. Mobile App      |  |
|  | EmployeeWorkspace.tsx   |  | PublishingPortal...tsx  |  | /campfire/index.html   |  | mobile/App.tsx     |  |
|  | ----------------------- |  | ----------------------- |  | ---------------------- |  | ------------------ |  |
|  | * Weekly Challenge      |  | * Scoped Review Queue   |  | * Zero /api/v1 calls   |  | * 5 Bottom Tabs    |  |
|  | * Your Pulse Feed       |  | * Revision Inspector    |  | * Pure browser engine  |  | * Native Audio     |  |
|  | * Appreciate (+5 pts)   |  | * Approve / Return      |  |   (engine.mjs)         |  |   (expo-av)        |  |
|  | * Progress & Champions  |  | * Publish (+20) / Sched |  | * 11 Node unit tests   |  | * Cross-device     |  |
|  | * 15-m Connect & Listen |  | * Withdraw (-20) & Audit|  |   (engine.test.mjs)    |  |   resume           |  |
|  +------------+------------+  +------------+------------+  +------------------------+  +---------+----------+  |
|               |                            |                                                     |             |
|               +--------------+-------------+-----------------------------------------------------+             |
|                              |                                                                                 |
|                              v                                                                                 |
|  +----------------------------------------------------------------------------------------------------------+  |
|  | EXPLICIT TRANSPORT ROUTER: web/src/api/apiClient.ts  (contracts: web/src/api/contracts.ts)               |  |
|  |   * Mode = "live" (VITE_APP_MODE=live)  --> Calls Spring Boot /api/v1/*; throws PulseHttpError on error  |  |
|  |   * Mode = "demo" (VITE_APP_MODE=demo)  --> Calls deterministic in-browser state machine (0 network I/O) |  |
|  |   * Role-Aware Loader (App.tsx)         --> Calls /me first; calls /portal/* ONLY if user has portal role|  |
|  +----------------------------------------------------+-----------------------------------------------------+  |
+=======================================================|========================================================+
                                                        |
                                   HTTPS /api/v1/*      |  Headers: X-Pulse-User-Id: usr_asha
                                   (JSON DTOs)          |           Idempotency-Key: <uuid>
                                                        v
+================================================================================================================+
|                   BACKEND LAYER: SPRING BOOT 3.3.5 + JAVA 21 VIRTUAL THREADS (`backend/`)                      |
|                                                                                                                |
|  +----------------------------------------------------------------------------------------------------------+  |
|  | API BOUNDARY & IDENTITY GUARD (`com.mastek.pulse.common`)                                                |  |
|  |   * IdentityService.requireUser(userId) / requireRole(...) / requirePortalRole(...) / requireAudience... |  |
|  |   * ApiException + GlobalExceptionHandler -> { status, code, message, timestamp } (401/403/404/409/422)  |  |
|  |   * PulseClock -> Injectable UTC clock (Monday 00:00:00Z week calculation & deterministic testing)       |  |
|  +-----+---------------+---------------+---------------+---------------+---------------+--------------+-----+  |
|        |               |               |               |               |               |              |        |
|        v               v               v               v               v               v              v        |
|  +-----------+   +-----------+   +-----------+   +-----------+   +-----------+   +-----------+  +-----------+  |
|  | challenge |   |  content  |   |recognition|   |connection |   |  listen   |   |    ai     |  |  outbox   |  |
|  | --------- |   | --------- |   | --------- |   | --------- |   | --------- |   | --------- |  | --------- |  |
|  | * Current |   | * Feed    |   | * +20 on  |   | * Invite  |   | * Radio   |   | * Drafts  |  | * Polls   |  |
|  |   Prompt  |   |   Filter/ |   |   Publish |   |   Request |   |   Queue   |   | * Saathi  |  |   due     |  |
|  | * Idemp.  |-->|   Rank/Why|-->| * -20 on  |   | * FOR     |   | * Culture |   |   Summary |  |   items   |  |
|  |   Entry   |   | * Preview |   |   Withdraw|   |   UPDATE  |   |   Cast    |   |   (Pub-   |  | * Publishes|
|  |   Submit  |   | * Review/ |   | * +5/+5   |   |   Accept  |   | * Resume  |   |   only!)  |  |   & awards|  |
|  |           |   |   Publish/|   |   Apprec. |   | * Bilat.  |   |   Sync    |   | * 720-Cap |  |   +20 pts |  |
|  |           |   |   Withdraw|   | * 3 Badges|   |   Checkin |   |           |   | * Fallback|  |           |  |
|  +-----+-----+   +-----+-----+   +-----+-----+   +-----+-----+   +-----+-----+   +-----+-----+  +-----+-----+  |
+========|===============|===============|===============|===============|===============|==============|========+
         |               |               |               |               |               |              |
         +---------------+---------------+---------------+---------------+---------------+--------------+
                                                         |
                                            JPA / HikariCP / JDBC
                                                         v
+================================================================================================================+
|                   PERSISTENCE LAYER: POSTGRESQL 16.16 (or H2 `MODE=PostgreSQL` in local test)                  |
|                                                                                                                |
|  * PostgresCompatibilityConfig.java -> Registers `public.dateadd` on PG before Flyway migrates                 |
|  * V1__init_schema.sql              -> 13 Core Pulse tables (users, audiences, content_items, revisions, ...)  |
|  * V2__seed_pilot_data.sql          -> 5 Personas, 3 Audiences, 1 Radio Queue, 3 Podcasts, 6 Updates, 12 Stories|
|  * V3__campfire_challenges_...sql   -> 6 Campfire tables (challenges, challenge_entries, appreciations,        |
|                                        recognition_events, recognition_preferences, weekly_badges) +           |
|                                        published_audience_id & challenge_id on content_items + users.team      |
+================================================================================================================+
```

---

## 2. Complete Codebase File & Dependency Map

Every file in the repository has a specific responsibility in the call chain:

```text
FRONTEND (`web/`)
=================
web/src/main.tsx
  `--> web/src/App.tsx  (Top bar, Persona Switcher, LIVE/DEMO mode toggle, Surface Switcher, Audio Bar)
         |-- [Surface = EMPLOYEE] --> web/src/employee/EmployeeWorkspace.tsx
         |                              |-- Tab: PULSE      (Weekly Challenge, Recognition & Champions, Feed, Story Modal, Appreciate, Saathi Summary, Invite)
         |                              |-- Tab: LISTEN     (Mastek Radio Queue, Culture Cast Chapters & Transcripts)
         |                              |-- Tab: CONNECT    (Host Inbox Accept/Decline, Sent Requests, Private Bilateral Check-In)
         |                              |-- Tab: CONTRIBUTE (Draft Composer, Local Auto-Save, Submit to Review)
         |                              `-- Tab: PROFILE    (Audience Memberships, Interests, Notification Preferences)
         |
         |-- [Surface = PORTAL]   --> web/src/portal/PublishingPortalWorkspace.tsx
         |                              |-- Tab: DASHBOARD  (Pilot Success Metrics, Recognition Leaderboard, AI 720-Budget Gate)
         |                              |-- Tab: QUEUE      (Editorial Review Queue, Revision Inspector, Approve/Request Changes, Publish/Schedule/Withdraw)
         |                              `-- Tab: AUDIT      (Immutable Compliance & Security Audit Trail)
         |
         `-- [Surface = CAMPFIRE] --> web/public/campfire/index.html
                                        `--> web/public/campfire/app.mjs --> web/public/campfire/engine.mjs

SHARED API LAYER (`web/src/api/`)
=================================
web/src/api/contracts.ts   <-- 1:1 TypeScript mirror of `docs/aidlc/openapi.yaml` and `docs/campfire/NEW-CONTRACTS.md`
web/src/api/apiClient.ts   <-- Explicit `demo` | `live` transport client (`PulseHttpError` on live failures)

BACKEND (`backend/src/main/java/com/mastek/pulse/`)
===================================================
common/
  |-- PulseApplication.java              (@SpringBootApplication, @EnableScheduling)
  |-- PostgresCompatibilityConfig.java   (BeanPostProcessor: ensures `public.dateadd` exists on PG16 before Flyway)
  |-- PulseClock.java                    (UTC Clock wrapper + `currentWeekStart(Instant)` -> Monday 00:00:00Z)
  |-- IdentityController.java            (`GET /api/v1/me`, `GET /api/v1/personas`)
  |-- IdentityService.java               (Auth boundary, `requireRole`, `requirePortalRole`, `requireAudienceAccess`, `writeAudit`)
  |-- ApiException.java & GlobalExceptionHandler.java (Standardized `{status, code, message, timestamp}` JSON responses)
  `-- Entities & Repos: UserEntity, UserRepository, AudienceEntity, AudienceMemberEntity, UserInterestEntity, AuditEventEntity

content/
  |-- ContentController.java             (`/feed`, `/content/{id}`, `/content/{id}/preview`, `/content`, `/revisions`, `/submit`, `/review`, `/publish`, `/withdraw`, `/portal/*`)
  |-- FeedService.java                   (Filter by `published_audience_id` -> Rank by interests/capacity -> Explain `WHY THIS?` + Appreciation & Challenge badges)
  |-- PublishingWorkflowService.java     (Draft -> Submit -> Review -> Publish/Schedule -> Withdraw; locks item with `FOR UPDATE`; triggers `RecognitionService` + `ChallengeService`)
  `-- Entities & Repos: ContentItemEntity, ContentItemRepository, ContentRevisionEntity, ContentRevisionRepository

challenge/
  |-- ChallengeController.java           (`GET /api/v1/challenges/current`, `POST /api/v1/challenges/{id}/entries`)
  |-- ChallengeService.java              (Resolves active challenge + caller's submission; creates/updates story + submits to `PublishingWorkflowService` + records `ChallengeEntryEntity`)
  `-- Entities & Repos: ChallengeEntity, ChallengeRepository, ChallengeEntryEntity, ChallengeEntryRepository

recognition/
  |-- RecognitionController.java         (`POST /api/v1/content/{id}/appreciations`, `GET /api/v1/recognition/me`, `GET /api/v1/recognition/leaderboard`, `PUT /api/v1/recognition/preferences`)
  |-- RecognitionService.java            (Transactional ledger: `+20` publish, `-20` withdraw reversal, `+5`/`+5` appreciation with weekly caps, 3 badges, opt-in leaderboard)
  `-- Entities & Repos: RecognitionEventEntity, AppreciationEntity, RecognitionPreferenceEntity, WeeklyBadgeEntity (+ Repositories)

connection/
  |-- ConnectionController.java          (`POST /api/v1/requests`, `GET /api/v1/requests`, `PATCH /api/v1/requests/{id}`, `POST /confirmations`, `PATCH /invitations/{id}/availability`)
  |-- ConnectionService.java             (Idempotent request creation, 1-Transaction Pessimistic Locked Accept Flow, Bilateral Usefulness calculation)
  `-- Entities & Repos: InvitationEntity, InvitationRepository, ConnectionRequestEntity, ParticipantConfirmationEntity

listen/
  |-- ListenController.java              (`GET /api/v1/listen/radio`, `GET /api/v1/listen/culture-cast`, `PUT /api/v1/listen/progress`)
  |-- ListenService.java                 (Curated commute queue, chapters/transcripts, signed URLs, cross-device playback resume)
  `-- Entities & Repos: PlaybackProgressEntity, PlaybackProgressRepository

ai/
  |-- AiController.java                  (`POST /api/v1/ai/drafts`, `POST /api/v1/ai/summaries`, `GET /api/v1/ai/budget`)
  |-- AiAssistanceService.java           (30-case safety/refusal filter, published-only summary enforcement, 720-action budget ledger, deterministic template fallback)
  `-- Entities & Repos: AiUsageLedgerEntity, AiUsageLedgerRepository

outbox/
  |-- OutboxWorkerService.java           (`@Scheduled` poller: promotes due `SCHEDULED` items to `PUBLISHED`, awards `+20` points via `RecognitionService`, dispatches notifications)
  `-- Entities & Repos: OutboxEventEntity, OutboxEventRepository
```

---

## 3. Frontend Startup, Role-Aware Loading & Explicit `LIVE` vs `DEMO` Transport Flow

This diagram shows how [`web/src/App.tsx`](../../web/src/App.tsx) and [`web/src/api/apiClient.ts`](../../web/src/api/apiClient.ts) implement `D1` (`CF-003`, `CF-011`):

```text
 Browser loads http://localhost:5173
   |
   v
 [App.tsx mounts]
   |-- Reads initial `apiMode` from `apiClient.getApiMode()`
   |     * Checks `localStorage.getItem("campfire_api_mode")`
   |     * Or `import.meta.env.VITE_APP_MODE` ("live" | "demo")
   |     * Defaults to "demo" if unset
   |
   |-- Checks `surfaceMode` ("EMPLOYEE" | "PORTAL" | "CAMPFIRE")
   |     |
   |     +-- If `surfaceMode === "CAMPFIRE"`:
   |     |     * Renders `<iframe src="/campfire/index.html" />`
   |     |     * `loadBaseData()` returns IMMEDIATELY (ZERO `/api/v1/*` network calls!)
   |     |
   |     `-- If `surfaceMode === "EMPLOYEE"` or `"PORTAL"`:
   |           Calls `loadBaseData()`
   v
 +--------------------------------------------------------------------------------------------------+
 | `loadBaseData()` in `web/src/App.tsx`                                                            |
 | ------------------------------------------------------------------------------------------------ |
 | STEP 1: Fetch Identity & Core Employee Data in Parallel                                          |
 |   Promise.all([                                                                                  |
 |     apiClient.getMe(),                     --> GET /api/v1/me                                    |
 |     apiClient.getPersonas(),               --> GET /api/v1/personas                              |
 |     apiClient.getRadioQueue(),             --> GET /api/v1/listen/radio                          |
 |     apiClient.getCultureCastEpisodes()     --> GET /api/v1/listen/culture-cast                   |
 |   ])                                                                                             |
 |                                                                                                  |
 | STEP 2: Role-Aware Gate (Prevents 403 Forbidden spam for ordinary employees!)                    |
 |   canAccessPortal = me.roles.some(r => ["CREATOR","REVIEWER","PUBLISHER","MODERATOR","ADMIN"]    |
 |                                          .includes(r))                                           |
 |   * If `canAccessPortal == false` (e.g., Asha Verma `usr_asha` has only `["EMPLOYEE"]`):         |
 |       --> Do NOT call `/api/v1/portal/*`                                                         |
 |       --> Hide/disable the "Publishing Portal" button in the top navigation bar                  |
 |   * If `canAccessPortal == true` (Daniel, Priya, Marcus, Elena):                                 |
 |       --> Enable "Publishing Portal" workspace                                                   |
 +--------------------------------------------------------------------------------------------------+
   |
   v
 +--------------------------------------------------------------------------------------------------+
 | Inside `apiClient.request<T>(path, options, demoFallback)` (`web/src/api/apiClient.ts`)          |
 | ------------------------------------------------------------------------------------------------ |
 |                                                                                                  |
 |                         +-------------------------------+                                        |
 |                         | What is `currentApiMode`?     |                                        |
 |                         +-------+---------------+-------+                                        |
 |                                 |               |                                                |
 |                 "demo"          |               |          "live"                                |
 |         +-----------------------+               +-----------------------+                        |
 |         v                                                               v                        |
 |  +-------------------------------------+         +--------------------------------------------+  |
 |  | Execute `demoFallback()` in memory  |         | `fetch("/api/v1" + path, {                 |  |
 |  | * Zero HTTP requests                |         |    headers: { "X-Pulse-User-Id": persona } |  |
 |  | * Enforces same state rules, caps,  |         |  })`                                       |  |
 |  |   badges, and 403/409 errors        |         +--------------+---------------+-------------+  |
 |  +-------------------------------------+                        |               |                |
 |                                                       HTTP 2xx  |               | HTTP 4xx/5xx   |
 |                                                                 v               v or Network Err |
 |                                                  +------------------+   +---------------------+  |
 |                                                  | Return parsed    |   | Throw typed         |  |
 |                                                  | JSON DTO `T`     |   | `PulseHttpError`    |  |
 |                                                  +------------------+   | (status, code, msg) |  |
 |                                                                         | * NEVER falls back  |  |
 |                                                                         |   to demo state!    |  |
 |                                                                         +----------+----------+  |
 |                                                                                    |             |
 |                                                                                    v             |
 |                                                                   UI displays red LIVE API ERROR |
 |                                                                   banner with [Retry] button     |
 +--------------------------------------------------------------------------------------------------+
```

---

## 4. Database Startup, Flyway (`V1`->`V2`->`V3`) & Dual H2/PostgreSQL 16 Flow

How the backend boots cleanly on both **H2 (`MODE=PostgreSQL`)** and **Docker PostgreSQL 16 (`postgres:16-alpine`)** without touching `V1` or `V2`:

```text
 `mvn spring-boot:run` (H2)   OR   `mvn spring-boot:run -Dspring-boot.run.profiles=postgres` (PG 16)
   |
   v
 [PostgresCompatibilityConfig.postProcessBeforeInitialization(bean, "flyway")]
   |-- Inspects `dataSource.getConnection().getMetaData().getDatabaseProductName()`
   |     |-- If "H2":
   |     |     H2 already has built-in `DATEADD('HOUR', -6, CURRENT_TIMESTAMP)` --> no-op
   |     `-- If "PostgreSQL":
   |           Executes `CREATE OR REPLACE FUNCTION public.dateadd(unit text, amount int, ts timestamptz)`
   |           so untouched `V2__seed_pilot_data.sql` runs natively on PostgreSQL 16!
   v
 [Flyway.migrate()]  (`baseline-on-migrate: true`, `baseline-version: 0`)
   |
   |-- Runs `V1__init_schema.sql` (Checksum verified, untouched):
   |     Creates: users, audiences, audience_members, user_interests, content_items,
   |              content_revisions, invitations, connection_requests,
   |              participant_confirmations, playback_progress, ai_usage_ledger,
   |              audit_events, outbox_events
   |
   |-- Runs `V2__seed_pilot_data.sql` (Checksum verified, untouched):
   |     Seeds:   5 personas (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`),
   |              3 audiences (`aud_all`, `aud_pilot`, `aud_uk_delivery`),
   |              1 Radio queue (`radio_q_01`), 3 Culture Cast episodes (`cc_ep_01`..`03`),
   |              6 Updates (`upd_01`..`06`), 12 Contributions (`story_01`..`12`),
   |              Invitations, Connection Requests, Confirmations, AI Ledger rows
   |
   `-- Runs `V3__campfire_challenges_and_recognition.sql` (Additive Campfire migration):
         1. ALTERs `users` -> adds `team` (`India Delivery`, `UK Delivery`, `Internal Comms`, `Platform Admin`)
         2. ALTERs `content_items` -> adds `published_audience_id` & `challenge_id`
         3. ALTERs `content_revisions` -> adds `audience_id` per revision snapshot
         4. Grants `usr_asha` `CREATOR` role so Asha can submit weekly challenge stories in live demos
         5. Creates `challenges`, `challenge_entries`, `appreciations`, `recognition_events`,
            `recognition_preferences`, `weekly_badges`
         6. Seeds active challenge `chal_2026_w47` ("One small win that helped a teammate this week")
```

---

## 5. Published Snapshot Isolation vs. Mutable Drafts (`CF-004` & `CF-010`)

One of the most important architectural guardrails in Mastek Campfire is **Published Snapshot Isolation**. Here is how `content_items` and `content_revisions` prevent draft edits or audience changes from leaking to employees before publication:

```text
+===================================================================================================+
| EXAMPLE: Culture Cast Episode 04 (`cc_ep_03`) or an Edited Story                                  |
|                                                                                                   |
|  `content_items` row (`id = 'cc_ep_03'`):                                                         |
|  +-----------------------+-------------------------+-------------------+-----------------------+  |
|  | published_version = 2 | published_audience_id   | current_version=3 | audience_id (draft)   |  |
|  | (Live snapshot!)      | = 'aud_all' (Live aud!) | (In review!)      | = 'aud_uk_delivery'   |  |
|  +-----------+-----------+------------+------------+---------+---------+-----------+-----------+  |
|              |                        |                      |                     |              |
|              +-----------+------------+                      +----------+----------+              |
|                          |                                              |                         |
|                          v                                              v                         |
|  `content_revisions` (`version_number = 2`):    `content_revisions` (`version_number = 3`):       |
|  * title: "Ep 04: Quiet Leadership"             * title: "Ep 04: Quiet Leadership (Extended Cut)" |
|  * audience_id: "aud_all"                       * audience_id: "aud_uk_delivery"                  |
|  * approved_by: "usr_priya"                     * approved_by: NULL (Waiting for review!)         |
+==========================|==============================================|=========================+
                           |                                              |
         +-----------------+-----------------+                            |
         |                                   |                            |
         v                                   v                            v
+--------------------+             +--------------------+       +-----------------------------------+
| Employee Feed &    |             | Saathi AI Summary  |       | Owner / Reviewer Preview          |
| Story Detail       |             |POST /ai/summaries  |       | GET /api/v1/content/{id}/preview  |
| GET /api/v1/feed   |             | ------------------ |       | & GET /api/v1/portal/queue        |
| GET /content/{id}  |             | [AiAssistance-     |       | --------------------------------- |
| ------------------ |             |  Service]          |       | [FeedService.getContentPreview()] |
| [FeedService]      |             | * Requires         |       | * Allowed ONLY if caller is       |
| * Requires         |             |   publishedVersion |       |   `item.authorId` OR holds        |
|   publishedVersion |             |   != null (else    |       |   REVIEWER/PUBLISHER/MODERATOR/   |
|   != null          |             |   409 Conflict!)   |       |   ADMIN for the item's audience   |
| * Checks caller in |             | * Summarizes ONLY  |       | * Returns `current_version = 3`   |
|   `published_      |             |   `published_      |       |   so Priya can inspect & approve  |
|   audience_id`     |             |   version = 2`     |       |   Revision #3!                    |
| * Returns ONLY     |             |   text!            |       +-----------------------------------+
|   Revision #2!     |             +--------------------+
+--------------------+
```

---

## 6. Campfire Weekly Challenge -> Human Editorial Review -> `+20` Publication & `-20` Withdrawal Flow (`CF-002`, `CF-004`, `CF-005`)

Traces a story from weekly challenge submission through human review, publication (`+20` points), and optional withdrawal (`-20` reversal):

```text
=====================================================================================================
PHASE A: ASHA SUBMITS WEEKLY CHALLENGE ENTRY (`POST /api/v1/challenges/chal_2026_w47/entries`)
=====================================================================================================
 Asha (usr_asha, roles: [EMPLOYEE, CREATOR])
   |
   |  POST /api/v1/challenges/chal_2026_w47/entries
   |  Headers: X-Pulse-User-Id: usr_asha, Idempotency-Key: "idem-chal-asha-1"
   |  Body:    { title: "Automated checklist...", body: "...", tags: ["team learning"] }
   v
 [ChallengeController.submitEntry()] --> [ChallengeService.submitEntry(ashaId, challengeId, key, req)]
   |
   +--- TX (@Transactional) ------------------------------------------------------------------------+
   |  1. Validate Idempotency-Key present (--x 422 if blank)                                        |
   |  2. Load `ChallengeEntity("chal_2026_w47")` (--x 404 if missing)                               |
   |  3. Check `challenge.status == "ACTIVE"` and `now <= closesAt` (--x 409 if closed)             |
   |  4. `identityService.requireAudienceAccess(ashaId, challenge.audienceId)` (--x 403)            |
   |  5. `identityService.requireRole(ashaId, "CREATOR")` (--x 403)                                 |
   |  6. Check existing `challenge_entries`:                                                        |
   |     * Same `(user_id, idempotency_key)`? ==> Return existing entry (200 OK idempotent replay!) |
   |     * Existing entry for `(challenge_id, user_id)` in `IN_REVIEW | APPROVED | PUBLISHED`?      |
   |       --x 409 Conflict ("You already have an active or published entry for this challenge")    |
   |     * Existing entry in `CHANGES_REQUESTED / DRAFT`?                                           |
   |       ==> Calls `PublishingWorkflowService.editRevision(...)` (creates Revision N+1)           |
   |           + `PublishingWorkflowService.submitForReview(...)`                                   |
   |     * Brand-new submission?                                                                    |
   |       ==> Calls `PublishingWorkflowService.createDraft(..., challengeId)`                      |
   |           + `PublishingWorkflowService.submitForReview(ashaId, contentId)`                     |
   |  7. Save `ChallengeEntryEntity(challengeId, ashaId, contentId, key, submittedAt)`              |
   |  8. Write `audit_events("CHALLENGE_ENTRY", entryId, "CHALLENGE_ENTRY_SUBMITTED")`              |
   +------------------------------------------------------------------------------------------------+
   |
   v
 200 OK  ChallengeEntryDto { entryId, challengeId, contentId, contentStatus: "IN_REVIEW", pointsAwarded: 0 }
         * NOTE: 0 points awarded on submission!


=====================================================================================================
PHASE B: PRIYA REVIEWS & APPROVES REVISION (`POST /api/v1/content/{id}/review`)
=====================================================================================================
 Priya (usr_priya, roles: [EMPLOYEE, CREATOR, REVIEWER])
   |
   |  POST /api/v1/content/{id}/review   { revisionNumber: 1, decision: "APPROVE", comments: "LGTM" }
   v
 [ContentController.review()] --> [PublishingWorkflowService.review(priyaId, contentId, req)]
   |
   +--- TX (@Transactional) ------------------------------------------------------------------------+
   |  1. `identityService.requireRole(priyaId, "REVIEWER")` (--x 403)                               |
   |  2. [LOCK: `itemRepository.findByIdForUpdate(contentId)` (`SELECT ... FOR UPDATE`)]            |
   |  3. `identityService.requireAudienceAccess(priyaId, item.audienceId)` (--x 403)                |
   |  4. SELF-APPROVAL BLOCK:                                                                       |
   |     `if (item.authorId.equals(priyaId))` --x 403 Forbidden ("Authors cannot self-approve")     |
   |  5. REVISION & STATE CHECK:                                                                    |
   |     `if (item.status != "IN_REVIEW")` --x 409 Conflict                                         |
   |     `if (req.revisionNumber != item.currentVersion)` --x 409 Conflict (Stale revision)         |
   |  6. Update `ContentRevisionEntity`: `approvedBy = priyaId`, `approvedAt = now()`               |
   |  7. Update `ContentItemEntity`: `status = "APPROVED"`                                          |
   |  8. Write `audit_events("CONTENT_ITEM", contentId, "CONTENT_APPROVED")`                        |
   +------------------------------------------------------------------------------------------------+
   |
   v
 200 OK  { id: contentId, status: "APPROVED", currentVersion: 1 }
         * NOTE: Still 0 points awarded on reviewer approval!


=====================================================================================================
PHASE C: MARCUS PUBLISHES STORY -> SYNCHRONOUS +20 POINTS (`POST /api/v1/content/{id}/publish`)
=====================================================================================================
 Marcus (usr_marcus, roles: [EMPLOYEE, CREATOR, PUBLISHER])
   |
   |  POST /api/v1/content/{id}/publish   { scheduledAt: null }
   v
 [ContentController.publish()] --> [PublishingWorkflowService.publish(marcusId, contentId, req)]
   |
   +--- TX (@Transactional — Single Atomic Commit for Publish + Recognition Ledger!) ---------------+
   |  1. `identityService.requireRole(marcusId, "PUBLISHER")` (--x 403)                             |
   |  2. [LOCK: `itemRepository.findByIdForUpdate(contentId)` (`SELECT ... FOR UPDATE`)]            |
   |  3. `identityService.requireAudienceAccess(marcusId, item.audienceId)` (--x 403)               |
   |  4. If already `PUBLISHED` and `publishedVersion == currentVersion`:                           |
   |       ==> Idempotent return (calls `recognitionService.onContentPublished(item)` safely)       |
   |  5. Verify `item.status == "APPROVED"` and `rev.approvedBy != null` (--x 409 if unapproved)    |
   |  6. Promote snapshot to live:                                                                  |
   |       `item.status = "PUBLISHED"`                                                              |
   |       `item.publishedVersion = item.currentVersion`                                            |
   |       `item.publishedAudienceId = rev.audienceId`                                              |
   |       `item.publishedAt = clock.now()`                                                         |
   |  7. Call `[RecognitionService.onContentPublished(item)]` in SAME transaction:                  |
   |       |-- Compute `weekStart = clock.currentWeekStart(publishedAt)` (Monday 00:00:00Z)         |
   |       |-- Already awarded `CONTRIBUTION_PUBLISHED` for `(authorId, contentId, weekStart)`?     |
   |       |     If yes -> return 0 (idempotent guard)                                              |
   |       |-- Already has net `>= 20` published-contribution points in `weekStart`?                |
   |       |     If yes -> return 0 (weekly cap: max 1 published contribution = +20 pts/week)       |
   |       |-- Clamp against 60-point total weekly cap                                              |
   |       |-- Insert `RecognitionEventEntity(CONTRIBUTION_PUBLISHED, beneficiary=asha, +20 pts)`   |
   |       `-- Call `syncWeeklyBadges(ashaId, weekStart, publishedAudienceId)`                      |
   |             --> Unlocks `FIRST_SPARK` badge!                                                   |
   |  8. Update `challenge_entries.awarded_at = publishedAt`                                        |
   |  9. Insert `OutboxEventEntity("CONTENT_PUBLISHED")` + `AuditEventEntity("CONTENT_PUBLISHED")`  |
   +------------------------------------------------------------------------------------------------+
   |
   v
 200 OK  { id: contentId, status: "PUBLISHED", publishedVersion: 1 }
         * Asha now has +20 points and the `FIRST_SPARK` badge in PostgreSQL!


=====================================================================================================
PHASE D: WITHDRAWAL REVERSAL (`POST /api/v1/content/{id}/withdraw`)
=====================================================================================================
 Marcus or Elena calls `POST /api/v1/content/{id}/withdraw`
   |
   +--- TX (@Transactional) ------------------------------------------------------------------------+
   |  1. `item.status = "WITHDRAWN"`, `item.publishedVersion = null`, `publishedAudienceId = null`  |
   |  2. `[RecognitionService.onContentWithdrawn(item)]`:                                           |
   |       |-- Checks if `CONTRIBUTION_PUBLISHED` (+20) was awarded to `item.authorId` in the       |
   |       |   current UTC week and not yet reversed                                                |
   |       |-- Inserts `RecognitionEventEntity(REVERSAL_WITHDRAWN, beneficiary=asha, -20 pts)`      |
   |       `-- Re-evaluates `syncWeeklyBadges(ashaId, weekStart, ...)`                              |
   |             --> Removes `FIRST_SPARK` if net published contributions drop to 0!                |
   +------------------------------------------------------------------------------------------------+
```

---

## 7. Colleague Appreciation (`+5`/`+5`), Team Snapshots, Badges & Opt-In Leaderboard Flow (`CF-006`, `CF-007`)

```text
 Daniel (`usr_daniel`, team: "UK Delivery") clicks "Appreciate Story (+5 pts)" on Asha's Story
   |
   |  POST /api/v1/content/{contentId}/appreciations
   |  Headers: X-Pulse-User-Id: usr_daniel, Idempotency-Key: "idem-app-daniel-1"
   v
 [RecognitionController.appreciate()] --> [RecognitionService.appreciateContent(danielId, contentId, key)]
   |
   +--- TX (@Transactional) ------------------------------------------------------------------------+
   |                                                                                                |
   |  1. VALIDATE STORY & CALLER GUARDRAILS                                                         |
   |     * [LOCK: `contentItemRepository.findByIdForUpdate(contentId)`]                             |
   |     * `item.status == "PUBLISHED"` and `item.publishedVersion != null`?                        |
   |         --x false (Draft / In-Review / Approved / Withdrawn) ---------> 409 Conflict           |
   |     * `identityService.requireAudienceAccess(danielId, item.publishedAudienceId)`              |
   |         --x caller not in published audience -------------------------> 403 Forbidden          |
   |     * `item.authorId.equals(danielId)`?                                                        |
   |         --x self-appreciation forbidden ------------------------------> 409 Conflict           |
   |     * `appreciationRepository.findByContentIdAndGiverId(contentId, danielId)` exists?          |
   |         --x duplicate appreciation by same colleague -----------------> 409 Conflict           |
   |                                                                                                |
   |  2. SNAPSHOT TEAMS AT EVENT TIME (Protects Cross-Team Badge History!)                          |
   |     * `giverTeam  = daniel.getTeam()`  ("UK Delivery")                                         |
   |     * `authorTeam = asha.getTeam()`    ("India Delivery")                                      |
   |     * Insert `AppreciationEntity(contentId, giverId=daniel, authorId=asha,                     |
   |                                  giverTeam="UK Delivery", authorTeam="India Delivery")`        |
   |                                                                                                |
   |  3. COMPUTE GIVER POINTS (`APPRECIATION_GIVEN` for Daniel)                                     |
   |     * Weekly Giving Cap: +5 pts per story, up to 3 stories/week (max 15 pts/week)              |
   |     * `givenThisWeek = sumPointsByBeneficiaryAndWeekAndType(daniel, weekStart, GIVEN)`         |
   |     * `totalDaniel   = sumPointsByBeneficiaryAndWeek(daniel, weekStart)`                       |
   |     * If `givenThisWeek < 15` and `totalDaniel < 60`:                                          |
   |         Insert `RecognitionEventEntity(APPRECIATION_GIVEN, beneficiary=daniel, +5 pts)`        |
   |                                                                                                |
   |  4. COMPUTE AUTHOR POINTS (`APPRECIATION_RECEIVED` for Asha)                                   |
   |     * Weekly Receiving Cap: +5 pts per distinct colleague, up to 5 colleagues (max 25 pts/wk)  |
   |     * `distinctSenders = countDistinctActorsForAuthorInWeek(asha, weekStart)`                  |
   |     * `receivedThisWeek = sumPointsByBeneficiaryAndWeekAndType(asha, weekStart, RECEIVED)`     |
   |     * `totalAsha        = sumPointsByBeneficiaryAndWeek(asha, weekStart)`                      |
   |     * If `!alreadyCountedSender` and `distinctSenders < 5` and `receivedThisWeek < 25`:        |
   |         Insert `RecognitionEventEntity(APPRECIATION_RECEIVED, beneficiary=asha,                |
   |                                        actor=daniel, +5 pts)`                                  |
   |                                                                                                |
   |  5. SYNCHRONIZE WEEKLY BADGES (`syncWeeklyBadges`)                                             |
   |     +---------------------------------------------------------------------------------------+  |
   |     | Badge Code            | Unlock Condition Evaluated in `syncWeeklyBadges`              |  |
   |     +-----------------------+---------------------------------------------------------------+  |
   |     | `FIRST_SPARK`         | Net `CONTRIBUTION_PUBLISHED + REVERSAL_WITHDRAWN >= 20`       |  |
   |     | `CONNECTION_MAKER`    | Any appreciation given OR received this week where            |  |
   |     |                       | `giver_team != author_team` (e.g. UK Delivery <-> India Del.) |  |
   |     | `COMMUNITY_FAVOURITE` | `>= 5` distinct `giver_id` rows on user's stories this week   |  |
   |     +-----------------------+---------------------------------------------------------------+  |
   |                                                                                                |
   +------------------------------------------------------------------------------------------------+
   |
   v
 200 OK  AppreciationResultDto {
           appreciationId: "app_...",
           giverPointsAwarded: 5,
           authorPointsAwarded: 5,
           giverWeeklyTotal: 5,
           authorWeeklyTotal: 25
         }

 OPT-IN LEADERBOARD vs PRIVATE PROGRESS (`GET /recognition/leaderboard` vs `GET /recognition/me`):
 -------------------------------------------------------------------------------------------------
 * `PUT /api/v1/recognition/preferences { optInLeaderboard: false }`:
     - Upserts `recognition_preferences (user_id = 'usr_asha', opt_in_leaderboard = FALSE)`
     - `GET /api/v1/recognition/leaderboard` filters out `usr_asha` immediately (privacy respected!)
     - `GET /api/v1/recognition/me` (called by `usr_asha`) STILL returns `weeklyPoints: 25` and badges!
```

---

## 8. Primary Connection Journey: Filter-Rank-Explain -> Idempotent Invite -> Locked Accept -> Bilateral Check-In

```text
+----------------------------------------------------------------------------------------------------+
| STEP 1: DISCOVER (`GET /api/v1/feed` -> `FeedService.getFeed(userId, interest)`)                   |
|   1. Load caller's `audienceIds` (`usr_asha` -> `["aud_all", "aud_pilot"]`)                        |
|   2. Filter `content_items` where `status = 'PUBLISHED'` AND `published_audience_id IN (...)`      |
|   3. Rank items deterministically:                                                                 |
|        `score = (matchingInterests * 20) + (openInvitationCapacity ? 15 : 0)`                      |
|   4. Build factual explanation (`whyThis`):                                                        |
|        `"WHY THIS? Your interest: team learning · 15 min conversation / 2 places available"`       |
+-------------------------------------------------+--------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
| STEP 2: SEND INVITATION REQUEST (`POST /api/v1/requests` -> `ConnectionService.createRequest`)     |
|   1. Require `Idempotency-Key` header (--x 422 if blank)                                           |
|   2. Check `findByRequesterIdAndIdempotencyKey` -> if present, return existing `200 OK`            |
|   3. Verify caller has audience access to story's `publishedAudienceId` (--x 403)                  |
|   4. Verify invitation not paused, not expired, and `acceptedCount < capacity` (--x 409)           |
|   5. Insert `connection_requests (state = 'PENDING')` + `outbox_events` + `audit_events`           |
+-------------------------------------------------+--------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
| STEP 3: HOST LOCKED ACCEPT FLOW (`PATCH /api/v1/requests/{id}` -> `ConnectionService.respond`)     |
|   +--- TX (@Transactional) ---------------------------------------------------------------------+  |
|   | 1. Check `req.version == request.expectedVersion` and `req.state == 'PENDING'` (--x 409)    |  |
|   | 2. [LOCK: `invitationRepository.findByIdForUpdate(invId)` (`SELECT ... FOR UPDATE`)]        |  |
|   | 3. Verify `inv.hostId == callerId` (--x 403)                                                |  |
|   | 4. Verify `!inv.paused && inv.expiresAt > now && inv.acceptedCount < inv.capacity` (--x 409)|  |
|   | 5. `inv.acceptedCount += 1`; `req.state = 'ACCEPTED'`; `req.version += 1`                   |  |
|   | 6. Save `OutboxEventEntity("REQUEST_ACCEPTED")` + `AuditEventEntity("REQUEST_ACCEPTED")`    |  |
|   +---------------------------------------------------------------------------------------------+  |
+-------------------------------------------------+--------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
| STEP 4: PRIVATE BILATERAL FEEDBACK (`POST /api/v1/requests/{id}/confirmations`)                    |
|   1. Caller must be `req.requesterId` (Asha) OR `inv.hostId` (Daniel) (--x 403)                    |
|   2. `req.state` must be `'ACCEPTED'` (--x 409)                                                    |
|   3. Upsert `participant_confirmations (request_id, user_id, happened, useful)`                    |
|   4. Compute `usefulConnection`:                                                                   |
|        `true` ONLY IF BOTH Asha AND Daniel recorded `happened = 'YES'` AND `useful = true`!        |
+----------------------------------------------------------------------------------------------------+
```

---

## 9. Audio Streaming (Mastek Radio & Culture Cast) & Cross-Device Playback Resume Flow

```text
 Mobile App (`mobile/App.tsx`) or Employee Web (`EmployeeWorkspace.tsx` / `App.tsx` Audio Bar)
   |
   |-- 1. `GET /api/v1/listen/radio` & `GET /api/v1/listen/culture-cast`
   |        `--> [ListenService]
   |               * Filters episodes by caller's `audienceIds`
   |               * Reads `published_version` revision metadata (chapters, transcript, duration)
   |               * Joins caller's `playback_progress` row (`position_seconds`, `completed`)
   |               * Generates short-lived signed URL:
   |                 `/api/v1/media/stream/cc_ep_03.mp3?token=signed_usr_asha&expires=...`
   |
   |-- 2. User pauses Culture Cast Episode 04 at `02:10` (`130` seconds) on Web:
   |        `PUT /api/v1/listen/progress`
   |        Body: `{ "contentId": "cc_ep_03", "positionSeconds": 130, "completed": false }`
   |        `--> [ListenService.saveProgress()]
   |               * Upserts `playback_progress (user_id='usr_asha', content_id='cc_ep_03', position_seconds=130)`
   |
   `-- 3. User opens Mobile App (`mobile/App.tsx`):
            `GET /api/v1/listen/culture-cast`
            `--> Returns `resumePositionSeconds: 130` so playback resumes at `02:10`!
```

---

## 10. Saathi AI Assistance, Published-Only Summaries, 30-Case Trust Gate & 720-Action Budget Cap

```text
 User clicks "Draft with AI" (`POST /api/v1/ai/drafts`) OR "Ask Saathi Summary" (`POST /api/v1/ai/summaries`)
   |
   v
 [AiController] --> [AiAssistanceService.generateDraft() / summarizeContent()]
   |
   |-- GUARD 1: Prohibited Prompt & Misuse Refusal Filter (`PROHIBITED_PATTERNS`)
   |     Matches `rank employees`, `appraisal score`, `salary`, `disciplinary`, `private message`, etc.?
   |     --x 422 Unprocessable Entity (`AI_POLICY_REFUSAL`: "Mastek Campfire refuses requests to rank...")
   |
   |-- GUARD 2: Published-Only Visibility & Audience Access Check
   |     * Loads `ContentItemEntity(targetContentId)`
   |     * For `summarizeContent`:
   |         Requires ` PUBLISHED `.equals(item.status) && `item.publishedVersion != null`
   |         --x 409 Conflict (`UNPUBLISHED_SUMMARY_FORBIDDEN`: "Saathi summaries are available only for published content")
   |     * Calls `identityService.requireAudienceAccess(userId, item.publishedAudienceId)`
   |         --x 403 Forbidden (Zero unauthorized disclosure across all 30 evaluation test cases!)
   |     * Loads strictly the `publishedVersion` revision (`ContentRevisionEntity`) — never draft text!
   |
   |-- GUARD 3: Deterministic Non-AI Template Fallback Always Built First
   |     * Builds grounded `templateFallback` citing `rev.title` and official source
   |
   `-- GUARD 4: 720-Action Pilot Budget Gate (`ai_usage_ledger`)
         * `used = ledgerRepository.count()`
         * If `used >= 720` (`CAP_REACHED_100`):
             Returns `aiAssisted: false`, `suggestion: templateFallback`, `budgetAlertLevel: "CAP_REACHED_100"`
         * If `used < 720`:
             Inserts `AiUsageLedgerEntity(userId, actionType, targetContentId, 1, "COMPLETED")`
             Computes alert level (`NONE` | `ALERT_50_PERCENT` at 360 | `ALERT_80_PERCENT` at 576 | `CAP_REACHED_100` at 720)
             Returns `aiAssisted: true`, `suggestion`, `templateFallback`, and `budget` status
```

---

## 11. Complete PostgreSQL 16 Entity-Relationship (ER) Diagram (`V1` + `V2` + `V3` — 19 Tables)

```text
+================================================================================================================+
|                              IDENTITY, ROLES & AUDIENCE ACCESS (`common/`)                                     |
+================================================================================================================+
  +---------------------------+       +-------------------------------+       +-------------------------------+
  | users (V1 + V3)           |       | audience_members (V1)         |       | audiences (V1)                |
  | ------------------------- |       | ----------------------------- |       | ----------------------------- |
  | PK id (VARCHAR)           |<------| PK,FK user_id                 |------>| PK id ('aud_all','aud_pilot') |
  |    display_name           |       | PK,FK audience_id             |       |    name, description          |
  |    role_title, location   |       +-------------------------------+       +-------------------------------+
  |    roles (CSV)            |
  |    team (V3: VARCHAR)     |       +-------------------------------+
  |    notification_frequency |       | user_interests (V1)           |
  +---------------------------+       | ----------------------------- |
                ^                     | PK,FK user_id, PK interest_tag|
                |                     +-------------------------------+
                |
+===============|================================================================================================+
|               |              CONTENT, REVISIONS & WEEKLY CHALLENGES (`content/`, `challenge/`)                 |
+===============|================================================================================================+
                |
  +-------------+-------------+       +-------------------------------+       +-------------------------------+
  | content_items (V1 + V3)   |       | content_revisions (V1 + V3)   |       | challenges (V3)               |
  | ------------------------- |       | ----------------------------- |       | ----------------------------- |
  | PK id                     |<------| PK id                         |       | PK id ('chal_2026_w47')       |
  |    kind (STORY/UPDATE/..) |       | FK content_id                 |       |    title, prompt              |
  | FK author_id (users.id)   |       |    version_number             |       | FK audience_id (audiences.id) |
  | FK audience_id (draft)    |       |    title, summary, body       |       |    week_start, starts_at      |
  | FK published_audience_id  |       |    audience_id (V3 snapshot)  |       |    closes_at, points_reward   |
  |    status (DRAFT..PUBLISH)|       |    tags (CSV), media_url      |       |    status ('ACTIVE'|'CLOSED') |
  |    current_version        |       |    transcript, chapters_json  |       +---------------+---------------+
  |    published_version      |       | FK created_by, FK approved_by |                       |
  | FK challenge_id (V3)      |-------+    UNIQUE(content_id, ver_no) |                       |
  +-------------+-------------+       +-------------------------------+                       |
                |                                                                             |
                |                     +-------------------------------+                       |
                |                     | challenge_entries (V3)        |                       |
                |                     | ----------------------------- |                       |
                +-------------------->| PK id                         |<----------------------+
                                      | FK challenge_id, FK user_id   |
                                      | FK content_id                 |
                                      |    idempotency_key            |
                                      |    UNIQUE(challenge_id,user_id|
                                      |    UNIQUE(user_id, idemp_key) |
                                      +-------------------------------+

+================================================================================================================+
|                              RECOGNITION LEDGER, APPRECIATIONS & BADGES (`recognition/`)                       |
+================================================================================================================+
  +-----------------------------------+   +-----------------------------------+   +---------------------------+
  | appreciations (V3)                |   | recognition_events (V3)           |   | weekly_badges (V3)        |
  | --------------------------------- |   | --------------------------------- |   | ------------------------- |
  | PK id                             |   | PK id                             |   | PK id                     |
  | FK content_id (content_items.id)  |   |    event_type (CONTRIBUTION_PUB,  |   | FK user_id (users.id)     |
  | FK giver_id   (users.id)          |   |      APPRECIATION_GIVEN/RECEIVED, |   |    badge_code             |
  | FK author_id  (users.id)          |   |      REVERSAL_WITHDRAWN)          |   |    week_start             |
  |    giver_team, author_team (Snap!)|   | FK beneficiary_id, FK actor_id    |   | FK audience_id            |
  |    week_start, idempotency_key    |   | FK content_id, FK challenge_id    |   |    UNIQUE(user_id, badge, |
  |    UNIQUE(content_id, giver_id)   |   |    points (+20, -20, +5)          |   |      week_start, aud_id)  |
  |    CHECK (giver_id <> author_id)  |   |    week_start, team_snapshot      |   +---------------------------+
  +-----------------------------------+   |    UNIQUE(type,beneficiary,       |
                                          |      content,actor,week_start)    |   +---------------------------+
                                          +-----------------------------------+   | recognition_preferences   |
                                                                                  | ------------------------- |
                                                                                  | PK,FK user_id (users.id)  |
                                                                                  |    opt_in_leaderboard     |
                                                                                  +---------------------------+

+================================================================================================================+
|                              CONNECTIONS, LISTEN, AI BUDGET & OUTBOX (`connection/`, `listen/`, `ai/`, `outbox`)|
+================================================================================================================+
  +-----------------------------------+   +-----------------------------------+   +---------------------------+
  | invitations (V1)                  |   | connection_requests (V1)          |   | participant_confirmations |
  | --------------------------------- |   | --------------------------------- |   | ------------------------- |
  | PK id, FK content_id, FK host_id  |<--| PK id, FK invitation_id           |<--| PK id, FK request_id      |
  |    format, capacity, accepted_cnt |   | FK requester_id, message, state   |   | FK user_id, happened,     |
  |    CHECK (accepted_cnt<=capacity) |   |    version, idempotency_key       |   |    useful                 |
  +-----------------------------------+   +-----------------------------------+   +---------------------------+

  +-----------------------------------+   +-----------------------------------+   +---------------------------+
  | playback_progress (V1)            |   | ai_usage_ledger (V1)              |   | outbox_events &           |
  | --------------------------------- |   | --------------------------------- |   | audit_events (V1)         |
  | PK(user_id, content_id)           |   | PK id, FK user_id, action_type    |   | ------------------------- |
  |    position_seconds, completed    |   |    target_content_id, units_used  |   | Transactional outbox +    |
  +-----------------------------------+   +-----------------------------------+   | immutable audit trail     |
                                                                                  +---------------------------+
```

---

## 12. End-to-End 4-Persona Sequence Diagram (Asha, Priya, Marcus, Daniel)

This sequence diagram shows the exact 3-minute full-stack Campfire demonstration flow verified by `CampfireChallengeAndRecognitionTest`:

```text
  Asha Verma                 Priya Nair                 Marcus Reed                Daniel Brooks             Spring Boot + PG16
 (Employee/Creator)          (Reviewer)                 (Publisher)                (UK Colleague)            (Backend & Ledger)
  India Delivery                                                                    UK Delivery
        |                         |                          |                           |                            |
        | 1. POST /challenges/chal_2026_w47/entries          |                           |                            |
        |------------------------------------------------------------------------------------------------------------>|
        |                         |                          |                           |  Creates ContentItem v1    |
        |                         |                          |                           |  Status = IN_REVIEW        |
        |                         |                          |                           |  Asha Points = 0           |
        |<------------------------------------------------------------------------------------------------------------|
        |                         |                          |                           |                            |
        |                         | 2. POST /content/{id}/review {rev:1, APPROVE}        |                            |
        |                         |---------------------------------------------------------------------------------->|
        |                         |                          |                           |  Checks priya != asha      |
        |                         |                          |                           |  Status = APPROVED         |
        |                         |                          |                           |  Asha Points = 0           |
        |                         |<----------------------------------------------------------------------------------|
        |                         |                          |                           |                            |
        |                         |                          | 3. POST /content/{id}/publish                          |
        |                         |                          |------------------------------------------------------->|
        |                         |                          |                           |  TX:                       |
        |                         |                          |                           |  * Status = PUBLISHED      |
        |                         |                          |                           |  * published_version = 1   |
        |                         |                          |                           |  * Ledger: +20 pts (Asha)  |
        |                         |                          |                           |  * Badge: FIRST_SPARK      |
        |                         |                          |<-------------------------------------------------------|
        |                         |                          |                           |                            |
        |                         |                          |                           | 4. POST /content/{id}/     |
        |                         |                          |                           |    appreciations           |
        |                         |                          |                           |--------------------------->|
        |                         |                          |                           |  TX:                       |
        |                         |                          |                           |  * Snapshots:              |
        |                         |                          |                           |    UK Del. -> India Del.   |
        |                         |                          |                           |  * Ledger: +5 pts (Daniel) |
        |                         |                          |                           |  * Ledger: +5 pts (Asha)   |
        |                         |                          |                           |  * Badge: CONNECTION_MAKER |
        |                         |                          |                           |    unlocked for BOTH!      |
        |                         |                          |                           |  * Totals:                 |
        |                         |                          |                           |    Asha = 25, Daniel = 5   |
        |                         |                          |                           |<---------------------------|
        |                         |                          |                           |                            |
        | 5. PUT /recognition/preferences { optInLeaderboard: false }                    |                            |
        |------------------------------------------------------------------------------------------------------------>|
        |                         |                          |                           |  * Leaderboard hides Asha  |
        |                         |                          |                           |    (Daniel #1 with 5 pts)  |
        |                         |                          |                           |  * GET /recognition/me for |
        |                         |                          |                           |    Asha STILL shows 25 pts!|
        |<------------------------------------------------------------------------------------------------------------|
```
