# GitHub Copilot Instructions — Mastek Pulse (AI-DLC)

You are assisting a developer on **Mastek Pulse** ("Small stories. Meaningful connections."), built using an **AI-Driven Development Lifecycle (AI-DLC)** so that developers across Claude Code, OpenAI Codex, and GitHub Copilot stay strictly aligned.

## 1. Single Source of Truth (`docs/aidlc/`)
Always reference and obey the specifications in `docs/aidlc/`:
- **Vision & Scope:** `docs/aidlc/01-inception-vision-and-scope.md` (Includes the 5 synthetic personas: `usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`, and the exact POC vs. Deferred scope table).
- **Domain Model & API Contracts:** `docs/aidlc/02-domain-model-and-contracts.md` (PostgreSQL 16 schema, editorial state machine, connection request state machine, 1-transaction locked Accept flow, and HTTP `401`/`403`/`409`/`422` rules).
- **Architecture & Coding Guardrails:** `docs/aidlc/03-architecture-and-guardrails.md` (Java 21 + Spring Boot 3.3+ Modular Monolith in `backend/`, React 18 + TypeScript in `web/`, React Native + TypeScript in `mobile/`).
- **Complete Code Flow & ASCII Diagrams:** `docs/aidlc/05-complete-code-flow-and-ascii-diagrams.md` (Master ASCII call graphs, Campfire challenge/recognition ledger flows, snapshot isolation, and 19-table ER diagram).
- **MVP & Scalability Blueprint:** `docs/aidlc/06-mvp-and-scalability-plan.md` (8-week MVP sprint plan, 50 -> 10,000+ user scaling tiers, and plug-and-play feature extension ports).
- **Task Backlog:** `docs/aidlc/04-task-backlog.md`.
- **Learning Journal & ADRs:** `learn.md` (Do not confuse with `README.md`; record all architectural decisions and AI-DLC lessons in `learn.md`).

## 2. Mandatory Coding & Documentation Rules
1. **Intern-Level Documentation:** Every `.java`, `.ts`, `.tsx`, and `.sql` file must start with a clear, educational comment block for interns explaining *what* the file does, *which user journey* it supports, and *why* it uses its design pattern (e.g., Java 21 virtual threads, pessimistic row locking, idempotency keys, or revision-bound approvals).
2. **Enforce All Proposal Invariants:**
   - Filter by `audience_id` before ranking by explicit interests; return factual explanations only (`"WHY THIS? Your interest: team learning"`).
   - In `PATCH /api/v1/requests/{id}`, lock the `invitations` row (`SELECT ... FOR UPDATE`), verify `accepted_count < capacity`, increment `accepted_count`, update state to `ACCEPTED`, and insert an `outbox_events` row in a single `@Transactional` call.
   - Count a connection as useful **only** when both requester and host confirm `happened = 'YES'` and `useful = true`.
   - Never allow self-approval (`approved_by != author_id` -> `403 Forbidden`). Any content or audience edit after approval creates a new revision and returns status to `IN_REVIEW`.
   - All AI drafting/summarization endpoints must enforce the 720-action budget cap and always return a deterministic non-AI `templateFallback`.
