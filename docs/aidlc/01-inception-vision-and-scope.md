# Mastek Pulse — AI-DLC Phase 1: Inception, Vision, and Scope

> **🎓 Intern & AI Agent Guide:**
> Welcome to **Mastek Pulse**! In an **AI-Driven Development Lifecycle (AI-DLC)**, this document is the **Inception Blueprint**. Before writing any Java, React, or React Native code, every human developer and AI coding agent (Claude Code, Codex, GitHub Copilot) must read this file to understand *what* we are building, *who* we are building it for, and *what is strictly in or out of scope*.

---

## 1. Executive Summary & Proposition
**Mastek Pulse** ("Small stories. Meaningful connections.") is a connected employee experience across **Android, iOS, Employee Web, and a Role-Based Publishing Portal**, backed by a unified **Java 21 + Spring Boot 3 + PostgreSQL** core.

### The Problem We Are Solving (Hypothesis)
Employees across geographies (e.g., India, UK, US) and functions (Delivery, Engineering, HR, Sales) often want to learn from or collaborate with colleagues beyond their immediate team, but they lack a **comfortable, specific opening** to reach out. Traditional enterprise channels (like Viva Engage) support broad communities, whereas Mastek Pulse tests a **narrower, consent-first, capacity-capped invitation workflow** paired with bite-sized updates and commute-friendly audio.

### The Six Connected Experiences
1. **Mastek Radio (01 / Commute Listening):** A curated listening queue of approved company updates, stories, and innovation highlights with background playback and cross-device resume.
2. **Culture Cast (02 / On-Demand Audio):** Browse colleague and culture conversations by episode, topic, and duration, complete with chapters, transcripts, and related stories.
3. **Bite-Sized Updates (03 / Stay Informed):** Short text, audio, and captioned video updates with an official source, date, and link to more detail, tailored to explicit employee interests.
4. **Employee Voice / Moments (04 / Share):** Share achievements, lessons, ideas, and innovations. Creators review exact content and audience before submitting a versioned revision for approval.
5. **Colleague Connections (05 / Across Boundaries):** Voluntary, capacity-limited invitations attached to stories (*"Ask me about this — 15 min conversation / 2 places available"*). Both participants privately confirm whether the interaction happened and was useful.
6. **AI-Assisted Engagement (06 / Less Effort):** Grounded, factual recommendation explanations, source-linked update summaries, and optional contribution/invitation drafting with a non-AI template fallback.

---

## 2. Core User Journeys (Hackathon POC & Pilot Demonstration)

### 🌟 Primary Demonstration Journey: "Discover -> Connect -> Confirm"
```mermaid
sequenceDiagram
    actor Asha as Asha (Developer, India)
    participant App as Mastek Pulse (Mobile/Web)
    participant API as Spring Boot Core API
    actor Daniel as Daniel (Delivery Host, UK)

    Asha->>App: 1. Opens "Your Pulse" feed (Interest: "team learning")
    App->>API: GET /v1/feed (filters by Audience first, ranks by explicit Interest)
    API-->>App: Returns Daniel's Story + Factual Reason ("Your interest: team learning; 2 places available")
    Asha->>App: 2. Reads Story ("A retrospective everyone can contribute to") & clicks "Write an invitation"
    Asha->>App: 3. Uses optional AI Draft (or template fallback), edits message, clicks "Send invitation"
    App->>API: POST /v1/requests (with Idempotency-Key)
    API-->>App: Status: PENDING
    Daniel->>App: 4. Opens "Connections > Received" inbox & clicks "Accept"
    App->>API: PATCH /v1/requests/{id} (state = ACCEPTED)
    Note over API: Locks invitation row (FOR UPDATE), checks capacity > 0,<br/>increments accepted_count, writes Outbox event in 1 transaction
    API-->>Daniel: Status: ACCEPTED ("Agree a time using your existing workplace tools")
    Asha->>App: 5. Completes Private Check-in (Happened = Yes, Useful = Yes)
    Daniel->>App: 6. Completes Private Check-in (Happened = Yes, Useful = Yes)
    Note over API: Useful connection is counted ONLY when BOTH confirm happened=true & useful=true!
```

### 🎧 Supporting Journey A: Commute & Desk Listening (Mastek Radio & Culture Cast)
1. Employee opens **Listen** tab on mobile during their commute or on the Employee Web app at their desk.
2. Selects **Mastek Radio** (curated queue of 3 items, 12 mins total) or **Culture Cast** (3 on-demand episodes with chapters).
3. Plays audio via short-lived signed URLs, reads synchronized/accessible **transcripts**, and pauses at `04:12`.
4. Switches from Desktop Web to Mobile App (or vice versa) and **resumes playback from the last server-saved listening position** (`04:12`).

### 📝 Supporting Journey B: Controlled Revision-Based Publishing
1. **Creator** drafts a contribution (*"A better retrospective"*), chooses audience (*"Pilot colleagues"*), attaches an optional invitation (*"Ask me about this"*, capacity: `2`, expires: `7 days`), and submits **Revision 1** for review.
2. **Reviewer** opens the **Publishing Portal**, inspects the exact revision (`Revision 1`), checks audience/transcript/media readiness, and clicks **Approve revision** (Note: Creators cannot self-approve their own submissions!).
3. **Publisher** chooses immediate release or scheduled release (with timezone and expiry). If any edit is made to the title, body, or audience after approval, the approval is automatically invalidated and a new revision (`Revision 2`) returns to `IN_REVIEW`.

### ✨ Supporting Journey C: Human-in-the-Loop AI Assistance with Fallback
1. When drafting an invitation or story, or reading an update, the user clicks **"Suggest a draft with AI"** (or **"Summarize"**).
2. The system checks the AI budget cap and permissions, sends only permitted text to the AI adapter, and displays the clearly labeled AI suggestion alongside a **non-AI template fallback**.
3. The user **must review and explicitly choose** the next action (edit, send, or discard). AI never auto-publishes or auto-sends.

---

## 3. Scope Boundaries (Strict Guardrails for Developers & AI Agents)

> **⚠️ Rule for AI Agents (Claude Code, Codex, Copilot):**
> Do **not** build features listed in the **Defer** column. Keep the codebase lean, focused, and faithful to the POC & 10k-scale modular monolith design.

| Include in the POC | Defer (Do NOT Build in POC) |
| :--- | :--- |
| **Responsive employee web experience** and **role-based publishing portal** (React + TypeScript) | Live audio/video broadcasting |
| **Android and iOS builds** (React Native + TypeScript) demonstrating core journeys and native audio controls | Public app-store launch |
| **Synthetic profiles, explicit interests, and audience groups** (seeded via Flyway migrations) | Real employee data or corporate production Entra ID integration until pilot readiness approval |
| **Seeded Pilot Dataset:** 1 Radio queue, 3 Culture Cast episodes, 6 bite-sized updates, and 12 synthetic contributions | Extensive offline media downloads (keep drafts local; stream audio via signed URLs) |
| **Rules-based recommendations** with factual explanations grounded in real fields (`"WHY THIS? Your interest: team learning"`) | Semantic/vector recommendations unless time permits (never invent a fake "compatibility score"!) |
| **Invitations workflow:** capacity, expiry, pause, acceptance, decline, cancellation, and bilateral private feedback | Calendar integration and automatic meeting booking (users agree a time using existing workplace tools) |
| **Revision-based editorial workflow:** `Draft > In review > Approved > Scheduled / Published > Withdrawn` | Advanced editorial analytics or employee popularity/appraisal rankings (strictly prohibited!) |
| **One optional AI capability** (drafting/summarization) with a **deterministic non-AI template fallback** | Generated AI voice narration, automated translation, and unreviewed AI actions |

---

## 4. Synthetic Personas & Scoped Roles (For Testing & Demo)

Every developer and AI agent should use these canonical synthetic personas in seed data and automated tests:

| Persona ID | Name | Role / Function | Region | App Roles | Key Demo Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `usr_asha` | **Asha Patel** | Developer / Engineering | India | `EMPLOYEE`, `CREATOR` | Primary requester; interests: `team learning`, `innovation`, `culture`. |
| `usr_daniel` | **Daniel Wright** | Delivery Lead / Delivery | UK | `EMPLOYEE`, `CREATOR` | Primary host; author of *"A retrospective everyone can contribute to"* (Capacity: 2). |
| `usr_priya` | **Priya Nair** | Internal Comms / HR | India | `EMPLOYEE`, `REVIEWER` | Reviews submitted revisions in the Publishing Portal (`no self-approval`). |
| `usr_marcus` | **Marcus Vance** | Program Director / Ops | US | `EMPLOYEE`, `PUBLISHER`, `MODERATOR` | Publishes/schedules approved revisions, withdraws content, handles reports. |
| `usr_elena` | **Elena Rostova** | Platform Engineer / IT | UK | `ADMIN` | Manages role and audience configuration (Note: `ADMIN` has **no** automatic editorial approval right!). |

---

## 5. Pilot Success Metrics & Trust Gates (Page 12 of Proposal)
Why do we track specific fields in our database? Because the 4-week pilot measures 5 strict gates:
1. **Useful Connections (Target: ≥ 20% in weeks 3–4):** Weekly active people with at least one outside-team interaction where **both** parties confirm `happened = true` and `useful = true` ÷ weekly active people. *(Skipped or missing feedback stays `UNKNOWN`, never assumed positive!)*
2. **Acceptance Rate (Target: ≥ 50%):** Accepted requests ÷ requests with an accept/decline decision.
3. **Return Participation (Target: ≥ 40%):** Week-1 active users active again in Week 4.
4. **Host Workload (Target: Median ≤ 45 mins/week):** Self-reported weekly time spent publishing, responding, and participating.
5. **Trust & Usability (Target: Zero material incidents):** Zero unauthorized disclosures, no critical WCAG accessibility barriers, and AI spend strictly within the hard budget cap (720 AI actions illustrative cap, alerting at 50%, 80%, 100%).
