# Development Results — 17 November 2026

Status: **D0, D1, D2, D3, D4, and D5 (Stretch) COMPLETED & VERIFIED on Java 21 + Spring Boot 3.3.5 (H2 & PostgreSQL 16.16) + React/Vite. D6 BLOCKED on missing external voice branch.**

## Baseline & Environment (`D0`)
- **Repository & Branch:** `/Users/pranjalmedhi/Desktop/Workspace/Mastek-Pulse`, branch `dev`, baseline commit `cb28c16` with `Campfire-POC.patch` applied cleanly.
- **Java:** OpenJDK 21.0.6 (`Corretto-21.0.6.7.1`, `aarch64`).
- **Maven:** Resolved cached Apache Maven `3.9.4` at `/Users/pranjalmedhi/.m2/wrapper/dists/apache-maven-3.9.4-bin/2vqnav6ufo1qvo5j2um40861m/apache-maven-3.9.4/bin/mvn`.
- **PostgreSQL 16 (Docker):** Started Docker Desktop and launched container `mastek-pulse-postgres` (`postgres:16-alpine` on `localhost:5432`, DB `mastek_pulse`, user `pulse_app`). Added `PostgresCompatibilityConfig.java` (`public.dateadd` compatibility function) and `baseline-on-migrate: true` (`baseline-version: 0`) so untouched `V1__init_schema.sql`, `V2__seed_pilot_data.sql`, and new `V3__campfire_challenges_and_recognition.sql` run identically on H2 and real PostgreSQL 16.16.
- **Node & Web:** Node `v24.13.0`, npm `11.8.0`.
- **Voice Branch (`D6`):** Searched both `/Users/pranjalmedhi/Desktop/Workspace/Mastek-Pulse` and `/Users/pranjalmedhi/Documents/ChatGPT/Mastek Pulse/work/campfire-poc`; `voice-service/` (Saathi/Saarthi/Whisper/Piper/Azure) is absent in inspected sources.

---

## Agent Execution Record (`D0`–`D6`)

| Step | Status | Changed / Added Files | Verification / Evidence |
|---|---|---|---|
| **D0** Environment & Baseline | **Completed** | `backend/src/main/java/com/mastek/pulse/common/PostgresCompatibilityConfig.java`, `backend/src/main/resources/application.yml` | Verified Java 21.0.6, Maven 3.9.4, Node v24.13.0, and Docker `postgres:16-alpine` (`PostgreSQL 16.16`). |
| **D1** Explicit Adapters & Role-Aware Loading (`CF-003`, `CF-011`) | **Completed** | `web/src/api/apiClient.ts`, `web/src/App.tsx`, `web/src/employee/EmployeeWorkspace.tsx`, `web/src/portal/PublishingPortalWorkspace.tsx` | Explicit `demo` vs `live` mode (`VITE_APP_MODE` + UI toggle); typed `PulseHttpError(status, code, message)`; zero silent local fallback on live API failure; `/me` loaded first and `/portal/*` skipped for non-portal personas; no background `/api/v1` requests when `surfaceMode === 'CAMPFIRE'`; persona-scoped state cleared on persona switch. |
| **D2** Shared Publication Visibility & Editorial Correctness (`CF-010`, `CF-004`) | **Completed** | `IdentityService.java`, `UserRepository.java`, `ContentItemEntity.java`, `ContentRevisionEntity.java`, `ContentItemRepository.java`, `FeedService.java`, `PublishingWorkflowService.java`, `ContentController.java`, `AiAssistanceService.java`, `OutboxWorkerService.java` | Ordinary employees receive `403` on `/api/v1/portal/queue` & `/api/v1/portal/audit-log`; portal endpoints and actions (`submit`, `review`, `publish`, `withdraw`) are audience-scoped and role-checked (`403`); self-approval returns `403`; review requires `IN_REVIEW` + matching `revisionNumber` (`409`); `published_audience_id` and `published_version` remain isolated from unapproved draft edits (`audience_id`, `current_version`); employee `GET /api/v1/content/{id}` and `POST /api/v1/ai/summaries` read strictly `PUBLISHED` snapshots while `GET /api/v1/content/{id}/preview` serves owner/editor previews. |
| **D3** Contracts & Persistence (`CF-002`, `CF-005`) | **Completed** | `docs/campfire/NEW-CONTRACTS.md`, `docs/aidlc/02-domain-model-and-contracts.md`, `docs/aidlc/openapi.yaml`, `web/src/api/contracts.ts`, `backend/src/main/resources/db/migration/V3__campfire_challenges_and_recognition.sql`, `PulseClock.java` | Frozen OpenAPI 3.1, Markdown, and TypeScript contracts. Added `V3__campfire_challenges_and_recognition.sql` (`challenges`, `challenge_entries`, `appreciations`, `recognition_events`, `recognition_preferences`, `weekly_badges`, plus `published_audience_id` & `challenge_id` on `content_items`, `content_revisions.audience_id`, and `users.team`). `V1` and `V2` untouched. |
| **D4** Persisted `+20` Challenge & Publication Vertical Slice (`CF-002`, `CF-004`, `CF-005`, `CF-007`) | **Completed** | `ChallengeEntity.java`, `ChallengeRepository.java`, `ChallengeEntryEntity.java`, `ChallengeEntryRepository.java`, `ChallengeService.java`, `ChallengeController.java`, `RecognitionEventEntity.java`, `RecognitionEventRepository.java`, `RecognitionService.java`, `RecognitionController.java` | `GET /api/v1/challenges/current`, idempotent `POST /api/v1/challenges/{id}/entries` (`Idempotency-Key` + one entry per user/challenge), `0` points on submit/approve, synchronous transaction-safe `+20` (`CONTRIBUTION_PUBLISHED`) on `publishNow()` and scheduled `OutboxWorkerService`, idempotent replay protection, `REVERSAL_WITHDRAWN` (`-20`) on withdraw, and private `GET /api/v1/recognition/me`. |
| **D5** Full Recognition, Appreciation, Badges, Leaderboard & Opt-Out (`CF-006`, `CF-007` — Stretch) | **Completed** | `AppreciationEntity.java`, `AppreciationRepository.java`, `RecognitionPreferenceEntity.java`, `RecognitionPreferenceRepository.java`, `WeeklyBadgeEntity.java`, `WeeklyBadgeRepository.java`, `RecognitionService.java`, `RecognitionController.java`, `EmployeeWorkspace.tsx`, `PublishingPortalWorkspace.tsx` | `POST /api/v1/content/{id}/appreciations` (blocks unpublished/self/duplicate, `+5` giver up to 3 stories / `15` pts, `+5` author up to 5 distinct colleagues / `25` pts, `60` pts weekly cap, event-time `giver_team`/`author_team` snapshot), 3 badges (`FIRST_SPARK`, `CONNECTION_MAKER`, `COMMUNITY_FAVOURITE`), `GET /api/v1/recognition/leaderboard` (top-5 opt-in), and `PUT /api/v1/recognition/preferences`. |
| **D6** Voice / Saathi Branch Reconciliation (`CF-008`, `CF-009`) | **Blocked** | — | Blocked: `voice-service/` (Whisper/Piper/Azure Foundry/Content Safety) branch is not present in workspace or archives. |

---

## Completed vs. Blocked Story IDs

- **Completed:**
  - `CF-001` — Brand and proposal alignment (`Mastek Campfire`, circuit-flame logo, `Saathi` display naming)
  - `CF-002` — Weekly challenge domain and persistence (`V3__campfire_challenges_and_recognition.sql`, `GET /api/v1/challenges/current`, `POST /api/v1/challenges/{id}/entries`)
  - `CF-003` — Explicit demo and live API modes (`VITE_APP_MODE=demo|live`, UI transport switch, `PulseHttpError`, no silent fallback in live mode, role-aware loading)
  - `CF-004` — Challenge submission through human editorial workflow (author/role/audience validation, revision binding, self-approval block, `published_audience_id` isolation, preview vs. published detail)
  - `CF-005` — Server-side recognition ledger (`+20` on publish only, `-20` reversal on withdraw, weekly caps, UTC Monday `00:00:00Z` boundary via injectable `PulseClock`)
  - `CF-006` — Colleague appreciation and badge persistence (`+5` giver / `+5` receiver, distinct appreciators, cross-team snapshot, `FIRST_SPARK`, `CONNECTION_MAKER`, `COMMUNITY_FAVOURITE`)
  - `CF-007` — Opt-in leaderboard and recognition privacy (`GET /api/v1/recognition/leaderboard`, `PUT /api/v1/recognition/preferences`, private `GET /api/v1/recognition/me` preserved on opt-out)
  - `CF-010` — Role-scoped portal endpoints and published-only visibility (`403` for non-portal users, audience-scoped queue/audit, published-only employee reads and Saathi summaries)
  - `CF-011` — End-to-end full-stack demo readiness (both standalone browser demo `/campfire/index.html` and live React + Spring Boot + PostgreSQL stack)
- **Blocked / Deferred:**
  - `CF-008` — Reconcile external voice/Foundry branch (Blocked: missing `voice-service/` branch)
  - `CF-009` — Content safety and moderation enforcement for voice/Foundry pipeline (Blocked: depends on `CF-008` voice/Foundry branch; text moderation & AI refusal gates remain enforced in `AiAssistanceService`)
  - `CF-012` — 6-week pilot telemetry & outcome measurement (Scheduled for post-POC pilot execution)

---

## Verification Commands & Results

1. **Browser Campfire Engine Unit Tests (11/11 Passing):**
   ```bash
   npm --prefix web run test:campfire
   ```
   - Result: `11 passed, 0 failed` (`engine.test.mjs`).

2. **Frontend TypeScript & Vite Production Build:**
   ```bash
   npm --prefix web run build
   ```
   - Result: `tsc && vite build` succeeded cleanly (`dist/assets/index-*.js` generated with 0 errors).

3. **Backend Integration, Security, Challenge & Recognition Tests — Default H2 Mode (7/7 Passing):**
   ```bash
   /Users/pranjalmedhi/.m2/wrapper/dists/apache-maven-3.9.4-bin/2vqnav6ufo1qvo5j2um40861m/apache-maven-3.9.4/bin/mvn -f backend/pom.xml test
   ```
   - Result: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
     - `PulseIntegrationAndAiGatesTest` (4 tests):
       - `testPrimaryJourneyAndConcurrencyLock`
       - `testEditorialGuardrailsAndPortalAuthorization` (verifies `403` for Asha on `/portal/queue` & `/portal/audit-log`, `403` self-approval, draft edit isolation from published snapshot, and `/preview` vs `/content/{id}`)
       - `testCrossDeviceAudioResume`
       - `testThirtySyntheticAiEvaluationCasesAndBudgetGate` (30/30 cases, 0 unauthorized disclosures, 100% faithfulness, published-only summary enforcement)
     - `CampfireChallengeAndRecognitionTest` (3 tests):
       - `testChallengeSubmissionEditorialPublishWithdrawAndScheduledPublish` (verifies idempotent entry, `0` pts on submit/approve, `+20` on publish, idempotent replay, `-20` on withdraw, and scheduled outbox publication award)
       - `testAppreciationsCapsBadgesCrossTeamSnapshotAndOptOutLeaderboard` (verifies `+5` giver up to 3 stories / `15` pts, `+5` receiver up to 5 distinct colleagues / `25` pts, `FIRST_SPARK` / `CONNECTION_MAKER` / `COMMUNITY_FAVOURITE` badges, team snapshot preservation after team change, and opt-out hiding from leaderboard while keeping private score)
       - `testUtcMondayWeekBoundaryReset` (verifies Sunday `23:59:59Z` vs Monday `00:00:00Z` weekly reset via `PulseClock`)

4. **Backend Integration Tests — Live PostgreSQL 16.16 Mode (7/7 Passing):**
   ```bash
   docker compose up -d postgres
   /Users/pranjalmedhi/.m2/wrapper/dists/apache-maven-3.9.4-bin/2vqnav6ufo1qvo5j2um40861m/apache-maven-3.9.4/bin/mvn -f backend/pom.xml -Dspring.profiles.active=postgres test
   ```
   - Result: Connected to `jdbc:postgresql://localhost:5432/mastek_pulse` (`PostgreSQL 16.16`), applied Flyway migrations `V1`, `V2`, `V3` (`schema version: 3`), and passed `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.

---

## Exact Startup Commands (`Live` Mode vs `Demo` Mode)

### Option A: Full-Stack Live Mode (PostgreSQL 16 + Spring Boot 3.3 + React Web/Portal)
```bash
# 1. Start PostgreSQL 16
docker compose up -d postgres

# 2. Start Spring Boot backend on port 8080 (connected to PostgreSQL 16)
/Users/pranjalmedhi/.m2/wrapper/dists/apache-maven-3.9.4-bin/2vqnav6ufo1qvo5j2um40861m/apache-maven-3.9.4/bin/mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=postgres

# 3. Start Vite web server in explicit LIVE API mode on port 5173
VITE_APP_MODE=live npm --prefix web run dev
```

### Option B: Standalone Browser Demo Mode
```bash
npm --prefix web run dev
# Open http://localhost:5173/campfire/index.html (or toggle DEMO ENGINE in the React top bar)
```

---

## 3-Minute Live Walkthrough & Expected Point / Badge Totals

1. **Submit (`Asha Verma`, `usr_asha`):**
   - Switch persona to **Asha Verma** (`Employee Web`).
   - In the **Weekly Campfire Challenge** banner (*"One small win that helped a teammate this week"*), enter title & story body and click **Submit to Weekly Challenge (+20 pts on Publish)**.
   - **Expected Score:** Asha remains at **`0 pts`** (`IN_REVIEW` awards 0 points; entry status is `IN_REVIEW`).
2. **Approve (`Priya Nair`, `usr_priya`):**
   - Switch persona to **Priya Nair** and open **Publishing Portal**.
   - Select Asha's submitted story in the **Editorial Review Queue** and click **Approve Rev #1**.
   - **Expected Score:** Asha remains at **`0 pts`** (`APPROVED` awards 0 points).
3. **Publish (`Marcus Reed`, `usr_marcus`):**
   - Switch persona to **Marcus Reed** in **Publishing Portal** and click **Publish Now**.
   - **Expected Score:** Asha immediately receives **`+20 pts`** (`CONTRIBUTION_PUBLISHED`) and unlocks the **`First Spark`** badge (`20 / 60 pts`).
4. **Cross-Team Appreciate (`Daniel Brooks`, `usr_daniel`):**
   - Switch persona to **Daniel Brooks** (`Employee Web`, `UK Delivery` team vs Asha's `India Delivery` team).
   - Click **Appreciate Story (+5 pts)** on Asha's newly published story.
   - **Expected Score:**
     - **Asha Verma:** **`25 pts`** (`20` published + `5` received appreciation) and unlocks **`Connection Maker`** (cross-team appreciation).
     - **Daniel Brooks:** **`5 pts`** (`5` given appreciation).
5. **Opt-Out & Restore (`Asha Verma`, `usr_asha`):**
   - Switch back to **Asha Verma** and click **Hide me from leaderboard** in **Your Weekly Recognition & Opt-In Champions**.
   - **Expected Result:** Asha disappears from the public **Community Champions** top-5 list (`optInLeaderboard: false`), while her private progress card still displays **`25 / 60 pts`** and her badges. Clicking **Show me on leaderboard** restores her #1 rank.
6. **Saathi Published-Only Summary:**
   - Click **Ask Saathi Summary** on any published story or update -> returns a grounded summary with source citation and deterministic template fallback.

## Make me Productive addition

Added web prototype in both demo entry points, a four-page proposal addendum (new 23-page PDF), and web/native development plan. Web build and 11 existing browser-engine tests passed in an isolated copy. Headless Chrome checks passed for habit creation/edit/check-in/reload, persona separation, timer completion/pause/breathing phases, 390px width, live privacy gate and standalone navigation. New PDF pages rendered and visually inspected. Backend/private persistence and native productivity screens are not implemented.
