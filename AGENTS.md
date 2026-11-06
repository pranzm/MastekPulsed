# AGENTS.md — Mastek Pulse (Universal AI Agent & OpenAI Codex Instructions)

> **Welcome, AI Coding Agent (OpenAI Codex, Cursor, Windsurf, Antigravity)!**
> This repository uses the **AI-Driven Development Lifecycle (AI-DLC) Hub-and-Spoke Pattern**. All specifications live in `docs/aidlc/` so every developer and AI tool works from the exact same contracts.

---

## 1. Read the Hub First (`docs/aidlc/`)
Before generating or modifying code, inspect these files:
- **Vision, Personas & Scope:** [`docs/aidlc/01-inception-vision-and-scope.md`](docs/aidlc/01-inception-vision-and-scope.md)
- **Database Schema, State Machines & API Contracts:** [`docs/aidlc/02-domain-model-and-contracts.md`](docs/aidlc/02-domain-model-and-contracts.md)
- **Architecture (10k Scale), Folder Structure & Guardrails:** [`docs/aidlc/03-architecture-and-guardrails.md`](docs/aidlc/03-architecture-and-guardrails.md)
- **Atomic Task Backlog:** [`docs/aidlc/04-task-backlog.md`](docs/aidlc/04-task-backlog.md)
- **AI-DLC Learning Journal & Decision Log:** [`learn.md`](learn.md)

---

## 2. Locked Tech Stack & Monorepo Layout
- `backend/`: **Java 21 + Spring Boot 3.3+ + PostgreSQL 16 + Flyway** (Modular Monolith with Virtual Threads for 10,000+ user concurrency).
- `web/`: **React 18 + TypeScript + Vite + Tailwind CSS** (houses both the **Employee Web Experience** and the **Role-Based Publishing Portal**).
- `mobile/`: **React Native (Expo) + TypeScript** (Android & iOS builds with native audio & local draft storage).

---

## 3. Core Execution Rules for Every Agent
1. **Work in Vertical Slices:** Pick an atomic task from `docs/aidlc/04-task-backlog.md`, implement only what is in scope, verify it with tests or build checks, and mark it `[x]`.
2. **Intern-Level Documentation:** Every source file (`.java`, `.ts`, `.tsx`, `.sql`) must include an intern-friendly top-of-file docblock explaining *what* the file does, *which Mastek Pulse journey* it implements, and *why* specific patterns are used.
3. **Strict Domain Guardrails:**
   - Filter by `audience_id` *before* ranking by explicit interests.
   - Use pessimistic locking (`SELECT ... FOR UPDATE`) when accepting an invitation so `accepted_count` never exceeds `capacity`.
   - Enforce `Idempotency-Key` on `POST /api/v1/requests`.
   - Enforce `approved_by != author_id` (no self-approval) and invalidate approval if content or audience is edited.
   - Always pair optional AI drafting/summarization with a deterministic non-AI template fallback and hard budget cap checks.
4. **Keep `learn.md` Updated:** Whenever a design decision is made or a new pattern is introduced, document the lesson in `learn.md` (separate from `README.md`).
