# CLAUDE.md — Mastek Pulse (AI-DLC Instructions for Claude Code)

> **Welcome, Claude Code!** This repository follows a strict **AI-Driven Development Lifecycle (AI-DLC)** using the **Hub-and-Spoke Pattern** so developers working in Claude Code, OpenAI Codex, and GitHub Copilot stay 100% aligned.

---

## 1. Mandatory Context Reading (Read Before Writing Any Code)
Before planning or editing any file, always read our Single Source of Truth in `docs/aidlc/`:
1. [`docs/aidlc/01-inception-vision-and-scope.md`](docs/aidlc/01-inception-vision-and-scope.md) — Product vision, primary/supporting journeys, strict POC scope vs. deferred features, and the 5 synthetic personas (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`).
2. [`docs/aidlc/02-domain-model-and-contracts.md`](docs/aidlc/02-domain-model-and-contracts.md) — Exact PostgreSQL tables, state machines (`content_items.status` and `connection_requests.state`), the 4-step transactional Accept Flow (`SELECT ... FOR UPDATE`), and REST API endpoints (`401`, `403`, `409`, `422`).
3. [`docs/aidlc/03-architecture-and-guardrails.md`](docs/aidlc/03-architecture-and-guardrails.md) — Monorepo layout (`backend/`, `web/`, `mobile/`), 10,000-user scaling patterns (Java 21 Virtual Threads, Signed URLs, Transactional Outbox), security guardrails, and intern documentation standard.
4. [`docs/aidlc/04-task-backlog.md`](docs/aidlc/04-task-backlog.md) — Atomic task checklist (`TASK-101` through `TASK-601`).
5. [`learn.md`](learn.md) — Our living AI-DLC classroom textbook and Architectural Decision Log (ADRs).

---

## 2. Technology Stack (Locked)
- **Backend (`backend/`):** Java 21 LTS + Spring Boot 3.3+ (Virtual Threads enabled) + PostgreSQL 16 + Flyway + Spring Data JPA.
- **Employee Web & Publishing Portal (`web/`):** React 18 + TypeScript + Vite + Tailwind CSS.
- **Mobile App (`mobile/`):** React Native (Expo) + TypeScript (Android & iOS).

---

## 3. Non-Negotiable AI-DLC Rules
1. **Intern-Level Documentation:** Every `.java`, `.ts`, `.tsx`, and `.sql` file you create or edit **must** begin with a clear 3–5 line intern-friendly header comment explaining *what* the file does, *which user journey* it supports, and *why* key patterns (like pessimistic locking or idempotency keys) are used.
2. **Contract Fidelity:** Never invent new status enums, field names, or endpoints that contradict `docs/aidlc/02-domain-model-and-contracts.md`. If a contract change is needed, update `02-domain-model-and-contracts.md` first and log the decision in `learn.md`.
3. **Critical Business Invariants:**
   - **Audience Filter Before Rank:** Always filter content by permitted `audience_id` before ranking by explicit interests.
   - **Factual Explanations Only:** Show verified reasons (`"WHY THIS? Your interest: team learning"`), never a fake percentage compatibility score.
   - **1-Transaction Locked Accept Flow:** Lock the `invitations` row (`PESSIMISTIC_WRITE`), verify `accepted_count < capacity` and not paused/expired, increment `accepted_count`, set `ACCEPTED`, and write an `OutboxEvent` in the same `@Transactional` method.
   - **Bilateral Usefulness:** Count a connection as useful **only** when both requester and host confirm `happened = 'YES'` and `useful = true`. Missing/skipped feedback stays `UNKNOWN`.
   - **Revision-Bound Approvals:** Creators cannot self-approve (`approved_by != author_id` -> `403`). Any edit to title, body, or audience after approval creates a new revision and resets status to `IN_REVIEW`.
   - **Optional AI with Fallback:** Enforce the 720-action budget gate, label AI suggestions clearly, require explicit human review before sending/publishing, and always provide a deterministic non-AI template fallback.
4. **Update `learn.md` & Backlog:** When completing a task or making an architectural decision, check off the task in `docs/aidlc/04-task-backlog.md` and record key learnings in `learn.md`.
