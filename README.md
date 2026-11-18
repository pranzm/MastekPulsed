# Mastek Campfire — November engagement POC

**Next development agent:** [Start here](docs/campfire/AGENT-START-HERE.md) · [17 November plan](docs/campfire/DEVELOPMENT-PLAN-2026-11-17.md)

Start with [the demo guide](docs/campfire/DEMO-GUIDE.md) and [implementation stories](docs/campfire/IMPLEMENTATION-STORIES.md). The app opens a complete browser-local challenge/review/publish/recognition journey. Existing workflows remain accessible. Saathi cloud/voice adapters are absent in this baseline.

# Mastek Campfire — Small Stories. Meaningful Connections.

> **Android + iOS + Employee Web + Role-Based Publishing Portal | One Connected Core**
> Built using an **AI-Driven Development Lifecycle (AI-DLC)** for multi-agent collaboration across **Claude Code**, **OpenAI Codex**, and **GitHub Copilot**.

---

## 📖 Quick Links
- **🎓 Learning AI-DLC?** Open [`learn.md`](learn.md) — our interactive AI-DLC classroom textbook and Architectural Decision Log (ADRs).
- **🧭 AI-DLC Specifications (The Hub):**
  - [Phase 1: Inception, Vision, Personas & Scope (`docs/aidlc/01-inception-vision-and-scope.md`)](docs/aidlc/01-inception-vision-and-scope.md)
  - [Phase 2A: Domain Model, State Machines & API Contracts (`docs/aidlc/02-domain-model-and-contracts.md`)](docs/aidlc/02-domain-model-and-contracts.md)
  - [Phase 2B: Architecture (10k+ Users), Folder Layout & Guardrails (`docs/aidlc/03-architecture-and-guardrails.md`)](docs/aidlc/03-architecture-and-guardrails.md)
  - [Phase 3: Atomic Task Backlog & Parallel Dev Plan (`docs/aidlc/04-task-backlog.md`)](docs/aidlc/04-task-backlog.md)
- **🤖 Multi-Agent Entry Files (The Spokes):**
  - [`CLAUDE.md`](CLAUDE.md) (for Claude Code)
  - [`AGENTS.md`](AGENTS.md) (for OpenAI Codex, Cursor, Windsurf, Antigravity)
  - [`.github/copilot-instructions.md`](.github/copilot-instructions.md) (for GitHub Copilot)

---

## 🏗️ Technology Stack (Designed for 10,000+ Employees)
| Layer | Stack | Purpose |
| :--- | :--- | :--- |
| **Backend Core (`backend/`)** | **Java 21 LTS + Spring Boot 3.3+ + Flyway** | Modular monolith with Virtual Threads, Pessimistic Locking (`FOR UPDATE`), and Transactional Outbox. |
| **Database** | **PostgreSQL 16** | Stores versioned revisions, audience permissions, invitations, requests, bilateral confirmations, and playback progress. |
| **Web & Portal (`web/`)** | **React 18 + TypeScript + Vite + Tailwind CSS** | Responsive Employee Web Experience + Scoped Role-Based Publishing Portal. |
| **Mobile (`mobile/`)** | **React Native (Expo) + TypeScript** | Android and iOS builds with native audio controls and local draft storage. |

---

## 🚀 Demonstration Journeys Included in Scope
1. **Primary Journey (Discover -> Connect -> Confirm):**
   - **Asha** (Developer, India) discovers **Daniel's** story (*"A retrospective everyone can contribute to"*) with a factual recommendation reason (`"WHY THIS? Your interest: team learning"`).
   - Asha composes an invitation (with optional AI drafting or template fallback) and sends it idempotently.
   - **Daniel** (Delivery Lead, UK) accepts the invitation from his Host Inbox (atomically locking capacity `2 -> 1`).
   - Both Asha and Daniel complete the **Private Check-in** (`Happened = Yes`, `Useful = Yes`), recording a verified Useful Connection.
2. **Supporting Journey A (Mastek Radio & Culture Cast):**
   - Listen to the curated **Mastek Radio** commute queue (3 items, 12 mins) and 3 **Culture Cast** episodes with chapters, transcripts, and seamless **cross-device playback resume** between Web and Mobile.
3. **Supporting Journey B (Controlled Revision-Based Publishing):**
   - Submit a contribution -> **Priya** (`REVIEWER`) reviews the exact revision (no self-approval) -> **Marcus** (`PUBLISHER`) releases or schedules it to the intended audience. Any edit after approval automatically invalidates approval and creates a new revision in `IN_REVIEW`.
4. **Supporting Journey C (Optional AI with Fallback & Budget Gate):**
   - Use optional AI drafting or summarization governed by the 720-action budget cap (50%, 80%, 100% alerts), review the labeled output alongside a deterministic non-AI template fallback, and explicitly choose the next action.

## Make me Productive — 17 November 2026

Open **Make me Productive** from the browser demo or Employee Web navigation. Direct preview: `/productivity/index.html?mode=demo`. Includes editable habits, customizable focus/break timers, getting-started hints, box breathing and mindfulness. Fictional data only; live personal entry is gated pending verified employee authentication. See [development plan](docs/campfire/MAKE-ME-PRODUCTIVE-PLAN.md) and [updated proposal](mastek-campfire-proposal-productivity.pdf). Native implementation is planned.
