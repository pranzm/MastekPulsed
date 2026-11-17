# Mastek Campfire — AI-DLC Phase 4: MVP Execution Plan & Dual-Axis Scalability Blueprint (Users + Features)

> **🎓 Intern & AI Agent Guide — Why This Document Exists:**
> A **Hackathon POC** proves *that the product idea and core rules work* (using synthetic personas, seeded data, and local containers).
> A **Production MVP (Minimum Viable Product)** proves *that real employees get measurable value safely in production*—without over-building features that aren't needed yet.
>
> This blueprint shows how we take **Mastek Campfire** from today's verified POC to a **Production MVP (50–500 users)** and scale it along **two independent axes**:
> 1. **Axis 1 — User Scale:** `50 Pilot Users` -> `1,000 Regional Users` -> `10,000 All-Mastek Users` -> `50,000+ Enterprise Users`
> 2. **Axis 2 — Feature Scale:** Adding Voice AI (`Saathi`), Teams/Outlook Calendar booking, Semantic Search (`pgvector`), and new Recognition rules **without rewriting core Java or React code**.

---

## Table of Contents
1. [POC vs. MVP vs. Scale-Out Scope Matrix](#1-poc-vs-mvp-vs-scale-out-scope-matrix)
2. [Axis 1: Scaling by User Volume (50 -> 1,000 -> 10,000 -> 50,000+ Users)](#2-axis-1-scaling-by-user-volume-50---1000---10000---50000-users)
3. [Axis 2: Scaling by Features (Plug-and-Play Extension Points)](#3-axis-2-scaling-by-features-plug-and-play-extension-points)
4. [8-Week MVP & Rollout Execution Plan (Sprints 1–4)](#4-8-week-mvp--rollout-execution-plan-sprints-14)
5. [Zero-Downtime Database & Contract Evolution Rules](#5-zero-downtime-database--contract-evolution-rules)
6. [MVP Go-Live & Pilot Success Gate Checklist (`CF-012`)](#6-mvp-go-live--pilot-success-gate-checklist-cf-012)

---

## 1. POC vs. MVP vs. Scale-Out Scope Matrix

Before writing new code, every developer and AI agent must understand what is **already done in the POC**, what is **required for the Day-1 Production MVP**, and what is **deferred until scale triggers are met**:

| Capability Layer | Hackathon POC (Done & Verified Today) | Production MVP (Weeks 1–6: 50–500 Pilot Users) | 10,000-User Scale-Out (Weeks 7+: All Mastek) |
| :--- | :--- | :--- | :--- |
| **Authentication & Identity** | `X-Pulse-User-Id` header switcher (`usr_asha`..`usr_elena`). | **Microsoft Entra ID (Azure AD) OIDC + PKCE** (`Bearer` JWT); keep `X-Pulse-User-Id` restricted to `@Profile("dev")` only. | Entra ID Dynamic Group sync -> automated `audience_members` & `users.team` provisioning (SCIM / Graph API). |
| **Database & Persistence** | Local PostgreSQL 16 container + H2 test mode; Flyway `V1`–`V3`. | Managed **PostgreSQL 16** (Azure DB / AWS RDS), automated daily backups, HikariCP pool, PostgreSQL Row-Level Security (RLS). | **1 Primary + 1 Read Replica** (`@Transactional(readOnly=true)` routing) + monthly partitioning on `recognition_events` & `audit_events`. |
| **Audio & Media Delivery** | Simulated signed URLs (`/api/v1/media/stream/...`). | **Private Azure Blob / AWS S3** with real 15-minute cryptographic SAS / Pre-Signed URLs (`MediaStoragePort`). | **Cloud CDN** in front of Blob/S3 + Redis write-behind buffer for `PUT /api/v1/listen/progress` heartbeats. |
| **Caching & Rate Limiting** | Direct PostgreSQL queries on every request. | **Caffeine** in-memory cache (`@Cacheable` on Radio, Culture Cast, Current Challenge, Leaderboard) + `Bucket4j` rate limits. | **Shared Redis Cluster** (`spring.cache.type=redis`) across N stateless Spring Boot pods. |
| **Notifications & Calendar** | `outbox_events` polled in-JVM (`OutboxWorkerService`) and logged. | **Microsoft Teams Webhook / Corporate Email (Graph API)** + Expo Push adapter delivering privacy-safe generic prompts. | Optional **Outlook / Teams Calendar 15-min invite generator** on `REQUEST_ACCEPTED` (`CalendarIntegrationPort`). |
| **Saathi AI & Voice (`CF-008`, `CF-009`)** | Grounded text drafts & published-only summaries + 720-action cap + template fallback. | Live **Azure OpenAI / Foundry adapter** + **Azure AI Content Safety** + optional **Whisper STT / Piper TTS** voice microservice behind feature flag. | **Semantic Discovery (`pgvector`)** hybrid ranking (combining explicit interest tags + vector similarity). |
| **Observability & Analytics (`CF-012`)** | Audit log table (`audit_events`) + Portal Pilot Metrics card. | **OpenTelemetry + Prometheus/Grafana** + privacy-safe Cohort Telemetry (`useful_connections`, `publish_cycle_time`, `weekly_return_rate`). | Automated weekly executive digest & anomaly alerting. |

---

## 2. Axis 1: Scaling by User Volume (50 -> 1,000 -> 10,000 -> 50,000+ Users)

Because we built the backend as a **Java 21 Virtual-Thread Modular Monolith**, scaling from 50 users to 10,000+ users does **not** require rewriting business logic. Instead, we upgrade infrastructure and Spring profiles across **4 predictable tiers**:

```text
+================================================================================================================+
|                         USER SCALING LADDER (INFRASTRUCTURE & BOTTLENECK EVOLUTION)                            |
+================================================================================================================+

  TIER 1: MVP PILOT                  TIER 2: REGIONAL ROLLOUT           TIER 3: ALL-MASTEK SCALE
  (50 – 500 Users)                   (1,000 – 2,500 Users)              (10,000+ Users / 2,500 Concurrent)
  --------------------------         --------------------------         ----------------------------------
  * 1-2 Spring Boot Pods             * 2-3 Spring Boot Pods             * 4-6 Spring Boot Pods (Auto-scaled)
    (2 vCPU, 4 GB RAM,                 (Stateless behind ALB/AppGW)       (Java 21 Virtual Threads)
     Virtual Threads ON)             * Shared Redis Cache               * Shared Redis Cache + Write-Behind
  * 1 Managed PostgreSQL 16            (Radio, Culture Cast,              Buffer for `/listen/progress`
    (2 vCPU, 8 GB RAM)                  Leaderboard, Audience sets)     * PostgreSQL 16 Primary (Writes/Locks)
  * Caffeine In-Memory Cache         * Outbox Worker across pods          + 1 Read Replica (`/feed`, `/leaderboard`)
  * Azure Blob / S3 Direct             using `FOR UPDATE SKIP LOCKED`   * CDN Edge Caching for MP3 Audio
    Signed URLs (15m expiry)         * Blob/S3 + CDN                    * PgBouncer Connection Pooler
```

### Bottleneck Analysis & Exact Solutions at Each Tier

#### 1. The Audio Bandwidth Bottleneck (Solved at Tier 1)
- **Math:** If 2,000 commuters press play on a 15 MB *Culture Cast* episode at 8:30 AM, that is **30 GB of audio traffic** in a few minutes.
- **Solution:** Audio bytes **never** pass through Spring Boot (`ListenService`). Spring Boot only verifies `requireAudienceAccess(userId, episode.publishedAudienceId)` and generates a 15-minute cryptographic SAS/Signed URL pointing directly to Azure Blob Storage / S3 / CDN.

#### 2. The "Playback Progress Heartbeat" Write Bottleneck (Solved at Tier 3)
- **Math:** 2,500 concurrent listeners syncing `PUT /api/v1/listen/progress` every 15 seconds = **167 SQL `UPSERT`s/second** on `playback_progress`.
- **Solution:**
  - **Tier 1–2 (<= 2,500 users):** PostgreSQL 16 handles 167 simple PK upserts/sec in < 5% CPU.
  - **Tier 3 (10,000+ users):** Enable `RedisPlaybackProgressBuffer`. `PUT /api/v1/listen/progress` writes to a Redis hash (`HSET playback:{userId} {contentId} {pos}`) in `0.2 ms`, and a `@Scheduled(fixedDelay = 30000)` batch job flushes dirty positions to PostgreSQL using `jdbcTemplate.batchUpdate(...)`—reducing DB write load by **95%**.

#### 3. The Feed & Leaderboard Read Bottleneck (Solved at Tier 2 & Tier 3)
- **Math:** Every Monday morning when a new Weekly Challenge launches, thousands of employees open `GET /api/v1/feed`, `GET /api/v1/challenges/current`, and `GET /api/v1/recognition/leaderboard`.
- **Solution:**
  - Cache `GET /api/v1/challenges/current` and `GET /api/v1/recognition/leaderboard` by `(audienceId, weekStart)` with a 30-second TTL (or explicit cache eviction on `onContentPublished` / `appreciateContent`).
  - Route `@Transactional(readOnly = true)` methods (`FeedService.getFeed`, `RecognitionService.getLeaderboard`) to the PostgreSQL Read Replica while keeping all `@Transactional` write/lock methods (`ConnectionService.respondToRequest`, `PublishingWorkflowService.publish`, `RecognitionService.appreciateContent`) on the Primary node.

#### 4. Concurrency Hotspots on Invitations & Appreciations (Already Solved in Code!)
- **Solution:** Our pessimistic locks (`SELECT ... FOR UPDATE` in `InvitationRepository.findByIdForUpdate` and `ContentItemRepository.findByIdForUpdate`) lock **only the single target row** for `< 2 ms`, while Java 21 Virtual Threads park waiting requests with near-zero memory overhead.

---

## 3. Axis 2: Scaling by Features (Plug-and-Play Extension Points)

> **🎓 Intern Lesson — The Open/Closed Principle in AI-DLC:**
> A well-architected system is **Open for Extension, Closed for Modification**. When product managers ask for a new feature (e.g., *"Let's add a Mentor badge"*, *"Let's auto-book a 15-min Teams call"*, or *"Let's switch AI from OpenAI to Azure Foundry"*), you should be able to add a **new class** implementing a clean interface rather than rewriting existing working services!

Here is how our 8 backend packages and React workspaces are designed to accept new features cleanly:

```text
+================================================================================================================+
|                            PLUG-AND-PLAY FEATURE EXTENSION ARCHITECTURE                                        |
+================================================================================================================+

  CORE DOMAIN MODULE                 EXTENSION INTERFACE (PORT)           PLUGGABLE ADAPTERS (ADD WITHOUT REWRITE)
  ==================                 ==========================           ========================================

  1. [outbox/OutboxWorkerService] -> `OutboxEventHandler` -------------> * `InAppAuditNotificationHandler` (POC)
                                                                         * `TeamsWebhookNotificationHandler` (MVP)
                                                                         * `GraphEmailDigestHandler` (MVP)
                                                                         * `OutlookCalendarInviteHandler` (Scale)

  2. [recognition/RecognitionSvc] -> `BadgeRuleEvaluator` -------------> * `FirstSparkBadgeRule` (Current)
                                                                         * `ConnectionMakerBadgeRule` (Current)
                                                                         * `CommunityFavouriteBadgeRule` (Current)
                                                                         * `CultureListenerBadgeRule` (Future)
                                                                         * `CoffeeChatHostBadgeRule` (Future)

  3. [content/FeedService] --------> `FeedRankingStrategy` ------------> * `ExplicitInterestRuleRanker` (POC/MVP)
                                                                         * `PgVectorSemanticHybridRanker` (Scale)

  4. [ai/AiAssistanceService] -----> `AiCompletionPort` & -------------> * `DeterministicTemplateAdapter` (Fallback)
                                     `VoiceSpeechPort`                   * `AzureFoundryLlmAdapter` (MVP)
                                                                         * `SaathiWhisperPiperVoiceAdapter` (CF-008)

  5. [listen/ListenService] -------> `MediaUrlSignerPort` -------------> * `LocalDemoUrlSigner` (POC)
                                                                         * `AzureBlobSasUrlSigner` (MVP)
                                                                         * `AwsCloudFrontSignedUrlSigner` (Alt)
```

### Concrete Examples of Adding Future Features Safely

#### Feature Extension A: Adding New Campfire Challenge Formats (Audio / Micro-Video Moments)
- **What changes?** Zero changes to `ChallengeEntity` or `RecognitionService`!
- `content_items.kind` already supports `STORY`, `UPDATE`, `CULTURE_CAST`, `RADIO`. Adding `AUDIO_MOMENT` simply reuses `content_revisions.media_url` + `transcript` and flows through the exact same `PublishingWorkflowService` (`DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED -> +20 pts`).

#### Feature Extension B: Adding New Recognition Actions or Badges
- **What changes?**
  1. `recognition_events.event_type` is a `VARCHAR(40)` ledger column (not a rigid DB enum that requires `ALTER TYPE` table locks).
  2. To reward hosting a confirmed 15-minute connection (`CONNECTION_CONFIRMED`, e.g., `+10 pts`), `ConnectionService.submitConfirmation` simply emits an event when `usefulConnection == true`, and `RecognitionService` writes a ledger row capped by the same 60-point weekly ceiling.

#### Feature Extension C: Feature Flagging by Audience (`feature_flags`)
- Never deploy an experimental feature to all 10,000 employees at once!
- Because every user in Mastek Campfire already belongs to explicit `audiences` (`aud_pilot`, `aud_uk_delivery`, `aud_all`), we gate new features by `audience_id`:
  - Turn on `saathi_voice_input` for `aud_pilot` (50 users).
  - Verify the 30-case safety gate and token budget in the Publishing Portal Dashboard.
  - Graduate to `aud_all` with a single database config update—zero redeployments required.

---

## 4. 8-Week MVP & Rollout Execution Plan (Sprints 1–4)

This roadmap organizes the transition from today's POC to the 10,000-user rollout into **4 two-week AI-DLC sprints**:

```text
  SPRINT 1 (Weeks 1-2)          SPRINT 2 (Weeks 3-4)          SPRINT 3 (Weeks 5-6)          SPRINT 4 (Weeks 7-8)
  ====================          ====================          ====================          ====================
  MVP Identity, Storage         Saathi Live AI/Voice,         6-Week Pilot Launch           10,000-User Scale-Out
  & Cloud Foundation            Teams/Email & Telemetry       (50 - 500 Employees)          (Redis, Replica, Extras)
  --------------------          -----------------------       --------------------          ------------------------
  * MVP-101: Entra ID OIDC      * MVP-201: Reconcile          * MVP-301: Onboard            * SCALE-401: Redis Cache
    + PKCE (`common/`)            `voice-service/` (`CF-008`)   3 real audience groups        + `/listen/progress`
  * MVP-102: Azure Blob/S3      * MVP-202: Azure Content        & weekly challenge queue      write-behind buffer
    15m SAS URLs (`listen/`)      Safety gate (`CF-009`)      * MVP-302: Measure            * SCALE-402: PostgreSQL
  * MVP-103: Managed PG 16      * MVP-203: Teams/Email          6-week pilot outcomes         Read Replica routing
    + Flyway `V4` RLS             Outbox adapter (`outbox/`)    (`CF-012` dashboard)        * SCALE-403: Outlook/Teams
  * MVP-104: CI/CD pipeline     * MVP-204: OpenTelemetry      * MVP-303: Accessibility        Calendar invite plugin
    (H2 + Testcontainers PG)      & Pilot KPI endpoints         (WCAG 2.1 AA) & Mobile        & `pgvector` hybrid
                                                                TestFlight / Play Internal    discovery (Optional)
```

### Detailed Sprint Backlog (AI-DLC Work Units)

#### Sprint 1: Enterprise Identity, Cloud Media & Database Security (Weeks 1–2)
- [ ] **MVP-101: Production OIDC Authentication (`Microsoft Entra ID`) + Dev Persona Profile Split**
  - **Target:** `backend/src/main/java/com/mastek/pulse/common/SecurityConfig.java`, `IdentityService.java`, `web/src/api/apiClient.ts`
  - **Scope:** Validate `Authorization: Bearer <jwt>` in `prod` profile (`spring-boot-starter-oauth2-resource-server`); restrict `X-Pulse-User-Id` header switching strictly to `@Profile({"default", "dev", "test"})`.
- [ ] **MVP-102: Private Object Storage & 15-Minute Cryptographic Signed URLs**
  - **Target:** `backend/src/main/java/com/mastek/pulse/listen/MediaUrlSignerPort.java`, `PublishingWorkflowService.java`
  - **Scope:** Replace mock `/api/v1/media/stream/...` strings with Azure Blob SAS / S3 Pre-Signed URLs (`15m` TTL) so audio files stream directly from cloud storage/CDN.
- [ ] **MVP-103: Flyway `V4__production_indices_and_feature_flags.sql`**
  - **Target:** `backend/src/main/resources/db/migration/V4__production_indices_and_feature_flags.sql`
  - **Scope:** Add `feature_flags (flag_key, enabled, allowed_audiences)` table and composite covering indices for high-volume feed, recognition ledger, and outbox queries.

#### Sprint 2: Live Saathi AI / Voice Reconciliation & Real Notification Channels (Weeks 3–4)
- [ ] **MVP-201 (`CF-008`): Reconcile `voice-service/` (Whisper STT / Piper TTS / Azure Foundry Adapter)**
  - **Target:** `voice-service/`, `backend/src/main/java/com/mastek/pulse/ai/`
  - **Scope:** Connect the external voice branch behind `AiCompletionPort` and `VoiceSpeechPort` with strict circuit-breaker timeouts (`3s` -> fallback to typed text + deterministic template).
- [ ] **MVP-202 (`CF-009`): Automated Content Safety & Moderation Gate**
  - **Target:** `backend/src/main/java/com/mastek/pulse/ai/AiAssistanceService.java`, `PublishingWorkflowService.java`
  - **Scope:** Screen voice transcripts and story submissions through Azure AI Content Safety before queuing for human reviewer (`REVIEWER`) sign-off.
- [ ] **MVP-203: Real Notification Delivery Adapter (Microsoft Teams & Email)**
  - **Target:** `backend/src/main/java/com/mastek/pulse/outbox/OutboxWorkerService.java`
  - **Scope:** Dispatch privacy-safe notifications (`REQUEST_CREATED`, `REQUEST_ACCEPTED`, `CONTENT_PUBLISHED`, `FOLLOWUP_DUE`) respecting `users.notification_frequency` (`IMMEDIATE`, `DAILY_DIGEST`, `WEEKLY_DIGEST`).

#### Sprint 3: 6-Week Pilot Execution & Outcome Telemetry (`CF-012`) (Weeks 5–6)
- [ ] **MVP-301 (`CF-012`): Pilot Outcomes Telemetry & Cohort Comparison API**
  - **Target:** `backend/src/main/java/com/mastek/pulse/content/PortalMetricsService.java`, `web/src/portal/PublishingPortalWorkspace.tsx`
  - **Scope:** Track and display the 6 proposal KPIs without individual employee surveillance:
    1. Weekly challenge submission rate & publish conversion rate
    2. Median time from `IN_REVIEW` -> `PUBLISHED`
    3. Bilateral `USEFUL_CONFIRMED` 15-minute connections completed
    4. Cross-team appreciations (`giver_team != author_team`)
    5. Audio completion & cross-device resume rate
    6. AI 720-action budget burn rate & fallback ratio

#### Sprint 4: 10,000-User Scale-Out & Optional Feature Plugins (Weeks 7–8)
- [ ] **SCALE-401: Distributed Redis Cache & Playback Heartbeat Write-Behind Buffer**
  - **Target:** `backend/src/main/resources/application-prod.yml`, `ListenService.java`
- [ ] **SCALE-402: Primary / Read-Replica DataSource Routing**
  - **Target:** `backend/src/main/java/com/mastek/pulse/common/RoutingDataSourceConfig.java`
- [ ] **SCALE-403: Optional Calendar Invite (`ICS` / Graph API) & `pgvector` Hybrid Discovery**
  - **Target:** `backend/src/main/java/com/mastek/pulse/outbox/CalendarInviteOutboxHandler.java`, `FeedService.java`

---

## 5. Zero-Downtime Database & Contract Evolution Rules

As multiple developers and AI agents add features from Sprint 1 to Sprint 4, they must follow the **Expand-and-Contract Pattern** so deployments never cause downtime or break mobile clients:

```text
  RULE 1: DATABASE MIGRATIONS (`V1` -> `V2` -> `V3` -> `V4`...)
  -------------------------------------------------------------
  * NEVER edit an existing `V1`, `V2`, or `V3` SQL file (Flyway checksums are immutable).
  * EXPAND FIRST: Add new nullable columns (or columns with `DEFAULT`), new tables, and new indices in `V4`.
  * Dual-write / backfill in code while old pods are still running.
  * Only add `NOT NULL` constraints after all rows are backfilled.

  RULE 2: REST API & TYPESCRIPT CONTRACTS (`/api/v1/*`)
  -----------------------------------------------------
  * NEVER rename or delete an existing JSON response field in `openapi.yaml` or `contracts.ts`
    (Mobile users on older app versions will crash!).
  * ADDITIVE ONLY: Add new optional fields to DTOs, or introduce a new endpoint path when behavior changes
    fundamentally.
```

---

## 6. MVP Go-Live & Pilot Success Gate Checklist (`CF-012`)

Before promoting Mastek Campfire from the **500-user MVP Pilot** to **10,000-user General Availability**, the product must pass these 5 quantitative gates:

1. **Security & Privacy Gate:** `0` cross-audience leaks (`published_audience_id` enforced on all feeds, story details, appreciations, leaderboards, and Saathi summaries) and `100%` of leaderboard appearances opted-in (`opt_in_leaderboard = true`).
2. **AI Faithfulness & Cost Gate:** `0` unauthorized disclosures across the 30-case evaluation suite, `>= 90%` faithfulness on grounded summaries, and hard enforcement of the pilot token/action cap with deterministic template fallback.
3. **Editorial Integrity Gate:** `100%` of published stories reviewed by a distinct human reviewer (`approved_by != author_id`), with `+20` points awarded strictly on `PUBLISHED` (never on `DRAFT` or `APPROVED`).
4. **Connection Quality Gate:** At least `25%` of accepted 15-minute invitations result in a bilateral `USEFUL_CONFIRMED` check-in (`happened = YES` and `useful = true` from both participants).
5. **Performance & Reliability Gate:** `p95 < 250 ms` on `GET /api/v1/feed` and `GET /api/v1/recognition/leaderboard`, `0` over-capacity invitation accepts under concurrent load (`SELECT ... FOR UPDATE`), and `0` duplicate recognition ledger awards under retry (`Idempotency-Key` + unique ledger constraints).
