# Mastek Pulse — AI-DLC Phase 2B: System Architecture, Scale (10,000+ Users), Code Flows & Microservices Evolution

> **🎓 Intern & AI Agent Guide:**
> How do we design a Hackathon POC that doesn't need to be thrown away when Mastek scales it to **10,000+ employees**—and can cleanly split into **microservices** in the future if needed?
> By building a strict **Modular Monolith** in **Java 21 + Spring Boot 3.3+**, backed by **PostgreSQL 16** and **React / React Native (TypeScript)**. Every domain module owns its database tables exclusively and communicates through clean service interfaces and a **Transactional Outbox**.
>
> **Diagram Convention:** All architectural, sequence, state-machine, and code-flow diagrams in this specification use **pure ASCII diagrams** so they render identically in every terminal, IDE, `git diff`, code review, and AI agent context window.

---

## 1. High-Level Architecture ("Three Clients. One Core.")

Matches Page 5 and Page 14 of the Mastek Pulse Proposal:

```text
+====================================================================================================+
|                              CLIENTS  (Shared OpenAPI 3.1 TypeScript Contracts)                    |
|                                                                                                    |
|  +----------------------------+    +----------------------------+    +--------------------------+  |
|  |  Android + iOS App         |    |  Employee Web Experience   |    |  Publishing Portal       |  |
|  |  React Native + Expo + TS  |    |  React 18 + Vite + TS      |    |  React 18 Role Workspace |  |
|  |  ------------------------  |    |  ------------------------  |    |  ----------------------  |  |
|  |  * Native background audio |    |  * Pulse personalized feed |    |  * Review Queue (Priya)  |  |
|  |  * Offline local drafts    |    |  * Listen (Radio/Podcast)  |    |  * Schedule & Publish    |  |
|  |  * OIDC + PKCE login       |    |  * 15-min Connect & Checkin|    |  * Withdraw & Audit Log  |  |
|  +-------------+--------------+    +-------------+--------------+    +-------------+------------+  |
+================|=================================|=================================|===============+
                 |              HTTPS  /api/v1/*   |   (Bearer JWT / Demo Persona)   |
                 +---------------------------------+---------------------------------+
                                                   |
                                                   v
+====================================================================================================+
|                SPRING BOOT 3.3+ MODULAR MONOLITH   (Java 21 Virtual Threads Enabled)               |
|                                                                                                    |
|  +----------------------------------------------------------------------------------------------+  |
|  |  API BOUNDARY & SECURITY PIPELINE                                        (package: common/)  |  |
|  |  [Entra ID OIDC / DemoPersonaFilter] -> [RBAC @PreAuthorize] -> [RateLimit] -> [Idempotency] |  |
|  +--------+----------------------------+----------------------------+--------------------+------+  |
|           |                            |                            |                    |         |
|           v                            v                            v                    v         |
|  +--------------------+   +------------------------+   +-----------------------+   +------------+  |
|  | CONTENT &          |   | LISTEN & PLAYBACK      |   | CONNECTION & FEEDBACK |   | AI & MEDIA |  |
|  | PUBLISHING MODULE  |   | MODULE                 |   | MODULE                |   | MODULE     |  |
|  | (package: content/)|   | (package: listen/)     |   | (package: connection/)|   | (pkg: ai/) |  |
|  | ------------------ |   | ---------------------- |   | --------------------- |   | ---------- |  |
|  | * Audience Filter  |   | * Mastek Radio Queue   |   | * Story Invitations   |   | * 720-Cap  |  |
|  |   -> Rank -> Why   |   | * Culture Cast Chapters|   | * Pessimistic Lock    |   |   Budget   |  |
|  | * Revision History |   | * Cross-Device Resume  |   |   Accept (FOR UPDATE) |   | * Template |  |
|  | * Review / Approve |   | * Caffeine/Redis Cache |   | * Idempotency-Key     |   |   Fallback |  |
|  | * Schedule/Withdraw|   | * 15-m Signed URLs     |   | * Bilateral Check-ins |   | * Prompt   |  |
|  +---------+----------+   +-----------+------------+   +-----------+-----------+   |   Sanitizer|  |
|            |                          |                            |               +-----+--+---+  |
|            |                          |                            |                     |  |      |
|            |    +------------------------------------------------------------------+     |  |      |
|            |    |  TRANSACTIONAL OUTBOX & BACKGROUND WORKER     (package: outbox/) |     |  |      |
|            |    |  * Polls outbox_events (FOR UPDATE SKIP LOCKED) every 5s         |     |  |      |
|            |    |  * Rechecks SCHEDULED items at release time before publishing    |     |  |      |
|            |    |  * Dispatches generic push/email notifications with backoff      |     |  |      |
|            |    +----------------------------------+-------------------------------+     |  |      |
+============|=======================================|=====================================|==|======+
             |                                       |                                     |  |
             +-------------------+-------------------+-------------------------------------+  |
                                 |                                                            |
                                 v                                                            v
+=======================================================================+   +========================+
|  PRIVATE RELATIONAL DATABASE (PostgreSQL 16)                          |   | PRIVATE OBJECT STORAGE |
|  -------------------------------------------------------------------  |   | (S3 / Azure Blob / CDN)|
|  * ACID Transactions & Row Locks (SELECT ... FOR UPDATE)              |   | ---------------------- |
|  * Defense-in-Depth Constraints (chk_invitation_capacity, UNIQUEs)    |   | * MP3 Audio & Media    |
|  * Logical Schema Ownership per Module + outbox_events + audit_events |   | * 15-min Signed URLs   |
+=======================================================================+   +========================+
```

### Module-to-Table Ownership Matrix (Foundation for Modularity)

Every table in [`V1__init_schema.sql`](../../backend/src/main/resources/db/migration/V1__init_schema.sql) has **one owning Java package**. Other modules may read via a service façade or projection, but **never** write to another module's tables directly:

```text
+--------------------+-------------------------------------+-----------------------------------------+
| Java Package       | Owned PostgreSQL Tables (Read/Write)| Published Domain Events (via Outbox)    |
+--------------------+-------------------------------------+-----------------------------------------+
| common/            | users, audiences, audience_members, | AUTH_PERSONA_SWITCHED,                  |
|                    | audit_events                        | SECURITY_AUDIT_RECORDED                 |
+--------------------+-------------------------------------+-----------------------------------------+
| content/           | content_items, content_revisions    | CONTENT_SUBMITTED, CONTENT_APPROVED,    |
|                    |                                     | PUBLISH_DUE, CONTENT_PUBLISHED,         |
|                    |                                     | CONTENT_WITHDRAWN                       |
+--------------------+-------------------------------------+-----------------------------------------+
| listen/            | playback_progress                   | (Reads published audio items via        |
|                    |                                     |  content query façade)                  |
+--------------------+-------------------------------------+-----------------------------------------+
| connection/        | invitations, connection_requests,   | REQUEST_CREATED, REQUEST_ACCEPTED,      |
|                    | participant_confirmations           | REQUEST_DECLINED, FOLLOWUP_DUE          |
+--------------------+-------------------------------------+-----------------------------------------+
| ai/                | ai_usage_ledger                     | AI_BUDGET_ALERT (50%, 80%, 100%)        |
+--------------------+-------------------------------------+-----------------------------------------+
| outbox/            | outbox_events                       | Polls & dispatches all events above     |
+--------------------+-------------------------------------+-----------------------------------------+
```

---

## 2. How This Architecture Scales to 10,000+ Employees

> **🎓 Intern Lesson — Designing for Scale Without Over-Engineering:**
> Juniors often think scaling to 10,000 users requires 15 microservices and Kubernetes on Day 1. That slows down a Hackathon POC! Instead, senior architects use these 5 patterns inside a single Spring Boot deployment so it scales horizontally from 1 laptop instance to N cloud instances with zero code rewrites:

### 10,000-User Production Deployment Topology

```text
                        10,000+ Employees (Mobile + Web + Portal)
                                           |
                    +----------------------+----------------------+
                    |                                             |
                    v  1. API JSON Traffic (/api/v1/*)            v  2. Direct Audio Byte Stream
         +---------------------+                       +---------------------+
         |  HTTPS Load         |                       |  CDN / Private      |
         |  Balancer / WAF     |                       |  Object Storage     |
         +----------+----------+                       |  (15-m Signed URLs) |
                    |                                  +---------------------+
       +------------+------------+                                ^
       |                         |                                |
       v                         v                                | Signs URL only (0 bytes through JVM)
+--------------+         +--------------+                         |
| Spring Boot  |         | Spring Boot  |  ... (N Stateless       |
| Node 1       |         | Node 2       |       Instances) -------+
| ------------ |         | ------------ |
| Java 21      |         | Java 21      |      +----------------------------------+
| Virtual      |         | Virtual      | ---> | Shared Redis Cache               |
| Threads      |         | Threads      |      | (Radio Queue, Culture Cast,      |
| HikariCP(20) |         | HikariCP(20) |      |  Audience Memberships, RateLimit)|
+------+-------+         +------+-------+      +----------------------------------+
       |                        |
       +-----------+------------+
                   |
                   v
    +-----------------------------+            +-----------------------------+
    | PostgreSQL 16 Primary (R/W) | =========> | PostgreSQL 16 Read Replica  |
    | - Row Locks (FOR UPDATE)    |  Streaming | - Heavy Feed / Analytics    |
    | - Outbox (SKIP LOCKED)      |  Repl.     |   read queries (optional)   |
    +-----------------------------+            +-----------------------------+
```

1. **Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`):**
   - Traditional Java platform threads consume ~1 MB of OS stack RAM each, limiting a server to ~200–500 concurrent blocking requests.
   - Java 21 Virtual Threads are lightweight user-mode fibers (~few hundred bytes) managed by the JVM. When 2,000 employees open Mastek Pulse at 9:00 AM and wait on PostgreSQL or an 8-second AI call, the virtual thread unmounts from the OS carrier thread automatically—allowing a single Spring Boot instance to handle thousands of concurrent requests effortlessly.
2. **Audio Offloading via Short-Lived Signed URLs:**
   - Streaming 12-minute MP3s for *Mastek Radio* and *Culture Cast* through the Java API would saturate JVM heap and network NICs.
   - Instead, `GET /api/v1/listen/radio` checks the employee's `audience_id` in PostgreSQL and returns a signed URL (`/media-stream/...` or S3/Blob pre-signed URL with a 15-minute expiry). The client streams audio directly from storage/CDN.
3. **Database Connection Pooling & Pessimistic Locking (`HikariCP` + `FOR UPDATE`):**
   - When Daniel offers `2 places available` on a popular story, 50 people might view it and 5 might click/accept concurrently. Using `SELECT ... FOR UPDATE` on the `invitations` row serializes only the competing writes for that single invitation in under 2 milliseconds while leaving all other invitations unblocked.
4. **Transactional Outbox with `FOR UPDATE SKIP LOCKED`:**
   - Instead of calling slow external push notification APIs (APNs/FCM) inside a user's HTTP transaction, we insert a row into `outbox_events` in the same database commit. Multiple Spring Boot nodes poll `outbox_events` concurrently using `FOR UPDATE SKIP LOCKED`, guaranteeing zero duplicate processing across cluster nodes.
5. **Two-Tier Caching (`Spring Cache` — Caffeine in POC, Redis in Prod):**
   - Read-heavy endpoints (`GET /api/v1/listen/radio`, `GET /api/v1/listen/culture-cast`, approved updates) use `@Cacheable` keyed by the caller's `audience_id` set, switching from in-memory Caffeine (POC) to Redis (10,000-user multi-node production) via `application.yml`.

---

## 3. Monorepo Directory Structure

Every developer and AI agent must place files in these exact directories:

```text
Mastek-Pulse/
├── CLAUDE.md                        # Claude Code entry instructions -> points to docs/aidlc/
├── AGENTS.md                        # Codex / Cursor / Universal Agent instructions -> points to docs/aidlc/
├── .github/
│   └── copilot-instructions.md      # GitHub Copilot instructions -> points to docs/aidlc/
├── learn.md                         # Living AI-DLC Classroom Textbook & ADR Decision Log
├── README.md                        # Setup, run instructions, and architecture overview
├── docs/
│   └── aidlc/                       # THE SINGLE SOURCE OF TRUTH (AI-DLC Hub)
│       ├── 01-inception-vision-and-scope.md
│       ├── 02-domain-model-and-contracts.md
│       ├── 03-architecture-and-guardrails.md
│       ├── 04-task-backlog.md
│       └── openapi.yaml             # OpenAPI 3.1 shared API contract
├── backend/                         # Java 21 + Spring Boot 3.3+ Modular Monolith
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/mastek/pulse/
│       │   │   ├── common/          # Auth, Security, Idempotency, Exception Handling, Audit
│       │   │   ├── content/         # Stories, Updates, Revisions, Review & Publish Workflow
│       │   │   ├── listen/          # Mastek Radio, Culture Cast, Playback Progress
│       │   │   ├── connection/      # Invitations, Locked Accept Flow, Bilateral Confirmations
│       │   │   ├── ai/              # Optional AI Adapter, 720-Action Budget Gate, Template Fallback
│       │   │   └── outbox/          # Transactional Outbox & Background Worker
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/    # Flyway SQL migrations + Synthetic Pilot Seed Data
│       └── test/                    # Integration, Security, Concurrency & AI Evaluation Tests
├── web/                             # React 18 + TypeScript + Vite + Tailwind CSS
│   └── src/
│       ├── api/                     # Shared typed API client & Persona Switcher state
│       ├── employee/                # Employee Web Experience (Pulse, Listen, Connect, Contribute)
│       └── portal/                  # Role-Based Publishing Portal (Review Queue, Schedule, Audit)
└── mobile/                          # React Native (Expo) + TypeScript (Android & iOS)
    └── src/
        ├── api/                     # Shared typed API client
        ├── screens/                 # Pulse, Story, Invite Composer, Connections, Listen, Publish
        └── audio/                   # Native background audio & cross-device progress sync
```

---

## 4. Security, Module Isolation, and Coding Standards for All AI Agents

### A. Security & Trust Rules (From Proposal Pages 4, 5, 11, 14, 15)
1. **Filter Audience BEFORE Ranking:** Every query for stories, updates, Radio queues, or Culture Cast episodes **must** filter by the caller's permitted `audience_id` before applying interest ranking or returning data.
2. **No Self-Approval:** In `ContentReviewService`, a `REVIEWER` cannot approve a revision where `content_item.author_id == current_user.id` (must throw `403 Forbidden`).
3. **Edit Invalidates Approval:** Any edit to a content item's title, body, media, or audience when it is `APPROVED` or `SCHEDULED` must create a new revision and reset the status to `IN_REVIEW`.
4. **AI Trust & Hard Budget Cap:**
   - Track AI token/action usage against the 720-action budget cap (alert thresholds at 50%, 80%, 100%).
   - At 100% cap (or if AI is unavailable/toggled off), automatically return the deterministic **non-AI template fallback**.
   - Never auto-publish or auto-send AI outputs; label all AI suggestions clearly (`aiAssisted: true`).
5. **Privacy & Minimum Collection:** Never compute employee appraisal scores or public popularity leaderboards. Count a connection as "useful" only when both participants confirm `happened = YES` and `useful = true`.

### B. Strict Module Isolation Rules (Why Our Monolith Never Turns Into Spaghetti)

To keep the Modular Monolith ready for future microservice extraction (Section 6), every AI agent and developer must obey these boundary rules:

```text
   ALLOWED: Cross-Module Call via Public Service Interface or Outbox Event
   =======================================================================
   +-----------------------------------+       +-----------------------------------+
   | connection/                       |       | content/                          |
   |  ConnectionRequestService         | ----> |  ContentAudienceFacade (Interface)|
   |    .create()                      |  DTO  |    .isUserInStoryAudience(...)    |
   +-----------------+-----------------+       +-----------------+-----------------+
                     |                                           |
                     v                                           v
              invitations repo                           content_items repo

   FORBIDDEN: Reaching Directly into Another Module's Repository or JPA Entity
   ===========================================================================
   +-----------------------------------+       +-----------------------------------+
   | connection/                       |  XXX  | content/                          |
   |  ConnectionRequestService         | --X-> |  ContentItemRepository (INTERNAL) |
   +-----------------------------------+       +-----------------------------------+
```

1. **No Cross-Package Repository Injection:** A class in `connection/` must **never** inject `ContentItemRepository` directly; call a thin read façade (`ContentQueryFacade`) or validate via a well-defined query method.
2. **No Cross-Package JPA `@ManyToOne` Entity Graphs:** Store foreign keys across module boundaries as plain scalar IDs (`String storyId`, `String userId`) rather than JPA object references. That way, moving a module to its own database later requires zero JPA entity refactoring.
3. **Async Side Effects via `outbox_events`:** Whenever a state change in one module triggers work in another (e.g., publishing content invalidates caches or sends notifications), emit an `OutboxEventEntity` inside the same transaction.

### C. Intern-Level Documentation Standard (Mandatory!)
Every file generated by any developer or AI tool **must** include **Intern-Level Documentation**:
- **File Header Comment:** A clear 3–5 line explanation at the top of every `.java`, `.ts`, `.tsx`, and `.sql` file explaining:
  1. *What* this file does in plain English.
  2. *Which user journey or proposal requirement* it supports.
  3. *Why* it is designed this way (e.g., explaining `@Lock(LockModeType.PESSIMISTIC_WRITE)` or `Idempotency-Key` to an intern).
- **Method & Complex Logic Comments:** Explain non-obvious business rules inline so a newly onboarded intern can trace the entire flow without guessing.

---

## 5. Detailed Code Flow Diagrams

> **🎓 Intern & AI Agent Guide — How to read these flows:**
> Each diagram traces one HTTP request (or background job) from the client through Controller -> Service -> Repository -> PostgreSQL and back.
> - `[Class.method()]` = the Spring bean / method that owns the step.
> - `==>` = happy path, `--x` = guardrail rejection with its exact HTTP status from [`02-domain-model-and-contracts.md`](02-domain-model-and-contracts.md#4-rest-api-endpoints--standard-http-status-codes).
> - `+--- TX (@Transactional) ---+` = everything inside commits or rolls back **atomically** in one database transaction.

### 5.1 Common Request Pipeline (every `/api/v1/*` call)

Every module flow in 5.2–5.11 starts **after** steps `[1]`–`[3]` below have succeeded.

```text
 Client (Mobile / Employee Web / Publishing Portal)
   |
   |  HTTPS  GET | POST | PUT | PATCH  /api/v1/...
   |  Headers: X-Pulse-Persona: <userId>          (POC Demo Persona Switcher)
   |           Authorization: Bearer <oidc-jwt>   (Production Entra ID OIDC)
   |           Idempotency-Key: <uuid>            (Mandatory on POST /api/v1/requests)
   v
+---------------------------------------------------------------------------------------+
| Embedded Tomcat accepts socket -> dispatches onto a Java 21 VIRTUAL THREAD            |
| (spring.threads.virtual.enabled=true; blocking DB/AI I/O unmounts carrier thread)     |
+---------------------------------------------------------------------------------------+
   |
   v
[1] Identity & Auth Resolution  (common/IdentityService + SecurityFilterChain)
   |-- Resolve caller identity from Bearer JWT or X-Pulse-Persona header
   |     --x unknown / expired / invalid token -----------------------> 401 Unauthorized
   |-- Load UserEntity + audienceIds[] from audience_members
   v
[2] Controller Layer  (@RestController + @Valid DTO Bean Validation)
   |-- Validate request body / query params / required headers
   |     --x blank message, capacity < 1, missing Idempotency-Key ----> 422 Unprocessable Entity
   |-- Check role guard (CREATOR, REVIEWER, PUBLISHER, MODERATOR, ADMIN)
   |     --x caller lacks required role ------------------------------> 403 Forbidden
   v
[3] Domain Service Layer  (@Service + @Transactional)
   |-- Enforce domain guardrails:
   |     * Audience membership check before read/write ---------------> 403 Forbidden
   |     * No self-approval (approved_by != author_id) ---------------> 403 Forbidden
   |     * Optimistic version match / capacity / state check ---------> 409 Conflict
   v
[4] Repository Layer (Spring Data JPA) ==> HikariCP ==> PostgreSQL 16
   |-- Execute parameterized SQL + write audit_events & outbox_events in same TX
   v
[5] Map Entity -> Response DTO ==> 200 OK / 201 Created (JSON)

   Exception Path (any step [1]-[4] throws PulseException):
   `--> GlobalExceptionHandler (@RestControllerAdvice)
          `--> returns { timestamp, status, error, message, path } (zero stack-trace leakage)
```

---

### 5.2 Persona Switch & Identity (`GET /api/v1/me`, `POST /api/v1/auth/demo-switch`)

```text
 Employee Web / Portal Header: User selects "Priya Nair (Reviewer)"
   |
   |  POST /api/v1/auth/demo-switch   { "personaId": "user_priya" }
   v
[IdentityController.demoSwitch()]
   |-- Check pulse.demo-mode-enabled == true
   |     --x false (Production environment) --------------------------> 403 Forbidden
   v
[IdentityService.switchPersona("user_priya")]
   |
   +--- TX (@Transactional) ------------------------------------------------------------+
   |  1. userRepo.findById("user_priya")                                                |
   |       --x user row missing --------------------------------------> 422 / 401       |
   |  2. audienceRepo.findAudienceIdsByUserId("user_priya")                             |
   |       --> ["aud_all", "aud_uk_eng", "aud_leadership"]                              |
   |  3. auditEventRepo.save(AuditEvent("USER", "user_priya", "DEMO_PERSONA_SWITCH"))   |
   +------------------------------------------------------------------------------------+
   |
   v
 200 OK  UserProfileDto {
           id: "user_priya",
           displayName: "Priya Nair",
           roles: ["EMPLOYEE", "CREATOR", "REVIEWER"],
           audienceIds: ["aud_all", "aud_uk_eng", "aud_leadership"],
           interests: ["delivery", "architecture", "inclusion"]
         }
   |
   `--> Client stores X-Pulse-Persona: user_priya and refreshes view;
        Publishing Portal unlocks the "Review Queue" tab automatically.
```

---

### 5.3 Feed Discovery: Filter -> Rank -> Explain (`GET /api/v1/feed`, `GET /api/v1/stories/{id}`)

```text
 Asha (user_asha) opens the Pulse Tab
   |
   |  GET /api/v1/feed?contentType=ALL
   v
[FeedController.getFeed()] --> [FeedService.getPersonalizedFeed(asha)]
   |
   |  +===============================================================================+
   |  | STEP 1: HARD AUDIENCE FILTER (Security Boundary — MUST execute in SQL first)  |
   |  | ----------------------------------------------------------------------------- |
   |  | SELECT ci.*, cr.*                                                             |
   |  |   FROM content_items ci                                                       |
   |  |   JOIN content_revisions cr                                                   |
   |  |     ON cr.content_item_id = ci.id AND cr.version_number = ci.published_version|
   |  |  WHERE ci.status = 'PUBLISHED'                                                |
   |  |    AND ci.audience_id IN ('aud_all', 'aud_uk_eng')  <-- Asha's audiences ONLY |
   |  |    AND (ci.expires_at IS NULL OR ci.expires_at > CURRENT_TIMESTAMP)           |
   |  +===============================================================================+
   |    * Stories for 'aud_hr_confidential' are filtered out at the DB level and
   |      NEVER enter JVM memory.
   v
   |  +===============================================================================+
   |  | STEP 2: DETERMINISTIC RANKING (Over permitted rows only)                      |
   |  | ----------------------------------------------------------------------------- |
   |  | For each permitted item:                                                      |
   |  |   matchedTags = intersect(cr.tags_csv, asha.interests_csv)                    |
   |  |   openInvite  = inv != null && !inv.paused && inv.acceptedCount < inv.capacity|
   |  |                 && inv.expiresAt > now()                                      |
   |  |   rankScore   = (matchedTags.size() * 30)                                     |
   |  |               + (openInvite ? 25 : 0)                                         |
   |  |               + recencyScore(ci.published_at)                                 |
   |  | * ZERO popularity leaderboards, ZERO like counts, ZERO employee scoring.      |
   |  +===============================================================================+
   v
   |  +===============================================================================+
   |  | STEP 3: TRANSPARENT "WHY THIS?" EXPLANATION                                   |
   |  | ----------------------------------------------------------------------------- |
   |  | If matchedTags not empty:                                                     |
   |  |   explanation = "WHY THIS? Matches your interest: " + matchedTags.first()     |
   |  | Else:                                                                         |
   |  |   explanation = "WHY THIS? Shared with " + audience.name                      |
   |  +===============================================================================+
   v
 200 OK  [ ContentCardDto { id, title, summary, explanation, invitationSummary } ]

 Single-Story Guardrail (`GET /api/v1/stories/{id}`):
   `--> Loads story -> checks `asha.audienceIds.contains(story.audienceId)`
        If false --x 403 Forbidden (never leaks restricted story metadata).
```

---

### 5.4 Listen: Mastek Radio, Culture Cast & Cross-Device Resume (`GET /api/v1/listen/*`, `PUT /progress`)

```text
 Mobile / Web Listen Tab
   |
   |  GET /api/v1/listen/culture-cast
   v
[ListenController.getCultureCast()] --> [ListenService.getCultureCastEpisodes(asha)]
   |
   |-- Check @Cacheable("cultureCast", key = asha.audienceIdsHash)
   |     |-- Cache HIT  -----------------------------------------------+
   |     |-- Cache MISS:                                               |
   |     |     Query PUBLISHED items WHERE content_type='CULTURE_CAST' |
   |     |       AND audience_id IN (:ashaAudienceIds)                 |
   |     |     Join latest PUBLISHED revision (published_version)      |
   |     v     Store metadata in cache (without signed URLs)           |
   |<----+-------------------------------------------------------------+
   |
   |-- Merge caller's personal `playback_progress` row (user_id = asha.id)
   |-- Generate fresh 15-minute signed URL per episode:
   |     [MediaSigningService.signUrl(audioAssetKey, Duration.ofMinutes(15))]
   v
 200 OK  [ AudioEpisodeDto {
             id: "cc_ep_03",
             publishedVersion: 2,          <-- V2 stays live even while V3 is IN_REVIEW!
             audioSignedUrl: "https://media.mastekpulse.internal/cc_ep_03_v2.mp3?sig=...&exp=...",
             chaptersJson: "[{\"title\":\"00:00 Intro\"...}]",
             transcriptText: "...",
             resumePositionSeconds: 185
           } ]
   |
   |  Direct Audio Byte Stream (bypasses Spring Boot completely)
   v
 +---------------------------------------------------------------------+
 | Private Object Storage / CDN streams MP3 range requests to client   |
 +---------------------------------------------------------------------+

 Cross-Device Progress Sync (`PUT /api/v1/listen/progress`):
   Client sends `{ contentItemId: "cc_ep_03", positionSeconds: 240, completed: false }`
     --> `{ TX: UPSERT playback_progress (user_id, content_item_id) }` --> 200 OK
```

---

### 5.5 Send a Connection Request with `Idempotency-Key` (`POST /api/v1/requests`)

```text
 Asha taps "Send 15-min Chat Request" to Daniel (Mobile retries on spotty Wi-Fi)
   |
   |  POST /api/v1/requests
   |  Header: Idempotency-Key: "idem-asha-inv01-001"
   |  Body:   { "invitationId": "inv_story_01", "messageText": "...", "aiAssisted": true }
   v
[ConnectionController.createRequest()]
   |-- Missing/blank Idempotency-Key header? --x -----------------------> 422 Unprocessable Entity
   |-- messageText blank or > 600 chars?     --x -----------------------> 422 Unprocessable Entity
   v
[ConnectionService.createRequest(asha, idempotencyKey, body)]
   |
   +--- TX (@Transactional) ------------------------------------------------------------+
   |                                                                                    |
   |  1. IDEMPOTENCY CHECK (Safe Mobile Retry)                                          |
   |     existing = requestRepo.findByRequesterIdAndIdempotencyKey(asha.id, key)        |
   |     if (existing.isPresent())                                                      |
   |       ==> return existing ConnectionRequestDto (200 OK, zero duplicate rows!)      |
   |                                                                                    |
   |  2. LOAD INVITATION & VERIFY AUDIENCE                                              |
   |     inv = invitationRepo.findById("inv_story_01")  --x missing ----> 422 / 403     |
   |     story = contentFacade.getStory(inv.storyId)                                    |
   |     if (!asha.audienceIds.contains(story.audienceId))                              |
   |       --x caller excluded from story audience ---------------------> 403 Forbidden |
   |     if (inv.hostId.equals(asha.id))                                                |
   |       --x host cannot request own invitation ----------------------> 422 Unproc.   |
   |                                                                                    |
   |  3. CHECK INVITATION AVAILABILITY                                                  |
   |     if (inv.paused || inv.expiresAt.isBefore(now())                                |
   |         || inv.acceptedCount >= inv.capacity)                                      |
   |       --x invitation closed or at full capacity -------------------> 409 Conflict  |
   |     if (requestRepo.existsByInvitationIdAndRequesterId(inv.id, asha.id))           |
   |       --x duplicate request for same invitation -------------------> 409 Conflict  |
   |                                                                                    |
   |  4. PERSIST REQUEST + OUTBOX + AUDIT TOGETHER                                      |
   |     req = requestRepo.save(new ConnectionRequest(PENDING, version=1, key))         |
   |     outboxRepo.save(OutboxEvent("CONNECTION_REQUEST", req.id, "REQUEST_CREATED"))  |
   |     auditRepo.save(AuditEvent("CONNECTION_REQUEST", req.id, "CREATE_REQUEST"))     |
   |                                                                                    |
   +------------------------------------------------------------------------------------+
   |
   v
 201 Created  ConnectionRequestDto { id, state: "PENDING", version: 1, aiAssisted: true }
```

---

### 5.6 Locked Accept Flow — One Transaction (`PATCH /api/v1/requests/{id}`)

Matches the 4-Step "Accept Flow / One Transaction" mandate on Proposal Page 15:

```text
 Daniel (Host) clicks "Accept" on Asha's request
   |
   |  PATCH /api/v1/requests/req_01   { "action": "ACCEPT", "expectedVersion": 1 }
   v
[ConnectionController.updateRequest()] --> [ConnectionService.handleDecision(daniel, req_01, dto)]
   |
   +--- TX (@Transactional — Single Atomic Database Transaction) -----------------------+
   |                                                                                    |
   |  STEP 1: Load Request & Verify State + Optimistic Version                          |
   |    req = requestRepo.findById("req_01")                                            |
   |    if (req.version != expectedVersion || req.state != PENDING)                     |
   |      --x stale version or already decided -------------------------> 409 Conflict  |
   |                                                                                    |
   |  STEP 2: Acquire PESSIMISTIC WRITE Lock on Invitation Row                          |
   |    +----------------------------------------------------------------------------+  |
   |    | SELECT * FROM invitations WHERE id = :invitationId FOR UPDATE              |  |
   |    | (@Lock(LockModeType.PESSIMISTIC_WRITE))                                    |  |
   |    | Blocks any concurrent accept on THIS invitation until COMMIT/ROLLBACK.     |  |
   |    +----------------------------------------------------------------------------+  |
   |    if (!inv.hostId.equals(daniel.id))                                              |
   |      --x caller is not the invitation host ------------------------> 403 Forbidden |
   |    if (inv.paused || inv.expiresAt.isBefore(now()))                                |
   |      --x invitation paused or expired -----------------------------> 409 Conflict  |
   |    if (inv.acceptedCount >= inv.capacity)                                          |
   |      --x full capacity reached (accepted_count == capacity) -------> 409 Conflict  |
   |                                                                                    |
   |  STEP 3: Mutate State + Increment Count + Write Outbox & Audit                     |
   |    req.setState(ACCEPTED);                                                         |
   |    req.setVersion(req.version + 1);                                                |
   |    inv.setAcceptedCount(inv.acceptedCount + 1);                                    |
   |    outboxRepo.save(OutboxEvent("CONNECTION_REQUEST", req.id, "REQUEST_ACCEPTED"))  |
   |    auditRepo.save(AuditEvent("CONNECTION_REQUEST", req.id, "ACCEPT_REQUEST"))      |
   |                                                                                    |
   +--- COMMIT (Row lock released; DB CHECK accepted_count <= capacity verified) -------+
   |
   v
 200 OK  ConnectionRequestDto { id: "req_01", state: "ACCEPTED", version: 2, placesRemaining: 0 }
   |
   `--> STEP 4 (Asynchronous): Outbox worker picks up REQUEST_ACCEPTED and sends generic notification.
```

#### Timeline Proof: Why `SELECT ... FOR UPDATE` Prevents Over-Accepting (`capacity = 2`, `accepted_count = 1`)

```text
 Time   Thread A (Accepts Request #2)            Thread B (Accepts Request #3 at same millisecond)
 ----   --------------------------------------   -------------------------------------------------
 t0     BEGIN TX-A                               BEGIN TX-B
 t1     SELECT * FROM invitations                SELECT * FROM invitations
          WHERE id='inv_01' FOR UPDATE;            WHERE id='inv_01' FOR UPDATE;
        --> Lock GRANTED (reads count = 1)       --> BLOCKED (waits on TX-A row lock)
 t2     Check: 1 < 2 (OK!)                             |
        UPDATE invitations SET accepted_count=2        | (Virtual thread parks cheaply;
        UPDATE connection_requests SET ACCEPTED        |  0% CPU burned while waiting)
        INSERT INTO outbox_events ...                  |
 t3     COMMIT TX-A (Lock Released) -----------> Lock GRANTED! Re-reads latest committed row:
 t4                                              Reads accepted_count = 2, capacity = 2
 t5                                              Guard: acceptedCount (2) >= capacity (2) !
 t6                                              ROLLBACK TX-B --> returns HTTP 409 Conflict
                                                 ("Full capacity reached (2 places filled)")
```

---

### 5.7 Bilateral Private Confirmations (`POST /api/v1/requests/{id}/confirmations`)

```text
 After the 15-min conversation, Asha and Daniel each complete their private check-in
   |
   |  POST /api/v1/requests/req_01/confirmations
   |  Body: { "happened": "YES", "useful": true }
   v
[ConnectionController.submitConfirmation()] --> [ConfirmationService.record(caller, req_01, body)]
   |
   |-- req = requestRepo.findById("req_01")
   |-- caller.id == req.requesterId || caller.id == inv.hostId ?
   |     --x false (Neither participant; even ADMIN cannot spoof) ------> 403 Forbidden
   |-- req.state == ACCEPTED ?
   |     --x false (Cannot confirm PENDING/DECLINED/CANCELLED request) -> 409 Conflict
   |
   +--- TX (@Transactional) ------------------------------------------------------------+
   |  UPSERT participant_confirmations (request_id, user_id, happened, useful)          |
   |    (Enforced by UNIQUE(request_id, user_id))                                       |
   |  Compute bilateral outcome across both participants' rows:                         |
   |                                                                                    |
   |  +-----------------------+-----------------------+------------------------------+  |
   |  | Requester (Asha)      | Host (Daniel)         | Computed Bilateral Outcome   |  |
   |  +-----------------------+-----------------------+------------------------------+  |
   |  | YES + useful = true   | YES + useful = true   | USEFUL (Counts in metrics!)  |  |
   |  | YES + useful = false  | (Any)                 | NOT_USEFUL                   |  |
   |  | NO                    | (Any)                 | DID_NOT_HAPPEN               |  |
   |  | NOT_YET or Missing    | YES + useful = true   | UNKNOWN (Never assumed!)     |  |
   |  +-----------------------+-----------------------+------------------------------+  |
   +------------------------------------------------------------------------------------+
   |
   v
 200 OK  ConfirmationResultDto { recorded: true, bilateralStatus: "USEFUL" | "UNKNOWN" ... }
         (Individual partner response details are never exposed on public profiles or leaderboards)
```

---

### 5.8 Editorial & Publishing State Machine (`content_items.status`)

```text
                       Creator creates draft
            (*) ----------------------------------> +-------------+
                                                    |    DRAFT    | <-------------------------+
                                                    +------+------+                           |
                                                           |                                  |
                              POST /content/{id}/submit    |                                  |
                              (CREATOR / author)           v            POST /review          |
                                                    +-------------+     (REQUEST_CHANGES)     |
         +----------------------------------------> |  IN_REVIEW  | --------------------------+
         |                                          +------+------+
         |                                                 |
         |  PUT /content/{id}/revisions                    |  POST /content/{id}/review
         |  (Editing title, body, media, or audience       |  decision = APPROVE
         |   creates Revision N+1, sets approved_by=NULL,  |  Guard: reviewer.id != author.id
         |   and INVALIDATES prior approval!)              v
         |                                          +-------------+
         +----------------------------------------- |  APPROVED   |
         |                                          +------+------+
         |                                                 |
         |                    +----------------------------+----------------------------+
         |                    | POST /publish                                           | POST /publish
         |                    | (scheduledAt in future)                                 | (immediate)
         |                    v                                                         |
         |              +-------------+     Outbox Worker at scheduledAt                |
         +------------- |  SCHEDULED  | ----------------------------------------------+ |
            Edit        +-------------+     (Verifies status==SCHEDULED,              | |
            invalidates                      revision still approved & media ready)   v v
            schedule!                                                           +-------------+
                                                                                |  PUBLISHED  |
                                                                                +------+------+
                                                                                       |
                                                       POST /content/{id}/withdraw     |
                                                       (PUBLISHER or MODERATOR)        v
                                                                                +-------------+
                                                                                |  WITHDRAWN  |
                                                                                +-------------+

   KEY REVISION INVARIANT (Proposal Pages 11 & 15):
   ================================================
   content_items tracks BOTH `published_version` (live for employees) and `current_version` (latest draft).
   If Revision 2 is PUBLISHED (`published_version = 2`) and the author edits it, Revision 3 is created
   (`current_version = 3, status = IN_REVIEW`) while Revision 2 remains safely live until Revision 3 is published!
```

---

### 5.9 Review, Approve, Schedule, Publish & Withdraw Flows (`/api/v1/content/*`)

```text
 (A) SUBMIT FOR REVIEW  —  POST /api/v1/content/{id}/submit
 ----------------------------------------------------------
 [ContentWorkflowService.submit(caller, contentId)]
   |-- caller.id == item.authorId || caller.hasRole(CREATOR) ? --x -----> 403 Forbidden
   |-- item.status == DRAFT ?                                  --x -----> 409 Conflict
   `-- { TX: item.status = IN_REVIEW; audit("SUBMIT_FOR_REVIEW"); } ----> 200 OK

 (B) REVIEW & APPROVE   —  POST /api/v1/content/{id}/review  { revisionNumber, decision, comments }
 --------------------------------------------------------------------------------------------------
 [ContentReviewService.review(reviewer, contentId, dto)]
   |-- reviewer.hasRole(REVIEWER) ?                            --x -----> 403 Forbidden
   |-- item.authorId.equals(reviewer.id) ?  (NO SELF-APPROVAL) --x -----> 403 Forbidden
   |-- item.status == IN_REVIEW
   |   && item.currentVersion == dto.revisionNumber ?          --x -----> 409 Conflict (Stale revision)
   `-- +--- TX (@Transactional) --------------------------------------------------------+
       | If decision == APPROVE:                                                        |
       |   revision.approvedBy = reviewer.id; revision.approvedAt = now();              |
       |   item.status = APPROVED;                                                      |
       | If decision == REQUEST_CHANGES:                                                |
       |   item.status = DRAFT; revision.reviewerNotes = dto.comments;                  |
       | auditRepo.save(AuditEvent("CONTENT_ITEM", item.id, "REVIEW_" + decision));     |
       +--------------------------------------------------------------------------------+
       --> 200 OK

 (C) EDIT AFTER APPROVAL — PUT /api/v1/content/{id}/revisions  { title, body, audienceId, ... }
 ----------------------------------------------------------------------------------------------
 [ContentRevisionService.createNewRevision(editor, contentId, dto)]
   |-- item.status == WITHDRAWN ?                              --x -----> 409 Conflict
   `-- +--- TX (@Transactional) --------------------------------------------------------+
       | newVer = item.currentVersion + 1;                                              |
       | revisionRepo.save(new ContentRevision(newVer, dto, approvedBy = NULL));        |
       | item.currentVersion = newVer;                                                  |
       | if (item.status in [APPROVED, SCHEDULED, PUBLISHED]) {                         |
       |   item.status = IN_REVIEW;           // Approval invalidated automatically!    |
       |   item.scheduledAt = null;                                                     |
       |   outboxRepo.cancelPendingScheduleEvents(item.id);                             |
       |   auditRepo.save(AuditEvent("CONTENT_ITEM", item.id, "APPROVAL_INVALIDATED")); |
       | }                                                                              |
       +--------------------------------------------------------------------------------+
       --> 200 OK { currentVersion: newVer, publishedVersion: (unchanged), status: "IN_REVIEW" }

 (D) PUBLISH OR SCHEDULE — POST /api/v1/content/{id}/publish  { scheduledAt?, expiresAt? }
 -----------------------------------------------------------------------------------------
 [ContentPublishService.publish(publisher, contentId, dto)]
   |-- publisher.hasRole(PUBLISHER) ?                          --x -----> 403 Forbidden
   |-- item.status == APPROVED
   |   && currentRevision.approvedBy != null ?                 --x -----> 409 Conflict
   `-- +--- TX (@Transactional) --------------------------------------------------------+
       | If scheduledAt == null || scheduledAt <= now():                                |
       |   item.status = PUBLISHED; item.publishedVersion = item.currentVersion;        |
       |   item.publishedAt = now();                                                    |
       |   outboxRepo.save(OutboxEvent("CONTENT_ITEM", item.id, "CONTENT_PUBLISHED"));  |
       | Else (Future Release):                                                         |
       |   item.status = SCHEDULED; item.scheduledAt = dto.scheduledAt;                 |
       |   outboxRepo.save(OutboxEvent("CONTENT_ITEM", item.id, "PUBLISH_DUE",          |
       |                               nextRun = dto.scheduledAt));                     |
       | auditRepo.save(AuditEvent("CONTENT_ITEM", item.id, "PUBLISH_OR_SCHEDULE"));    |
       +--------------------------------------------------------------------------------+
       --> 200 OK

 (E) WITHDRAW            — POST /api/v1/content/{id}/withdraw { reason }
 -----------------------------------------------------------------------
   |-- caller.hasAnyRole(PUBLISHER, MODERATOR) ?               --x -----> 403 Forbidden
   `-- { TX: item.status = WITHDRAWN; item.publishedVersion = null;
             evictCaches(); audit("WITHDRAW_CONTENT"); } ---------------> 200 OK
```

---

### 5.10 Transactional Outbox Poller & Scheduled Release Verification (`outbox/`)

```text
  Domain Transaction (e.g., Accept or Schedule)           PostgreSQL 16 (outbox_events)
  +-------------------------------------------+           +---------------------------------------+
  | 1. Mutate domain row (invitations / items)| ==Commit=>| event_id | event_type       | status  |
  | 2. Insert OutboxEventEntity (PENDING)     |  Together | ---------+------------------+-------- |
  +-------------------------------------------+           | evt_101  | REQUEST_ACCEPTED | PENDING |
                                                          | evt_102  | PUBLISH_DUE      | PENDING |
                                                          +------------------+--------------------+
                                                                             |
  @Scheduled(fixedDelay = 5000) [OutboxWorker.processPendingEvents()]        |
    |                                                                        |
    +--- TX (@Transactional) ------------------------------------------------+
    |                                                                        |
    |  1. Claim Batch Without Blocking Peer Nodes:                           |
    |     SELECT * FROM outbox_events                                <-------+
    |      WHERE status = 'PENDING' AND next_run <= CURRENT_TIMESTAMP
    |      ORDER BY next_run ASC LIMIT 50
    |      FOR UPDATE SKIP LOCKED;
    |
    |  2. Dispatch by `event_type`:
    |     +----------------------------------------------------------------------------------+
    |     | Case "PUBLISH_DUE":                                                              |
    |     |   Re-verify item in DB at release time:                                          |
    |     |     * Is item.status still SCHEDULED?                                            |
    |     |     * Is currentRevision.approvedBy still non-null (not edited since approval)?  |
    |     |     * Are audio/media assets present and verified?                               |
    |     |   If ALL true  --> item.status = PUBLISHED; item.publishedVersion = currentVer;  |
    |     |   If ANY false --> abort release; write audit("SCHEDULE_RELEASE_BLOCKED");       |
    |     +----------------------------------------------------------------------------------+
    |     | Case "REQUEST_CREATED" | "REQUEST_ACCEPTED" | "CONTENT_PUBLISHED":               |
    |     |   Send generic notification (respecting user's notification_frequency):          |
    |     |   "You have an update in Mastek Pulse" (never leaks private message text)        |
    |     +----------------------------------------------------------------------------------+
    |
    |  3. Update Outbox Row Status:
    |     * On Success --> status = 'PROCESSED'
    |     * On Failure --> attempts += 1;
    |                      if (attempts >= 5) status = 'FAILED';
    |                      else next_run = now() + (2 ^ attempts) minutes;
    +----------------------------------------------------------------------------------------+
```

---

### 5.11 Optional AI Assistance, 720-Action Budget Gate & Template Fallback (`ai/`)

```text
 User clicks "Draft with AI" (Invitation Request / Story Polish / Update Summary)
   |
   |  POST /api/v1/ai/drafts   { "assistType": "INVITATION_DRAFT", "contextId": "story_01", ... }
   v
[AiController.generateDraft()] --> [AiAssistanceService.assist(caller, dto)]
   |
   |  STEP 1: ALWAYS Build Deterministic Non-AI Template Fallback First (0 ms, $0 cost)
   |    templateFallback = [TemplateFallbackBuilder.build(dto.assistType, story, caller)]
   |    (Guarantees the user can complete every workflow even if AI is capped or offline!)
   v
   |  STEP 2: Check Audience Permission on Context Item
   |    Verify caller is in `story.audienceId` --x excluded ------------> 403 Forbidden
   v
   |  STEP 3: Atomic 720-Action Budget Gate (`ai_usage_ledger`)
   |  +--- TX (@Transactional) ---------------------------------------------------------+
   |  | usedActions = aiUsageLedgerRepo.countAllPilotActions();                         |
   |  |                                                                                 |
   |  | if (!aiProperties.isEnabled() || usedActions >= 720) {                          |
   |  |   // HARD CAP REACHED (100% = 720 actions) OR AI TOGGLED OFF                    |
   |  |   return AiAssistanceResponse {                                                 |
   |  |     aiAvailable: false,                                                         |
   |  |     aiAssisted: false,                                                          |
   |  |     suggestion: templateFallback,       <-- Seamless deterministic fallback!    |
   |  |     templateFallback: templateFallback,                                         |
   |  |     budgetUsed: usedActions, budgetCap: 720, alertLevel: "CAP_REACHED_100"      |
   |  |   };                                                                            |
   |  | }                                                                               |
   |  |                                                                                 |
   |  | // Within budget (< 720): sanitize prompt (strip PII/other users' data)         |
   |  | aiText = aiClient.complete(sanitizedPrompt);                                    |
   |  | aiUsageLedgerRepo.save(new AiUsageLedgerEntity(caller.id, dto.assistType, ...));|
   |  | newCount = usedActions + 1;                                                     |
   |  |                                                                                 |
   |  | // Check 50% (360), 80% (576), 100% (720) threshold alerts:                     |
   |  | if (newCount == 360 || newCount == 576 || newCount == 720) {                    |
   |  |   outboxRepo.save(OutboxEvent("AI_BUDGET", "PILOT", "AI_BUDGET_ALERT"));        |
   |  |   auditRepo.save(AuditEvent("AI_BUDGET", "PILOT", "THRESHOLD_" + newCount));    |
   |  | }                                                                               |
   |  +---------------------------------------------------------------------------------+
   v
 200 OK  AiAssistanceResponse {
           aiAvailable: true,
           aiAssisted: true,                    <-- Labeled "AI-Assisted Suggestion" in UI
           suggestion: "Hi Daniel, I read your story on...",
           templateFallback: "Hi Daniel, I'd love to join a 15-minute conversation...",
           budgetUsed: 4,
           budgetCap: 720,
           alertLevel: "NORMAL"                 <-- NORMAL | WARN_50 | WARN_80 | CAP_REACHED_100
         }
   |
   `--> Human-in-the-Loop Rule: UI places `suggestion` into an editable textarea.
        It is NEVER auto-sent or auto-published without explicit human submission.
```

---

### 5.12 End-to-End Multi-Persona Journey Map

```text
 Asha (Employee)            Daniel (Creator & Host)    Priya (Reviewer)        Marcus (Publisher)
 ====================       =======================    ====================    ====================
                            1. POST /api/v1/content
                               (Creates DRAFT v1)
                            2. POST /content/{id}/submit
                               (DRAFT -> IN_REVIEW)
                                                       3. POST /content/{id}/review
                                                          (Checks priya != daniel;
                                                           IN_REVIEW -> APPROVED)
                                                                               4. POST /content/{id}/publish
                                                                                  (APPROVED -> PUBLISHED)
 5. GET /api/v1/feed <----------------------------------------------------------- [Story is live in feed!]
    (Filter audience ->
     Rank interests ->
     "WHY THIS?" badge)

 6. POST /api/v1/ai/drafts
    (Checks 720-cap ->
     returns AI draft +
     template fallback)

 7. POST /api/v1/requests
    (Idempotency-Key ->
     state = PENDING) ----> 8. PATCH /api/v1/requests/{id}
                               (SELECT ... FOR UPDATE;
                                checks accepted < capacity;
                                PENDING -> ACCEPTED;
                                writes OutboxEvent)

 9. POST /confirmations     10. POST /confirmations
    {happened:YES,             {happened:YES,
     useful:true}               useful:true}
          \                         /
           v                       v
    +-------------------------------------+
    | Both confirmed YES + useful=true    |
    | ==> Counted as 1 USEFUL CONNECTION! |
    +-------------------------------------+
```

---

## 6. Future Evolution: Breaking the Modular Monolith into Microservices (Strangler-Fig Blueprint)

> **🎓 Intern & Architect Guide — Why Start Modular Monolith, How to Split Later:**
> Martin Fowler's *MonolithFirst* principle states that almost all successful microservice architectures started as a well-structured modular monolith. Because our Java packages (`common`, `content`, `listen`, `connection`, `ai`, `outbox`) already enforce **strict table ownership** (Section 1) and **zero cross-package repository calls** (Section 4.B), we can extract microservices incrementally using the **Strangler-Fig Pattern** without rewriting business logic or breaking the mobile/web clients.

### 6.1 Quantitative Triggers: When Should Mastek Split a Module Out?

Do **not** split the monolith during the POC or initial 10,000-user rollout. Extract a module into its own microservice **only** when one or more of these production triggers occur:

| Module Candidate | Quantitative Split Trigger | Why Extracting Helps |
| :--- | :--- | :--- |
| **1. `ai-assist-service`** (`ai/`) | AI prompt latency spikes (>5–10s), external LLM rate-limiting, or introduction of GPU/vector-embedding workloads. | Isolates slow external LLM I/O and token-budget bursts from core feed and connection APIs. |
| **2. `notification-worker`** (`outbox/`) | Push/Email volume exceeds >50,000 events/hour across APNs, FCM, and Microsoft Teams/Email webhooks. | Scales background delivery workers independently of user-facing HTTP pods. |
| **3. `listen-media-service`** (`listen/`) | High-frequency `PUT /api/v1/listen/progress` heartbeats (e.g., 5,000 concurrent listeners syncing every 15s = ~330 writes/sec). | Offloads continuous playback-progress upserts to a dedicated high-write datastore (or Redis -> batch flush) away from editorial transactions. |
| **4. `connection-service`** (`connection/`) | Separate engineering squad owns employee networking/mentoring, requiring independent daily deployments. | Gives the Connection squad an independent CI/CD pipeline and dedicated `invitations` / `connection_requests` database. |
| **5. `content-publishing-service`** (`content/`) | Regional compliance or multi-brand content workflows require isolated deployment lifecycles. | Isolates editorial state machines, revisions, and approval audit trails. |

---

### 6.2 Target Microservices Topology (Future State — Zero Client Changes)

Notice that the **Mobile App, Employee Web, and Publishing Portal still call the exact same `/api/v1/*` routes**. An API Gateway routes each path prefix to the extracted service:

```text
                        CLIENTS (Android, iOS, Employee Web, Publishing Portal)
                                  (Unchanged! Still call /api/v1/*)
                                                  |
                                                  v
+====================================================================================================+
|                         API GATEWAY / BFF  (Spring Cloud Gateway / Kong / Envoy)                   |
|        * Validates Entra ID OIDC JWT once -> forwards signed internal Principal headers            |
|        * Enforces global Rate Limiting & TLS termination                                           |
+=======+========================+========================+========================+=================+
        | /api/v1/feed           | /api/v1/listen/*       | /api/v1/requests/*     | /api/v1/ai/*
        | /api/v1/stories/*      |                        |                        |
        | /api/v1/content/*      |                        |                        |
        v                        v                        v                        v
+--------------------+  +---------------------+  +----------------------+  +---------------------+
| CONTENT &          |  | LISTEN & MEDIA      |  | CONNECTION &         |  | AI ASSIST & BUDGET  |
| PUBLISHING SERVICE |  | SERVICE             |  | FEEDBACK SERVICE     |  | SERVICE             |
| ------------------ |  | ------------------- |  | -------------------- |  | ------------------- |
| * Feed (Filter->   |  | * Radio & Culture   |  | * Invitations        |  | * 720-Action Gate   |
|   Rank->Explain)   |  |   Cast queues       |  | * Locked Accept Flow |  | * Prompt Sanitizer  |
| * Revisions &      |  | * Signed Audio URLs |  |   (SELECT FOR UPDATE)|  | * Template Fallback |
|   Review Workflow  |  | * Playback Progress |  | * Idempotency-Key    |  | * LLM Adapter       |
+---------+----------+  +----+-----------+----+  +----------+-----------+  +----------+----------+
          |                  |           |                  |                         |
          v                  v           v                  v                         v
   +--------------+   +------------+  +------+       +--------------+          +--------------+
   | Content DB   |   | Listen DB  |  | Object|      | Connection DB|          | AI Ledger DB |
   | (PostgreSQL) |   | (Postgres/ |  | Store |      | (PostgreSQL) |          | (PostgreSQL/ |
   |              |   |  Redis)    |  | (CDN) |      |              |          |  Redis)      |
   +------+-------+   +------------+  +------+       +------+-------+          +------+-------+
          |                                                 |                         |
          | Outbox CDC / Publish                            | Outbox CDC / Publish    |
          +------------------------+------------------------+-------------------------+
                                   |
                                   v
+====================================================================================================+
|                     EVENT BROKER  (Apache Kafka / Azure Service Bus / RabbitMQ)                    |
|   Topics: `content.lifecycle.v1`  |  `connection.events.v1`  |  `ai.budget.alerts.v1`              |
+==================================+=================================================================+
                                   |
                                   v
                    +------------------------------+
                    | NOTIFICATION & AUDIT SERVICE |
                    | ---------------------------- |
                    | * APNs / FCM Push Delivery   |
                    | * Email / Teams Digest       |
                    | * Immutable Compliance Sink  |
                    +------------------------------+
```

---

### 6.3 4-Stage Strangler-Fig Extraction Roadmap

Because we never do a risky "Big Bang" rewrite, we extract services in **4 incremental stages**:

```text
 STAGE 0 (Current POC & 10k Rollout)         STAGE 1 (Extract AI & Notification Worker)
 ===================================         ==========================================
 +---------------------------------+         +-----------------------+    +------------------+
 |      Spring Boot Monolith       |         | Spring Boot Core      |--->| ai-assist-service|
 |  [content] [listen] [connection]|   ==>   | [content] [listen]    |    +------------------+
 |  [ai]      [outbox] [common]    |         | [connection] [common] |    +------------------+
 +----------------+----------------+         +-----------+-----------+--->| notification-svc |
                  |                                      |                +------------------+
         Single PostgreSQL 16                   Core PostgreSQL 16           (Event Broker)
                                                         |
                                                         v
 STAGE 3 (Full Microservices if Needed)      STAGE 2 (Extract High-Write Listen Service)
 ======================================      ===========================================
 +---------------+  +-----------------+      +-----------------------+    +------------------+
 | content-svc   |  | connection-svc  |      | Spring Boot Core      |    | listen-media-svc |
 | (Content DB)  |  | (Connection DB) | <==  | [content] [connection]|--->| (Listen DB + CDN)|
 +-------+-------+  +--------+--------+      +-----------------------+    +------------------+
         |                   ^                +------------------+        +------------------+
         +--- Event Bus -----+                | ai-assist-svc    |        | notification-svc |
      (StoryPublishedProjection)              +------------------+        +------------------+
```

---

### 6.4 How We Handle Cross-Service Data When Splitting the Database

When `connection-service` and `content-publishing-service` eventually move to separate PostgreSQL databases in **Stage 3**, two questions arise for engineers:

#### Question 1: How does `connection-service` check if Asha is in a Story's `audience_id` without joining `content_items`?
**Answer — Local Read-Model Projection via Domain Events:**
1. Whenever `content-publishing-service` publishes, updates, or withdraws a story, its `outbox_events` table emits a `CONTENT_PUBLISHED` or `CONTENT_WITHDRAWN` event containing `{ storyId, audienceId, hostId, status, expiresAt }`.
2. `connection-service` subscribes to `content.lifecycle.v1` and maintains a lightweight local table `story_audience_projection (story_id PK, audience_id, status, expires_at)`.
3. When Asha calls `POST /api/v1/requests`, `connection-service` checks `story_audience_projection` locally in `< 1 ms` with zero synchronous HTTP calls to `content-publishing-service`!

```text
 content-publishing-service                  Event Broker               connection-service
 --------------------------                  ------------               ------------------
 [ContentPublishService.publish()]
   |
   |-- TX: UPDATE content_items (PUBLISHED)
   |       INSERT outbox_events ------------> `content.lifecycle.v1` --> [StoryProjectionListener]
                                              { storyId: "story_01",       |
                                                audienceId: "aud_uk",      |-- UPSERT local table:
                                                status: "PUBLISHED" }      |   `story_audience_projection`
                                                                           v
                                                                        [ConnectionService.createRequest()]
                                                                           `-- Checks audience locally!
```

#### Question 2: Does the "Locked Accept Flow" (`SELECT ... FOR UPDATE`) require Distributed Transactions (2PC)?
**Answer — No!**
- Look at Section 5.6: the entire Locked Accept Flow (`invitations` row lock + `connection_requests` state update + `outbox_events` insert) touches **only** tables owned by the `connection/` module (`invitations`, `connection_requests`, `outbox_events`).
- Because those tables move **together** into the `Connection DB`, `SELECT ... FOR UPDATE` remains a fast, single-database local ACID transaction even in a full microservices architecture!

#### Question 3: How does our `outbox_events` table transition to Kafka / Azure Service Bus?
**Answer — Swap the Outbox Dispatcher Implementation Only:**

```text
 Monolith Mode (Stage 0):
   Domain Service --> OutboxRepository.save(event) --> @Scheduled OutboxPoller (in-JVM)

 Microservices Mode (Stages 1-3):
   Domain Service --> OutboxRepository.save(event) --> Debezium CDC / Outbox Relay --> Kafka / Service Bus
   (Domain Service Java code does NOT change at all!)
```
