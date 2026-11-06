# 🎓 Mastek Pulse — AI-DLC Learning Journal & Decision Log (`learn.md`)

> **What is this file?**
> Unlike `README.md` (which explains *how to install and run the project*), `learn.md` is our **living classroom textbook and decision log**. Every time we learn an **AI-Driven Development Lifecycle (AI-DLC)** concept, make an architectural decision, or establish a pattern for our team, we record it here so that any developer or intern joining the project can learn the *Why* behind the *What*.

---

## 📚 Table of Contents
1. [Lesson 0: Architectural Selection for 10,000+ Users & AI-DLC](#lesson-0-architectural-selection-for-10000-users--ai-dlc)
2. [Lesson 1: What is AI-DLC & The "Single Source of Truth" Multi-Agent Pattern](#lesson-1-what-is-ai-dlc--the-single-source-of-truth-multi-agent-pattern)
3. [Lesson 2: Contract-First Engineering, Database Invariants & Deterministic Seed Data](#lesson-2-contract-first-engineering-database-invariants--deterministic-seed-data)
4. [Lesson 3: Atomic Vertical Slices, 1-Transaction Pessimistic Locking & The 30-Case AI Trust Gate](#lesson-3-atomic-vertical-slices-1-transaction-pessimistic-locking--the-30-case-ai-trust-gate)
5. [Lesson 4 & 5: Cross-Platform Contract Sharing (Web, Portal & Mobile) & Resilient Demo Engineering](#lesson-4--5-cross-platform-contract-sharing-web-portal--mobile--resilient-demo-engineering)
6. [Decision Log (ADRs)](#decision-log-adrs)

---

## Lesson 0: Architectural Selection for 10,000+ Users & AI-DLC
**Date:** 2026-11-05
**Context:** We asked: *"What will be the best solution for 10,000 people initially if I want to scale it? How about using Java?"*

### Key Takeaways Taught
1. **Why Java 21 + Spring Boot 3.3+ Excels at 10,000+ Employee Scale:**
   - **The "Morning Commute Spike":** With 10,000 employees, 1,500–2,500 users may simultaneously listen to *Mastek Radio* or *Culture Cast*, syncing playback positions every 10–15 seconds and checking their feed.
   - **Virtual Threads (Project Loom):** Enabling `spring.threads.virtual.enabled=true` in Java 21 allows the JVM to handle thousands of concurrent database and I/O calls with minimal memory overhead—giving us reactive-level scale with simple, readable, synchronous code that interns can easily debug.
   - **Strict ACID Transactions & Row Locking:** The Mastek Pulse proposal (Page 15) requires **pessimistic row locking** (`SELECT ... FOR UPDATE`) when accepting an invitation so host capacity (`capacity = 2`) is never exceeded during concurrent clicks, plus a **Transactional Outbox** so state changes and notifications commit atomically. Spring's `@Transactional` + PostgreSQL is the industry gold standard for this.
   - **Offloading Audio Bandwidth via Signed URLs:** To support 10,000 listeners without overloading the Java API, audio bytes never flow through the Spring Boot server. Instead, Spring Boot checks audience permissions and issues a **short-lived Signed URL** to private object storage (MinIO locally / S3 or Azure Blob in production).

2. **Why Java + TypeScript is a Secret Weapon for Multi-Agent AI-DLC:**
   - When different developers use **Claude Code**, **OpenAI Codex**, and **GitHub Copilot**, loosely typed languages allow AI agents to hallucinate property names or break contracts silently.
   - By pairing **Java 21 (`record` DTOs, strict interfaces)** on the backend with **TypeScript (strict mode)** on Web and Mobile, and bridging them via an **OpenAPI 3.1 contract**, the compiler acts as an automated guardrail for every AI agent.

---

## Lesson 1: What is AI-DLC & The "Single Source of Truth" Multi-Agent Pattern
**Date:** 2026-11-05
**Context:** Setting up the project foundation before writing a single line of application code.

### 1. Traditional SDLC vs. AI-DLC (Spec-Driven Development)
In a traditional Hackathon or SDLC, developers jump straight into writing code or chatting with an AI using ad-hoc prompts (*"Build me a React page for stories"*).
- **The Problem ("AI Drift"):** Developer A asks Claude Code to build the backend API. Developer B asks GitHub Copilot to build the React frontend. Because neither AI shares the same context, Claude names a field `remainingPlaces` while Copilot expects `available_capacity`. Integration fails.
- **The AI-DLC Solution:** In AI-DLC, **Markdown Specifications are the Source Code of Intent**. We follow four strict phases:
  1. **Phase 1: Inception & Context Engineering** — Define the domain, scope, personas, and universal AI agent guardrails (`docs/aidlc/01-inception-vision-and-scope.md`).
  2. **Phase 2: Elaboration & Contract-First Specs** — Define the exact State Machines, Database Schema, and API Contracts (`docs/aidlc/02-domain-model-and-contracts.md`) *before* coding.
  3. **Phase 3: Atomic Construction (Vertical Slices)** — Break the work into small, self-contained tasks (`docs/aidlc/04-task-backlog.md`) where an AI agent builds, tests, and verifies one slice at a time.
  4. **Phase 4: Verification & Release Gates** — Automated tests, AI faithfulness gates (30 synthetic cases), and security/row-policy verification.

### 2. How to Make Claude Code, Codex, and GitHub Copilot Work Together
Different AI coding assistants automatically read different "magic files" when opened in a repository:
- **Claude Code** reads `/CLAUDE.md`
- **OpenAI Codex / Cursor / Windsurf / Universal Agents** read `/AGENTS.md`
- **GitHub Copilot** reads `/.github/copilot-instructions.md`

**Best Practice Rule:** Never duplicate long instructions across those three files (or they will get out of sync!). Instead, use the **Hub-and-Spoke Pattern**:
- Put all deep domain knowledge, architecture, and contracts inside `/docs/aidlc/*.md` (The Hub).
- Make `CLAUDE.md`, `AGENTS.md`, and `.github/copilot-instructions.md` lightweight "router" files (The Spokes) that force each tool to read `/docs/aidlc/` before writing code and update `learn.md` when decisions are made.

---

## Lesson 2: Contract-First Engineering, Database Invariants & Deterministic Seed Data
**Date:** 2026-11-05
**Context:** Completing Epic 1 (`TASK-101`, `TASK-102`, `TASK-103`) — OpenAPI 3.1 Contract, Flyway SQL Schema (`V1`), Synthetic Pilot Dataset (`V2`), and Spring Boot 3.3+ Skeleton.

### Key Takeaways Taught
1. **Why Contract-First (`openapi.yaml`) Unblocks 4 Parallel Developers:**
   - By locking [`docs/aidlc/openapi.yaml`](docs/aidlc/openapi.yaml) before writing controllers or React screens, the Frontend Web, Publishing Portal, and Mobile developers can build against exact DTO schemas (`ContentCardDto`, `ConnectionRequestDto`, `AiAssistanceResponse`) while the Backend developer implements the Java services.
2. **Defense-in-Depth Database Constraints (`V1__init_schema.sql`):**
   - Never rely on application code alone to protect data integrity—especially when AI agents write code!
   - We added `CONSTRAINT chk_invitation_capacity CHECK (accepted_count >= 0 AND accepted_count <= capacity)` on `invitations`, `UNIQUE (invitation_id, requester_id)` and `UNIQUE (requester_id, idempotency_key)` on `connection_requests`, and `UNIQUE (request_id, user_id)` on `participant_confirmations`. Even if an AI agent forgets an `if` check in Java, PostgreSQL rejects invalid state transitions automatically.
3. **Why We Separated `content_items` and `content_revisions`:**
   - Proposal Page 11 & 15 state: *"Approvals bind to a revision. Any content or audience edit invalidates approval and returns the new revision to review."*
   - In `V2__seed_pilot_data.sql`, look at `cc_ep_03` (Culture Cast Episode 03/04): `published_version = 2` is live for employees on the Listen tab, while `current_version = 3` is in the Publishing Portal's Review Queue waiting for Reviewer Priya Nair!
4. **Zero-Friction Dual Database Mode (`H2 PostgreSQL Mode` + `Docker PostgreSQL 16`):**
   - In a Hackathon with multiple developers, someone inevitably has Docker issues on their laptop. We configured `application.yml` so `mvn spring-boot:run` works out-of-the-box using H2 in strict `MODE=PostgreSQL` running the exact same Flyway migrations, while `-Dspring.profiles.active=postgres` connects to the live PostgreSQL 16 container in `docker-compose.yml`.

---

## Lesson 3: Atomic Vertical Slices, 1-Transaction Pessimistic Locking & The 30-Case AI Trust Gate
**Date:** 2026-11-05
**Context:** Completing Epic 2 (`TASK-201` through `TASK-206`) and Epic 3 (`TASK-301`) — Core Java 21 Spring Boot Backend Modules (`common`, `content`, `connection`, `listen`, `ai`, `outbox`) and Automated Integration & AI Evaluation Gate Tests.

### Key Takeaways Taught
1. **Why We Build in Vertical Domain Packages (`common`, `content`, `connection`, `listen`, `ai`, `outbox`):**
   - Instead of throwing 50 classes into a giant `controllers/` and `services/` folder, each domain slice owns its entities, repositories, services, and controllers.
   - This matches our Strangler-Fig Microservices Evolution plan (`ADR-007`): when Mastek scales beyond 10,000 users, any package can be extracted into a standalone service without untangling spaghetti imports.
2. **Implementing the "Accept Flow / One Transaction" (`ConnectionService.java`):**
   - Proposal Page 15 requires 4 exact steps when a host clicks **Accept**:
     1. Authenticate the host and re-check audience permission, invitation expiry/pause, and request version (`403` or `409` on mismatch).
     2. Lock the `invitations` row using `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE` in [`InvitationRepository.java`](backend/src/main/java/com/mastek/pulse/connection/InvitationRepository.java)) and verify `acceptedCount < capacity`.
     3. Set `state = ACCEPTED`, increment `acceptedCount`, and insert an `OutboxEventEntity` in the **exact same `@Transactional` boundary**.
     4. Let [`OutboxWorkerService.java`](backend/src/main/java/com/mastek/pulse/outbox/OutboxWorkerService.java) asynchronously deliver notifications and release due `SCHEDULED` publications.
3. **Bilateral Usefulness Confirmation (`computeBilateralOutcome`):**
   - Proposal Pages 8, 12, and 15 mandate that a connection is counted as useful (`USEFUL_CONFIRMED`) **only** when **both** the requester and the host confirm `happened = "YES"` and `useful = true`. If only one confirms (or someone selects `NOT_YET`), the outcome remains `UNKNOWN`—preventing inflated vanity metrics.
4. **Evaluate Before Enabling — The 30 Synthetic AI Cases ([`PulseIntegrationAndAiGatesTest.java`](backend/src/test/java/com/mastek/pulse/PulseIntegrationAndAiGatesTest.java)):**
   - Proposal Page 4 requires: *"Use 30 synthetic cases covering permission, relevance, refusal, and faithfulness. Proposed gate: zero unauthorized disclosures in the test set and at least 90% faithful outputs. Keep templates as a fallback."*
   - In [`AiAssistanceService.java`](backend/src/main/java/com/mastek/pulse/ai/AiAssistanceService.java), we check `requireAudienceAccess` *before* touching the AI adapter, refuse prohibited prompts (employee ranking, appraisal scores, private messages), track usage in `ai_usage_ledger` against the 720-action budget cap (50%, 80%, 100% alerts), and **always** return a deterministic `templateFallback`.

---

## Lesson 4 & 5: Cross-Platform Contract Sharing (Web, Portal & Mobile) & Resilient Demo Engineering
**Date:** 2026-11-05
**Context:** Completing Epic 4 (`TASK-401`, `TASK-402`, `TASK-403`), Epic 5 (`TASK-501`), and Epic 6 (`TASK-601`) — Responsive Employee Web Experience, Role-Based Publishing Portal, and Android/iOS React Native Mobile App.

### Key Takeaways Taught
1. **One Shared TypeScript Contract Across Three Clients ("Three clients. One core." - Proposal Page 14):**
   - [`web/src/api/contracts.ts`](web/src/api/contracts.ts) mirrors [`docs/aidlc/openapi.yaml`](docs/aidlc/openapi.yaml) 1-for-1 (`ContentCardDto`, `ConnectionRequestDto`, `PortalQueueItemDto`, `AiAssistanceResponseDto`).
   - Because Employee Web ([`EmployeeWorkspace.tsx`](web/src/employee/EmployeeWorkspace.tsx)), Publishing Portal ([`PublishingPortalWorkspace.tsx`](web/src/portal/PublishingPortalWorkspace.tsx)), and React Native Mobile ([`mobile/App.tsx`](mobile/App.tsx)) all consume the exact same contract, zero field-name mismatches can occur between developers using Claude Code, Codex, or GitHub Copilot.
2. **Dual-Mode API Client for Zero-Downtime Hackathon Demos ([`web/src/api/apiClient.ts`](web/src/api/apiClient.ts)):**
   - What happens during a live Hackathon demo if a developer's laptop doesn't have Java 21 or Docker running?
   - In [`apiClient.ts`](web/src/api/apiClient.ts), every method attempts to call the live Spring Boot `/api/v1/*` endpoint first, and if offline, seamlessly falls back to a stateful, contract-faithful in-browser engine pre-loaded with `V2__seed_pilot_data.sql` and enforcing all proposal invariants (`403` self-approval block, `409` full capacity lock, bilateral usefulness check, edit invalidates approval, and the 720-action AI budget cap).
3. **Native Mobile Audio vs. Desktop Web Resume ([`NativeAudioService.ts`](mobile/src/audio/NativeAudioService.ts) & [`App.tsx`](web/src/App.tsx)):**
   - Proposal Page 9, 10, and 15 require background/lock-screen audio controls on mobile (`expo-av` with `staysActiveInBackground: true`) and a persistent bottom audio bar on Employee Web (`Paused: Culture Cast / 02:10 of 08:00 · Play · Transcript · Switch Queue`) that syncs `positionSeconds` to `/api/v1/listen/progress` so employees can start listening on their commute and resume at their desk.

---

## Decision Log (ADRs)

| ID | Date | Decision | Rationale |
| :--- | :--- | :--- | :--- |
| **ADR-001** | 2026-11-05 | **Backend Stack:** Java 21 LTS + Spring Boot 3.3+ + PostgreSQL 16 (Modular Monolith) | Supports 10,000+ users via Virtual Threads, enforces strict compile-time safety for AI agents, and cleanly implements PostgreSQL Row-Level Security (RLS), pessimistic locking (`FOR UPDATE`), and the Transactional Outbox pattern from the proposal. |
| **ADR-002** | 2026-11-05 | **Frontend & Mobile Stack:** React 18 + TypeScript + Vite (Employee Web & Publishing Portal) and React Native + Expo + TypeScript (Android & iOS) | Aligns with Page 14 of the Mastek Pulse proposal. Enables sharing TypeScript OpenAPI types across Web, Portal, and Mobile while delivering native lock-screen/background audio on mobile. |
| **ADR-003** | 2026-11-05 | **Multi-Agent AI-DLC Structure:** Hub-and-Spoke Markdown specs (`docs/aidlc/`) linked from `CLAUDE.md`, `AGENTS.md`, and `.github/copilot-instructions.md` | Ensures developers using Claude Code, Codex, or GitHub Copilot never suffer from "AI drift" and always adhere to the same data contracts and intern-friendly documentation standards. |
| **ADR-004** | 2026-11-05 | **Continuous Learning Log (`learn.md`):** Maintain `learn.md` separately from `README.md` | Captures all AI-DLC lessons, architectural trade-offs, and team decisions as an interactive textbook for developers and interns. |
| **ADR-005** | 2026-11-05 | **Dual-Mode PostgreSQL Flyway Setup:** Support both zero-install `MODE=PostgreSQL` and Docker `postgres:16-alpine` | Eliminates Hackathon onboarding friction for developers and AI agents while keeping 100% SQL migration parity with production PostgreSQL 16. |
| **ADR-006** | 2026-11-05 | **ASCII Architecture & Code Flow Diagrams:** `03-architecture-and-guardrails.md` uses plain-text ASCII diagrams (no Mermaid) plus per-endpoint code flows (Section 5) | ASCII renders identically in every IDE, terminal, `git diff`, and AI agent context window without a renderer. Code flows name the owning `Controller -> Service -> Repository` method, the `@Transactional` boundary, and each guardrail's HTTP status, so agents implement the backlog without guessing. |
| **ADR-007** | 2026-11-05 | **Microservices Evolution Blueprint (Strangler-Fig):** Strict package-level table ownership + service façades + transactional outbox in `03-architecture-and-guardrails.md` (Section 6) | Keeps Day-1 POC velocity high inside a single Spring Boot deployment while guaranteeing any module (`ai`, `outbox`, `listen`, `connection`, `content`) can be extracted into an independent microservice and database in 4 incremental stages with zero client `/api/v1/*` changes. |
