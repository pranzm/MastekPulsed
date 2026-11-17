# Mastek Campfire — AI-DLC Phase 3: Atomic Task Backlog & Parallel Developer Plan

> **🎓 Intern & AI Agent Guide:**
> In AI-DLC, we never tell an AI agent: *"Go build the whole app."* That leads to hallucinations, missing edge cases, and messy code.
> Instead, we break the system into **Atomic Vertical Slices (Work Units)**. Each task below has:
> - **Dependencies:** What must be finished first.
> - **Target Files:** Exactly where the code belongs.
> - **Acceptance Criteria (Definition of Done):** How the developer and AI agent verify the slice works before marking it `[x]`.

---

## How Multiple Developers Can Work in Parallel
Once **Epic 1 (Foundation & Contracts)** is completed, 3 to 4 developers using Claude Code, Codex, or GitHub Copilot can work simultaneously on different branches without merge conflicts:
- **Dev 1 (Backend Core & Connections):** Epics 2 & 3 (`backend/src/main/java/com/mastek/pulse/connection`, `content`, `listen`, `ai`)
- **Dev 2 (Employee Web Experience):** Epic 4 (`web/src/employee/`)
- **Dev 3 (Publishing Portal):** Epic 5 (`web/src/portal/`)
- **Dev 4 (Android & iOS Mobile App):** Epic 6 (`mobile/src/`)

---

## Epic 1: Foundation, Database Schema, Seed Data & OpenAPI Contract
*Goal: Establish the PostgreSQL schema, synthetic pilot dataset (1 Radio queue, 3 Culture Cast episodes, 6 updates, 12 contributions, 5 personas), and Spring Boot 3.3+ skeleton.*

- [x] **TASK-101: Initialize Spring Boot 3.3+ (Java 21) & Docker Compose (PostgreSQL 16)**
  - **Target:** `docker-compose.yml`, `backend/pom.xml`, `backend/src/main/resources/application.yml`
  - **Details:** Enable Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`), configure Flyway, Spring Data JPA, Validation, and OpenAPI/Swagger UI.
  - **Acceptance Criteria:** `docker compose up -d` starts PostgreSQL; `mvn spring-boot:run` starts cleanly.

- [x] **TASK-102: Create Flyway Schema Migration (`V1__init_schema.sql`)**
  - **Target:** `backend/src/main/resources/db/migration/V1__init_schema.sql`
  - **Details:** Create tables for `users`, `audiences`, `audience_members`, `user_interests`, `content_items`, `content_revisions`, `invitations`, `connection_requests`, `participant_confirmations`, `playback_progress`, `ai_usage_ledger`, `audit_events`, and `outbox_events` with all constraints from `02-domain-model-and-contracts.md`.
  - **Acceptance Criteria:** All tables, foreign keys, unique constraints (`UNIQUE(invitation_id, requester_id)`), and check constraints (`accepted_count <= capacity`) apply cleanly.

- [x] **TASK-103: Seed Synthetic Pilot Dataset (`V2__seed_pilot_data.sql`)**
  - **Target:** `backend/src/main/resources/db/migration/V2__seed_pilot_data.sql`
  - **Details:** Seed the exact scope from the proposal:
    - 5 synthetic personas (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`) + interests & audience groups (`aud_all`, `aud_pilot`, `aud_uk_delivery`).
    - **1 Mastek Radio queue** (3 curated items, 12 mins total).
    - **3 Culture Cast episodes** (with chapters, durations, transcripts, and related story links).
    - **6 Bite-sized company updates** (with official source, date, summary, and links).
    - **12 Synthetic employee contributions (Stories/Moments)** across India, UK, and US—featuring Daniel's flagship story (*"A retrospective everyone can contribute to"*, 15 min conversation, 2 places available) plus drafts and items in review for the Publishing Portal.
  - **Acceptance Criteria:** Database queries return all seeded records accurately.

---

## Epic 2: Core Backend Services — Primary Journey & Supporting Journeys (Java 21)
*Goal: Implement the strict business rules, row locks, RBAC, and transactional outbox in Spring Boot.*

- [x] **TASK-201: Auth Boundary, Persona Switcher, and Standard Error Contract**
  - **Target:** `backend/src/main/java/com/mastek/pulse/common/`
  - **Details:** Support `X-Pulse-User-Id` header / JWT session for instant persona switching (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`) in POC mode while keeping OIDC/PKCE resource server structure ready. Map exceptions to `401`, `403`, `409`, and `422`.
  - **Acceptance Criteria:** `GET /api/v1/me` returns full persona profile, interests, and roles.

- [x] **TASK-202: Discovery Feed & Rules-Based Recommendation Engine**
  - **Target:** `backend/src/main/java/com/mastek/pulse/content/FeedService.java`
  - **Details:** Implement **Filter -> Rank -> Explain**:
    1. Filter published stories and updates strictly by caller's `audience_id`.
    2. Rank by matching explicit user `interests` and open host capacity (`accepted_count < capacity` and not paused/expired).
    3. Generate factual explanation (`"WHY THIS? Your interest: team learning · 15 min conversation / 2 places available"`). Never invent a compatibility percentage!
  - **Acceptance Criteria:** Asha sees Daniel's story ranked at the top with the exact factual explanation; restricted audience stories never leak.

- [x] **TASK-203: Invitations, Idempotent Request Creation & 1-Transaction Locked Accept Flow**
  - **Target:** `backend/src/main/java/com/mastek/pulse/connection/`
  - **Details:**
    - `POST /api/v1/requests`: Enforce `Idempotency-Key` header so mobile retries return the existing request without duplicates.
    - `PATCH /api/v1/requests/{id}`: Implement the exact 4-step **Accept Flow / One Transaction** (`SELECT ... FOR UPDATE` on `invitations`, check `accepted_count < capacity`, increment `accepted_count`, update state to `ACCEPTED`, and write `OutboxEvent` atomically). Also support `DECLINED` and `CANCELLED`, plus host availability pause.
    - `POST /api/v1/requests/{id}/confirmations`: Save private participant feedback (`happened`, `useful`). Compute `usefulConnection = true` **only** when both requester and host confirm `happened = YES` and `useful = true`.
  - **Acceptance Criteria:** Concurrent accept requests when `capacity = 1` result in exactly one `200 OK` and one `409 Conflict (Full capacity)`.

- [x] **TASK-204: Listen Service — Mastek Radio, Culture Cast & Cross-Device Playback Resume**
  - **Target:** `backend/src/main/java/com/mastek/pulse/listen/`
  - **Details:** Serve the curated Mastek Radio commute queue and Culture Cast episodes with chapters, transcripts, related story links, and short-lived signed audio URLs (`/api/v1/media/stream/{assetId}?token=...&expires=...`). Implement `PUT /api/v1/listen/progress` to persist and resume playback position across Web and Mobile.
  - **Acceptance Criteria:** Pausing Culture Cast Episode 04 at `02:10` on Web immediately reflects `positionSeconds: 130` when fetched on Mobile.

- [x] **TASK-205: Revision-Based Publishing Workflow & Scoped Portal Roles**
  - **Target:** `backend/src/main/java/com/mastek/pulse/content/PublishingWorkflowService.java`
  - **Details:** Implement `DRAFT -> IN_REVIEW -> APPROVED -> SCHEDULED / PUBLISHED -> WITHDRAWN`.
    - Enforce `CREATOR`, `REVIEWER` (with strict `approved_by != author_id` check), `PUBLISHER`, `MODERATOR`, and `ADMIN` (admin cannot self-approve editorial content unless granted `REVIEWER`!).
    - Any edit to title, body, or audience on an `APPROVED` or `SCHEDULED` item creates `version_number + 1` and resets status to `IN_REVIEW`.
    - Background outbox worker checks scheduled items and publishes them when due.
  - **Acceptance Criteria:** Attempting self-approval returns `403 Forbidden`; editing an approved story creates a new revision and invalidates approval.

- [x] **TASK-206: Optional AI Drafting & Summarization with Budget Cap & Template Fallback**
  - **Target:** `backend/src/main/java/com/mastek/pulse/ai/`
  - **Details:** Implement `POST /api/v1/ai/drafts` (for invitation messages and story drafts) and `POST /api/v1/ai/summaries` (for company updates). Track usage against the 720-action budget cap (50%, 80%, 100% alerts). Always provide a deterministic non-AI template fallback and require explicit human action in the UI.
  - **Acceptance Criteria:** Works with or without an external LLM API key; disables optional AI automatically if budget reaches 100%.

---

## Epic 3: Backend Verification & AI Trust Gate Tests
*Goal: Prove enterprise reliability, row-level access controls, concurrency locking, and the 30-case AI evaluation suite.*

- [x] **TASK-301: Automated Integration Tests for State Machines, Concurrency & 30 Synthetic AI Cases**
  - **Target:** `backend/src/test/java/com/mastek/pulse/`
  - **Details:** Test denied access (`403`), stale version/full capacity (`409`), bilateral usefulness calculation, and the 30 synthetic evaluation cases (permission, relevance, refusal, faithfulness).

---

## Epic 4: Responsive Employee Web Experience (React 18 + TypeScript)
*Goal: Deliver the employee web app shown on Page 10 of the proposal.*

- [x] **TASK-401: Employee Web App Shell, Persona Switcher & Persistent Audio Bar**
  - **Target:** `web/src/`
  - **Details:** Build navigation (`Your Pulse`, `Listen`, `Connect`, `Contribute`, `Profile`), top bar with instant Demo Persona Switcher (`Asha`, `Daniel`, `Priya`, `Marcus`, `Elena`), and bottom persistent audio player bar (`Paused: Culture Cast / 04:12 of 12:00 · Play · Transcript · Queue`).
- [x] **TASK-402: Primary Journey Screens (Your Pulse Feed, Story Detail, Invitation Composer, Connections Inbox & Private Check-In)**
  - **Target:** `web/src/employee/`
  - **Details:** Match wireframes 01–06 (Pages 6–8 & 10): Factual `WHY THIS?` badge, interest filter, AI draft helper + template fallback, idempotent send, Host Request Inbox (`Accept` / `Decline` with remaining places), and Private Follow-Up (`Did the conversation happen?` / `Was it useful?`).
- [x] **TASK-403: Supporting Journey Screens (Mastek Radio, Culture Cast Player with Chapters/Transcripts, and Contribute Story Composer)**
  - **Target:** `web/src/employee/`
  - **Details:** Match wireframes 05, 07, 08 (Pages 8–9): Commute queue, episode chapters, interactive transcript viewer, local draft auto-save (`localStorage`), and revision preview before submission.

---

## Epic 5: Role-Based Publishing Portal (React 18 + TypeScript)
*Goal: Deliver the dedicated web workspace for content owners, reviewers, publishers, and scoped admins shown on Page 11 of the proposal.*

- [x] **TASK-501: Publishing Portal Workspace (Review Queue, Revision Inspector, Schedule/Publish/Withdraw & Audit Log)**
  - **Target:** `web/src/portal/`
  - **Details:** Match Page 11 wireframe (`Dashboard`, `Content`, `Review queue`, `Schedule`, `Media library`, `Audiences`, `Audit log`, plus `Pilot Metrics & AI Budget Gate` dashboard from Page 5 & 12). Enforce role checks visually and via API (`Approve revision`, `Request changes`, `Publish now`, `Schedule`, `Withdraw`).

---

## Epic 6: Android & iOS Mobile App (React Native + Expo + TypeScript)
*Goal: Deliver the native mobile experience shown on Pages 6–9 of the proposal.*

- [x] **TASK-601: React Native App with 5 Bottom Tabs (`Pulse`, `Listen`, `Connect`, `Publish`, `Profile`) & Native Audio**
  - **Target:** `mobile/`
  - **Details:** Share the exact TypeScript API contracts from `docs/aidlc/02-domain-model-and-contracts.md`. Implement all 8 mobile wireframes (Pages 6–9), local draft storage, native background audio playback (`expo-av`), and cross-device playback resume with the Spring Boot backend.

## Epic 7: Campfire engagement, recognition and Saathi

Detailed acceptance criteria and dependencies: [CF-001–CF-012](../campfire/IMPLEMENTATION-STORIES.md).

- [x] Browser-only engagement demonstration, synthetic role simulation, scoring and badges (`CF-001`).
- [x] Optional browser leaderboard, opt-out, reset and typed Saathi fallback (`CF-001`).
- [x] CF-002–CF-007: integrate challenges and recognition with Java APIs and PostgreSQL (`V3__campfire_challenges_and_recognition.sql`, `ChallengeService`, `RecognitionService`, `CampfireChallengeAndRecognitionTest` verified on H2 and PostgreSQL 16.16).
- [x] CF-003 / CF-010: explicit live/demo transports (`PulseHttpError`, no silent fallback in live mode, role-aware loading) and shared authorization policy (`403` portal access for ordinary employees, `published_audience_id` isolation, published-only employee & Saathi reads, `/preview` endpoint).
- [ ] CF-008 / CF-009: reconcile the missing voice branch (`voice-service/`) and enforce voice moderation policy (Blocked: external branch not present in workspace).
- [x] CF-011: full-stack demonstration verified across standalone browser demo and live React + Spring Boot + PostgreSQL 16 stack (see [DAILY-RESULTS-2026-11-17.md](../campfire/DAILY-RESULTS-2026-11-17.md)).
- [ ] CF-012: 6-week pilot telemetry and measured pilot outcomes (Post-POC pilot execution).

## Ordered Execution Plan Status — 17 November 2026

See [D0–D6](../campfire/DEVELOPMENT-PLAN-2026-11-17.md) and verified execution evidence in [DAILY-RESULTS-2026-11-17.md](../campfire/DAILY-RESULTS-2026-11-17.md):
- **D0–D5 Completed & Verified** (`11/11` Node tests, `7/7` Java integration tests on H2 and PostgreSQL 16.16, `npm --prefix web run build` clean).
- **D6 Blocked** on missing external `voice-service/` branch.
