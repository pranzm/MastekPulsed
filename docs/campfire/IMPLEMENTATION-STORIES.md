# Mastek Campfire — implementation stories

Date: 17 November 2026. Baseline inspected: `cb28c16` in the supplied Mastek-Pulse repository. Display brand: **Mastek Campfire**. Assistant: **Saathi** (the developer handover called it Saarthi).

## Scope and evidence

The working browser engagement POC is in `web/public/campfire/`, embedded as the default mode in `web/src/App.tsx`. It deliberately uses synthetic, browser-local state. It is not connected to Spring Boot, corporate identity or the developer's voice service. Existing employee, publishing, connection and listening screens remain accessible through the app shell.

Verified source integration points:
- `web/src/App.tsx`: persona selection, employee/portal shell, playback and global refresh.
- `web/src/employee/EmployeeWorkspace.tsx`: contribution form, feed and story detail, connection journeys and audio screens.
- `web/src/portal/PublishingPortalWorkspace.tsx`: revision review and publishing controls.
- `web/src/api/apiClient.ts`: live requests mixed with local fallback; new recognition must use an explicit mode and must never turn failed live writes into local success.
- `web/src/api/contracts.ts`: shared frontend DTOs.
- `backend/src/main/java/com/mastek/pulse/content/PublishingWorkflowService.java`: `createOrSaveContribution`, `submitForReview`, `reviewRevision`, `publishOrSchedule`, `withdrawContent`; new awards belong after actual publication, not approval.
- `backend/src/main/java/com/mastek/pulse/content/FeedService.java`: publication and audience filtering reused for challenges and appreciation eligibility.
- `backend/src/main/java/com/mastek/pulse/common/`: synthetic persona authentication and user/audience records.
- `backend/src/main/resources/db/migration/`: Flyway schema and seed data.
- `docs/aidlc/openapi.yaml`: contract entry point.

No `voice-service/`, `docs/SAARTHI-DEMO.md`, Azure adapters, or 21-test voice suite exists in this inspected snapshot. The reported voice implementation must be obtained and reconciled before those changes can be validated. Earlier completed backlog checkboxes are not fresh evidence that all backend requirements pass.

## Product decisions

1. Preserve Radio, Culture Cast, updates, stories and consensual connections. Add a weekly challenge and voluntary recognition.
2. **Beyond My Job Title** is the first challenge. One qualifying published story/person/week earns 20 points. Approval alone earns zero.
3. Giving appreciation earns 5 points for each of the first three eligible stories/week. Receiving earns 5 per distinct colleague/week, capped at five. Self-appreciation and duplicate `(actor, story)` actions are disallowed. Maximum 60 points/week.
4. Badges: First Spark (published story), Connection Maker (appreciate stories from three teams), Community Favourite (five distinct appreciators). Demo badges are recomputed from the current weekly state. Lifetime badge persistence is future scope.
5. Weeks start Monday 00:00 UTC in the POC; display the period and timezone. Product can choose a different shared timezone before pilot; never use each viewer's browser timezone for global ranking.
6. Optional top-five leaderboard, tied scores share rank, alphabetical ordering only stabilizes ties. Opting out removes the public row while retaining private progress. No appraisal, attendance or productivity scoring.
7. Withdrawal removes public content and associated rewards; recompute caps deterministically. Private connection feedback never earns points.
8. Synthetic public demo uses one permitted audience. Real pilot ranking and activity explanations must not reveal restricted content or cross-audience participation.

## Delivery stories

### CF-001 — Campfire brand and entry point · P0 · Browser POC implemented
As an employee, I want a consistent Campfire identity so I recognise the same product across the website and app.
Targets: `web/index.html`, app and employee labels, proposal, website, docs. Keep package names, Java namespace, API headers and storage keys stable for compatibility.
Acceptance: Campfire title appears on entry; existing journeys remain reachable; Saathi display name is consistent. New recognition is visibly labelled a browser demo. No production SSO or cloud AI claim.

### CF-002 — Weekly challenge discovery and submission · P0 · Browser implemented; API pending
As an employee, I can discover an active challenge and submit a short story for review.
Targets: new `challenge/` Java module; `EmployeeWorkspace.tsx`; contracts; new Flyway migration.
Acceptance: active window and audience checked server-side; title 5–100 and body 30–1,200 characters; drafts remain private; one qualifying entry/person/challenge; invalid input 422, denied access 403, closed challenge 409. Creator sees review state and feedback. Resubmission creates a new revision and invalidates approval. No reward on submission.
Tests: wrong audience, closing-time boundary, duplicate concurrent submission, author-only draft read, return/resubmit.
Depends: CF-003 and CF-010 for live use.

### CF-003 — Explicit demo/live adapters · P0 · Pending
As a presenter, I can tell whether a result is simulated or persisted by the API.
Targets: `apiClient.ts`, app mode configuration, contract adapters.
Acceptance: explicit selected mode; simulated writes never follow a live failure. HTTP status/code retained. Visible live errors. Persona switching available only in synthetic demo mode. No demo header can impersonate a real employee in live mode.
Tests: live 401/403/409/500 and network loss remain failures; no local success; demo mode works offline; old browser keys remain compatible.

### CF-004 — Human publishing triggers recognition · P0 · Browser implemented; backend pending
As a contributor, I receive recognition only when a reviewed story becomes visible to its audience.
Targets: `PublishingWorkflowService.java`, new `recognition/` module and outbox integration.
Acceptance: reviewer differs from author; approval binds exact content revision and audience; publisher checks approval/current version. Actual immediate or scheduled publication produces one award source event. Replayed events and double clicks cannot duplicate points. Returning for changes or approval alone gives zero. Existing published snapshot remains stable while its replacement is in review.
Tests: self-approval, stale approval, publication retry, scheduled retry, concurrent publish, edit-after-approval.
Depends: CF-002, CF-003, CF-005, CF-010.

### CF-005 — Transactional recognition ledger · P0 · Browser rules implemented; backend pending
As an employee, I can trust that points reflect qualifying actions even when requests retry.
Targets: new `recognition/` service/entities/repositories; next available Flyway migration; domain contract.
Acceptance: server computes points; client never submits point values. Unique award source keys and actor-scoped idempotency; atomic cap reservation under concurrency; immutable ledger plus compensating reversals. Week and audience scope recorded. Withdrawal reverses applicable awards and recalculates capped eligible events in stable event-time/id order. Awards for re-publication cannot exceed weekly contribution cap.
Tests: race for last giving/receiving allowance; repeated consumer delivery; duplicate idempotency key with changed body returns 409; withdrawal/republication; week boundary.

### CF-006 — Appreciation and three badges · P0 · Browser implemented; backend pending
As a reader, I can recognise a colleague without a popularity contest driven by repeated clicks.
Targets: story detail and feed in `EmployeeWorkspace.tsx`; appreciation endpoint; recognition projections.
Acceptance: only visible published stories; no self-appreciation; one record/person/story; distinct recipients and team snapshots support badge progress; duplicate call returns existing result without reward. Appreciation beyond cap is recorded without extra points. Private feedback excluded. Badge state and progress are accessible as text.
Tests: inaccessible/unpublished/withdrawn story, repeated actor, five distinct actors, three teams, cap overrun and rollback.
Depends: CF-005, CF-010.

### CF-007 — Weekly Community Champions · P0 · Browser implemented; backend pending
As an employee, I can view weekly recognition and hide my public ranking.
Targets: leaderboard endpoint and UI, user preference, personal progress endpoint.
Acceptance: top five opted-in eligible participants; tied scores share rank; deterministic tie ordering; own progress remains private when hidden. Week selector and timezone clear. No inaccessible story titles, activity counts, or scores from restricted audiences leak. New week starts fresh. Opt-out applies on next read and invalidates cache.
Tests: tie, empty leaderboard, sixth participant, opt-out and re-opt-in, period rollover, restricted activity leakage.
Depends: CF-005, CF-006, CF-010.

### CF-008 — Integrate Saathi voice service · P1 · Typed/browser fallback implemented; voice pending
As an employee, I can optionally speak a command or use equivalent typed controls.
Targets: developer's missing `voice-service/` branch; new frontend adapter; app navigation and audio controls; docs.
Acceptance: reconcile Saathi/Saarthi names and commands without breaking existing settings. Push-to-talk requires a user action and shows listening state; microphone denied/unavailable falls back to typing. Whisper transcription and Piper output tested on demo machine. Foundry accepts only server-selected authorized sources and returns validated source IDs. Source-linked summaries exclude drafts, withdrawn and unauthorized items. Saathi cannot approve, publish, mutate points or bypass human actions. Cancel audio on persona/logout. Credentials server-side, bounded provider timeout and deterministic fallback.
Tests: revoked audience, malicious instructions in a story, missing Azure keys, Azure outage, invalid model JSON/source IDs, mic denial, unsupported audio, transcript mismatch, pause/stop. Record actual latency and intelligibility rather than invent targets.
Depends: CF-003, CF-010; obtain missing branch first.

### CF-009 — Contextual moderation and review evidence · P1 · Pending
As a reviewer, I see actionable assessment for the exact submitted revision and make the editorial decision.
Targets: missing Azure Content Safety adapter; revision assessment entity; portal review screen; publish gate.
Acceptance: policy describes which findings block or route to review. Secrets/contact-rule checks complement harm-category assessments. Assessment includes revision/hash, provider, timestamp and status. Edits invalidate it. Provider outage produces explicit unavailable state; no silent “safe” result. Human cannot bypass a configured blocking policy through direct API publication. AI never approves. Public demo remains synthetic until controls tested.
Tests: stale assessment, direct publish bypass, provider error, false positive review path, safe educational context, injection text, secret pattern; assess quality using representative fixtures.
Depends: CF-004, CF-008, CF-010.

### CF-010 — Shared publication visibility and scoped authorization · P0 · Pending
As an employee, my access boundaries hold across feed, portal, voice and recognition.
Targets: `FeedService.java`, `ContentController.java`, `PublishingWorkflowService.java`, `AiAssistanceService.java`, media and recognition queries.
Acceptance: central role/ownership/audience/publication policy; separate author preview from published reads; portal queue/audit scoped to editorial permissions; draft audience edits do not expose old published bodies. Voice and score projections use the same trusted policy. Denied operations leave all domain state unchanged.
Tests: ordinary employee portal call; wrong-audience reviewer; draft and withdrawn direct read; voice source request; unauthorized appreciation; leaderboard inference through restricted activity.

### CF-011 — Repeatable demonstration and accessibility · P0 · Browser implemented; full-stack pending
As a presenter, I can reset the synthetic scenario and demonstrate it predictably.
Targets: demo seed/reset tooling, new UI, `DEMO-GUIDE.md`.
Acceptance: reset affects synthetic data only, disabled in production; six personas; no real employees; three-minute sequence repeatable; refresh persists the browser demo; keyboard labels, focus indicators, readable feedback, responsive layout, no forced audio. Storage failure leaves usable in-memory state. Backend reset uses a separate demo database and authorization.
Tests: full browser journey, reset, refresh, keyboard navigation, mobile layout, backend deployment smoke test.

### CF-012 — Pilot learning dashboard · P1 · Pending
As a product owner, I can assess whether recognition creates useful participation.
Targets: existing pilot metrics endpoint/dashboard plus challenge aggregates.
Acceptance: definitions and denominators visible for unique contributors, next-week return, cross-team appreciation and privately confirmed useful connections. No inferred positive feedback, individual performance reports or invented results. Anonymous aggregates respect audience/privacy thresholds. Label synthetic demonstration metrics.
Depends: CF-005–007 and real pilot consent/readiness.

## Suggested order
CF-001 and CF-011 browser demonstration → CF-003 and CF-010 trust foundations → CF-002/005 contracts and storage → CF-004/006/007 integrated loop → CF-008/009 voice and moderation → CF-012 pilot measurement.

Production persistence and voice remain unchecked. Do not mark these complete solely because the browser simulation works.
