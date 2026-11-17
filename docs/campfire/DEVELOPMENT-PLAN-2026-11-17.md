# Mastek Campfire — development plan for 17 November 2026

![Campfire technology logo](../../web/public/brand/campfire-tech-logo.png)

**Owner:** the next development agent. **Timezone:** Asia/Kolkata. Timeboxes below are elapsed focused work, not promises or fixed appointments.

**Today's objective:** move the engagement journey toward one authoritative Java/PostgreSQL workflow. Preserve the usable browser demo while removing misleading live fallbacks and closing access gaps. The minimum finished slice is **submit → review → publish → exactly one persisted +20 reward → personal progress**. Appreciation, full leaderboard and badges are the next slice if the foundations pass with enough time remaining.

Do not spend today redesigning the website, changing frameworks, splitting microservices, implementing corporate SSO, or inventing the missing voice service. The website is already published and the new logo is approved.

## 1. Establish the correct checkout before editing

Reviewed working checkout:
`/Users/pranjalmedhi/Documents/ChatGPT/Mastek Pulse/work/campfire-poc`

Original source inspected read-only:
`/Users/pranjalmedhi/Desktop/Workspace/Mastek-Pulse`

Both start from commit `cb28c16`. The working checkout additionally contains the Campfire browser demonstration, revised documents and logo. It has intentional uncommitted changes. Do not reset, clean, overwrite or discard them. Do not assume the original directory already contains these changes.

Start in the working checkout, or use the updated `Mastek-Campfire-POC.zip` if running on another machine. If your own checkout includes the newer voice branch, inspect its diff and reconcile these additions there; do not replace newer files with this older baseline. The included patch is against the inspected original baseline, not arbitrary later commits. Run `git apply --check` before applying it, and do not apply it to a checkout that already has the changes.

Read in this order:
1. `AGENTS.md`, this plan and `docs/campfire/AGENT-START-HERE.md`.
2. `docs/aidlc/01-inception-vision-and-scope.md` and its 17 November amendment.
3. `docs/aidlc/02-domain-model-and-contracts.md`, `docs/campfire/NEW-CONTRACTS.md`, `docs/aidlc/openapi.yaml`.
4. `docs/aidlc/03-architecture-and-guardrails.md`, `docs/aidlc/04-task-backlog.md`.
5. `docs/campfire/IMPLEMENTATION-STORIES.md`, `DEMO-GUIDE.md`, `VALIDATION.md` and `learn.md`.

The dated scope amendment authorizes optional community rankings and Saathi narration. Old blanket exclusions do not supersede it. Appraisal/productivity rankings remain excluded. A checked box in the old backlog is a historical claim, not passing evidence.

## 2. Findings confirmed from current source

| Finding | Evidence / impact | Today's action |
|---|---|---|
| Two unrelated browser state stores | `web/public/campfire/engine.mjs` vs `web/src/api/apiClient.ts`; the new demo iframe does not call Java publishing | Keep an explicit browser demo; build a separate live adapter rather than synchronising two local stores |
| Live failures can look successful | `apiClient.ts:getPortalQueue` falls through after non-OK response; `editPortalItemAndInvalidateApproval` can mutate local state after failure; several methods inspect an error-message prefix | Typed HTTP errors; explicit demo/live selection; no fallback after a live failure |
| Portal reads are unscoped | `ContentController:getPortalReviewQueue` authenticates but passes no caller to `listPortalQueue`; audit reads return latest events globally | Role + audience filtering in the service; scope audit events as well |
| Fixing portal security can break all employee loading | `App.tsx:loadAllData` unconditionally includes portal queue/audit in one `Promise.all` | Load employee data first; fetch editorial data only for permitted roles and workspace; show explicit load/error states |
| Unpublished detail reads | `FeedService:getStoryDetail` falls back to current revision when no published version exists | Published-only employee read; distinct owner/editor preview policy; withdrawn content denied |
| Submission ownership is too broad | `PublishingWorkflowService:submitForReview` uses `!author && !CREATOR` | Require author ownership and appropriate creator role; validate allowed state |
| Review revision can be omitted | `reviewRevision` defaults expected revision to current; state check incomplete | Require exact revision; enforce IN_REVIEW; protect concurrent review/edit/publish |
| Documents overstate identity implementation | `IdentityService.authenticate` defaults missing header to Asha; `pom.xml` has no Spring Security resource-server dependency | Label synthetic identity honestly; gate it behind an explicit demo profile; reject missing/invalid identities in live API mode; do not claim SSO |
| New contracts are not implemented | `NEW-CONTRACTS.md` is a proposal; OpenAPI still has Pulse branding and no challenge/recognition operations | Reconcile DTOs and add operations before writing the live adapter |
| Fixture identities differ | Browser uses `asha`, `daniel`, etc.; Java uses `usr_asha`, `usr_daniel`; browser has Rohan, Java seeds five personas | Live adapter uses server IDs. Seed a sixth permitted persona for the five-distinct-appreciator badge; never map identities by display name |
| Reported voice service is missing | No `voice-service/`, `SAARTHI-DEMO.md` or Azure adapter suite in either inspected checkout | Obtain/reconcile that branch; continue independent backend work meanwhile |

These are static source findings, not claims of newly executed backend exploit tests. Existing browser tests cover demo rules only.

## 3. Work in this order

### D0 — Baseline and runnable environment · 30–45 minutes · required

Commands from repository root:
```bash
git status --short
git log -1 --oneline
java -version
mvn -version
docker version
npm --prefix web run test:campfire
npm --prefix web run build
```

Java 21.0.6 is available on the reviewed machine. `mvn` is not installed; there is no checked-in Maven wrapper. Docker executable exists, but daemon availability has not been verified. Resolve Maven through the agent environment's supported package route or add a standard Maven wrapper; do not invent successful Java results. Dependencies are already present in the reviewed web checkout; use `npm ci` when working from the zip. If the default npm cache is unwritable, use a task-local cache instead of changing ownership of the user's home directory.

Run baseline `mvn -f backend/pom.xml test` once tooling is ready. The default application database is in-memory H2 with PostgreSQL compatibility mode. That is not evidence of PostgreSQL SQL/locking compatibility. New persistence and concurrency work must also run against PostgreSQL 16. Use `docker compose up -d postgres` only after checking for an existing instance; do not delete volumes or reset an existing database.

Deliverable: record actual commands, pass/fail, environment blockers and baseline commit in `docs/campfire/DAILY-RESULTS-2026-11-17.md`. No secrets in logs. If tooling cannot be resolved, proceed with contracts, transport fixes and frontend work; report Java verification blocked.

### D1 — Explicit transports and role-aware loading · 60–90 minutes · required

Stories: CF-003, part of CF-011.
Targets: `web/src/api/apiClient.ts`, `web/src/App.tsx`, `web/src/api/contracts.ts`; add focused `api/httpClient.ts`, `api/demoAdapter.ts`, `api/liveAdapter.ts` if that reduces duplication.

Instructions:
- Choose mode explicitly through a documented configuration such as `VITE_APP_MODE=demo|live`. Keep public/static demo on `demo`. Unknown mode is a configuration error. In live mode, only API responses can establish success.
- Represent errors with `status`, `code`, `message`; never check whether a message starts with a digit. Handle non-JSON error responses and network failures without simulating a write.
- Preserve existing request signatures until callers are migrated. Avoid a wholesale rewrite of unrelated connection/audio logic.
- Fetch current user first. Derive allowed workspace actions from server roles. Employee mode must not require portal queue, audit, metrics or budget endpoints to load successfully.
- Do not fetch the old API in the background when only the standalone browser demo is being shown. Handle rejected promises and show retry states; no indefinite loading spinner.
- Clear persona-scoped data on persona changes. Do not carry a cached restricted story into the next user's view.

Acceptance: live 401/403/409/500 and network failures remain visible failures and leave local demo state untouched; demo works without backend. Ordinary employee loading succeeds when portal endpoints return 403. Mode is visible and accurate.

### D2 — Shared visibility and editorial correctness · 90–150 minutes · required

Stories: CF-010 and part of CF-004.
Targets: `common/IdentityService.java`, `content/ContentController.java`, `content/FeedService.java`, `content/PublishingWorkflowService.java`, content entities/repositories, `ai/AiAssistanceService.java`, focused backend tests.

Instructions:
- Centralize publication/ownership/role/audience decisions. Pass caller into portal service queries; limit reviewer/publisher access by audience. ADMIN alone is not editorial approval authority.
- Keep employee detail/AI context on the published snapshot. Drafts require owner/scoped-editor preview. Withdrawal must remove employee visibility and future AI context.
- Separate draft audience from the immutable published audience/snapshot. An audience edit must not broaden access to the previously published text. Preserve the prior published revision while a replacement is under review.
- Require creator ownership for submit, valid source states, an explicit revision for review/publication, no self-approval and atomic concurrency control. Use optimistic versioning or consistent locks; every rejected transition leaves state unchanged.
- Synthetic identity is for controlled demos only. Do not build partial SSO or call the current header authentication production-ready.

Acceptance: employee portal access denied; wrong-audience reviewer denied; another creator cannot submit an author's draft; unpublished/withdrawn direct reads denied; stale review/publish fails; missing revision is invalid; editing audience cannot disclose the old publication. Ensure D1 keeps employee UI usable under these denials.

**Gate:** if these checks fail, do not expose the live recognition path or start cloud voice integration. Finish these corrections first.

### D3 — Freeze contracts and add persistence · 45–75 minutes · required for live slice

Stories: CF-002, CF-005.
Targets: `docs/aidlc/openapi.yaml`, `02-domain-model-and-contracts.md`, `docs/campfire/NEW-CONTRACTS.md`, `web/src/api/contracts.ts`, new Java DTOs, next unused Flyway migration.

Instructions:
- Keep Java 21 / Spring Boot / PostgreSQL, React and `/api/v1`; preserve `com.mastek.pulse`, existing package IDs and `X-Pulse-User-Id` compatibility in synthetic demo mode.
- V1/V2 already exist. Add V3 only if still unused after reconciliation; never rewrite applied migrations.
- Define active challenge, challenge entry tied to existing `content_id`, recognition event/ledger, weekly projection and opt-out preference. Use the current draft/review/publish workflow; do not create a second content state machine.
- One active challenge for `aud_pilot`; Monday 00:00 UTC scoring boundary; return explicit start/end/timezone. Use an injected Clock for deterministic tests rather than fixed expired dates.
- Use a unique challenge/user entry. Award keys must be unique across retries. Week, audience and source event/revision are server-owned. No endpoint accepts a client point total.
- Resolve uniqueness, reversal behavior, idempotency response, error codes and DTO property names in the contract before implementation. Proposed endpoints are in NEW-CONTRACTS.md.

Acceptance: migrations run on a clean PostgreSQL database and upgrade V2; OpenAPI and Java/TS DTOs agree; fixtures use canonical server IDs and explicit audiences. No unrelated employee data is required.

### D4 — Finish one persisted vertical slice · 90–120 minutes · today's minimum feature

Stories: CF-002/004/005, first part of CF-007.
Targets: new `challenge/` and `recognition/` Java modules; `PublishingWorkflowService.java`; live frontend challenge/progress view.

Instructions:
1. Implement active challenge read and authorized submission tied to the existing revision workflow.
2. Reviewer approves exact revision. Publisher releases it. Award **+20 only after actual publication**, at most once per user/challenge/week.
3. For today's modular monolith, a transaction-safe recognition service called from the publication transaction is acceptable and gives immediate consistency. If using the outbox instead, show pending recognition until processed and make the consumer idempotent. Document one choice; do not use both award paths.
4. Add private progress read. Reload the page and restart the API using PostgreSQL: content and points remain.
5. Concurrent/replayed publication must not double-award. Publication failure rolls back the award. Withdrawal creates a compensating reversal; re-publication cannot exceed the contribution cap.
6. Reuse the existing portal for review/publication. Browser demo remains a fallback route labelled as simulation; live mode must use the server rather than mixing its outcomes into local state.

Acceptance demonstration: Asha submits → Priya reviews → Marcus publishes → Asha sees one persisted +20 award. Before publish she has zero. Retry adds zero. API restart does not erase state. Withdraw removes/reverses the reward. Tests prove denied access and stale revision cannot create points.

**Checkpoint:** if D0–D4 consume the day, stop feature expansion, verify and hand off. This is a complete useful delivery. Do not claim the full backend gamification loop is complete.

### D5 — Appreciation, ranking and badges · remaining time / next day

Stories: CF-006/007. Start only after D4 passes.
- Implement idempotent appreciation on authorized published stories, no self-appreciation.
- Giving: +5 for first three eligible stories/week. Receiving: +5 for first five distinct colleagues/week. Total maximum 60 including contribution.
- Lock a per-user/week scope or use an equivalent atomic cap strategy. Concurrent requests for the final allowance must produce only one reward.
- Seed sixth persona with correct permissions. Do not elevate ADMIN to editor for demo convenience.
- Compute top five from opted-in participants, filtered to permitted audience activity. Tied scores share rank; use stable alphabetical ordering within a tie. Keep personal progress when opted out.
- Add First Spark, Connection Maker (three author teams) and Community Favourite (five distinct appreciators). Snapshot team membership at event time; do not make past badges change because somebody edits a profile.
- Recalculate/compensate withdrawal effects without deleting ledger evidence. Define deterministic event order when earlier actions free a capped slot.
- Extend real UI only after APIs pass; no private feedback, clicks, logins or listening points.

Acceptance: browser and API show the same totals; duplicate/replayed/concurrent calls cannot inflate them; opting out hides the row without losing progress; unauthorized story events cannot be inferred from ranking; week rollover is deterministic.

### D6 — Saathi readiness only · optional 30-minute reconciliation

Story: CF-008; CF-009 remains separate policy/enforcement work.
The handover described **Saarthi**, Whisper, Piper, Foundry and Content Safety. Those files are absent here. If supplied today, identify its branch/commit, read its setup/security documents and reconcile display naming to **Saathi**. Reuse adapters; do not develop a second voice stack.

No credentials should be embedded in JavaScript, Git, logs or documentation. Keep text fallback usable. Voice/source queries must apply D2 authorization before selecting content or calling Azure. Saathi cannot approve/publish/award points. The current browser read-aloud is browser speech synthesis, not verified Piper. Tests of mocked adapters are not live mic/Azure validation. Do not block D1–D4 while waiting for this branch.

## 4. Verification and end-of-day handoff · reserve 45–60 minutes

Run appropriate checks after changes, not repeatedly without reason:
```bash
npm --prefix web run test:campfire
npm --prefix web run build
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -Dspring.profiles.active=postgres test
```

The PostgreSQL test command needs a dedicated synthetic test database configured with `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`; never point destructive fixtures at a personal/shared database. Inspect test setup first: the existing tests use shared seeded IDs. If adding Testcontainers, document it and use the corresponding runner instead. H2 passes do not substitute for PostgreSQL concurrency evidence.

Required negative cases: portal 403; wrong audience; draft/withdrawn read; wrong author submit; missing/stale revision; self-approval; publication retry/concurrency; live API failure without local mutation; award rollback/withdrawal. Add the appreciation/cap/week/opt-out tests only when D5 is implemented.

Record in `DAILY-RESULTS-2026-11-17.md`:
- branch, baseline SHA, changed files and completed D/CF IDs;
- commands actually run and output summaries, failed/skipped checks and reasons;
- migrations and exact startup commands; demo URL and selected mode;
- three-minute walkthrough and expected scores;
- known limitations and next task with concrete starting files.

Update `learn.md` for decisions, `04-task-backlog.md` for verified slices, and `VALIDATION.md` for fresh evidence. Never mark all CF-004/005 complete when only a subset shipped. Supply small reviewable commits or patches; preserve unrelated changes. Do not publish a live API or real employee data as part of this handoff.

## 5. Logo placement — completed in this handoff

Canonical asset: `web/public/brand/campfire-tech-logo.png` (transparent PNG).
- Vite URL: `/brand/campfire-tech-logo.png`.
- Standalone demo relative URL: `../brand/campfire-tech-logo.png`.
- Wired into React shell branding, standalone Campfire header and both page favicons.
- Use an empty image alt when adjacent text already says Mastek Campfire; label the home link. Preserve aspect ratio; use `object-fit: contain`.
- Do not rename namespaces, storage keys or API headers simply to match the visual rebrand.
- Public website already uses the same approved flame/circuit symbol. No website redesign or logo regeneration is required today.

## Completion criteria

**Minimum today:** D1–D4 pass, server-backed published-story reward survives reload/restart, negative cases pass, logo present and results recorded. **Stretch:** D5 full server-backed loop. **Blocked is a valid status:** explain missing tools/branch, retain a working demo and hand off exact next actions rather than simulated success.
