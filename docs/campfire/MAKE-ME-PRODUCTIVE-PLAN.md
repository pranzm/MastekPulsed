# Make me Productive — web and native delivery plan

Date: 17 November 2026. Requested scope: personal customizable habit dashboard, customizable Pomodoro timer, practical procrastination hints, box breathing and mindfulness timers. This is an optional employee space, separate from Campfire recognition and editorial publishing.

## Delivered web prototype

- Employee Web navigation and standalone Campfire demo include **Make me Productive**.
- Habits: add, rename, delete, select daily/weekdays/weekends, tick/untick elapsed days in the current week, completion count. Dates follow the browser timezone; future/off-schedule dates are disabled.
- Focus: custom focus/short/long breaks, explicit start, pause, resume, reset, completion message and session count. One timer runs at a time. Phase changes replace the previous timer. Deadline arithmetic handles background throttling; no automatic restart.
- Reset: five-minute starter, practical hints, four-second box breathing phases and custom mindfulness duration. No microphone, AI calls, sound permission or medical outcome claims.
- Synthetic habits/settings persist in sessionStorage under a persona-specific key. Timer resets on navigation/reload. The prototype is not secure storage, native functionality or cross-device synchronization.
- Live mode shows a privacy gate instead of accepting personal entries: the current header-based impersonation mechanism cannot satisfy employee-only privacy. Managers, publishers, reviewers and leaderboard users must never gain access to personal habit data through application roles.

## Product decisions

Employee controls names, schedules, completion history and deletion. Start with binary daily completion; numeric targets, reminders and arbitrary weekly schedules are subsequent enhancements. A missed day is neutral; no public streaks, penalties, team comparison or productivity score. Public recognition must not consume these events. Mindfulness and breathing are voluntary; offer Stop and a natural-breathing alternative. Hints suggest actions without diagnosing the employee.

Private means owner-only application access. Database operators and backups require separate organizational access controls; do not promise end-to-end encryption unless implemented. No habit text, session notes or wellbeing choices in analytics, error logs, AI prompts, push payloads, manager dashboards or shared audit views. Personal free-text task notes are excluded from MVP.

## Proposed data model and API (not implemented)

New `productivity` module in the existing Spring Boot modular monolith. Add the next unused Flyway migration; do not rewrite existing migrations.

- `personal_habits`: UUID id, owner subject, name (1–80 chars), schedule, IANA timezone, createdAt, archivedAt, optimistic version.
- `habit_checkins`: habit id, owner subject, local date, completed, version; unique `(habit_id, local_date)`. Store the local day explicitly to avoid midnight/DST shifts.
- `productivity_preferences`: owner subject, focus/short/long/reset durations, optional reminder consent and timezone.
- Optional `focus_sessions`: owner, client session UUID, kind, startedAt, completedAt, actual elapsed seconds. Only persist if the employee enables history; never infer a completed session from a closed screen.

All routes start `/api/v1/me/productivity`; owner comes exclusively from verified authentication, never request parameters or a persona header. IDs alone never grant access.

| Route | Behavior |
| --- | --- |
| GET /habits | Owner's active/archived habits, bounded pagination |
| POST /habits | Validate fields; caller-scoped idempotency key; create once |
| PATCH /habits/{id} | Owner check + expected version; 409 stale update |
| DELETE /habits/{id} | Owner check; delete associated check-ins under retention policy |
| GET /checkins?from=&to= | Owner only, maximum 93-day window |
| PUT /habits/{id}/checkins/{date} | Explicit completed boolean; idempotent upsert; validate local date/schedule |
| GET/PUT /preferences | Owner's validated duration/timezone settings |
| POST /sessions | Optional history, client UUID deduplication; no habit content |
| DELETE /data | Explicit confirmation and owner reauthentication; purge own module data |

Unauthenticated -> 401. Another employee's resource -> 404 without existence disclosure. Invalid input -> 422; stale writes/idempotency conflicts -> 409. Use Cache-Control: no-store for private reads. Browser caches must be cleared on sign-out/account change. Disable general service-worker caching for these routes.

## Ordered implementation backlog

| ID | Deliverable | Dependencies | Acceptance |
| --- | --- | --- | --- |
| MP-01 | Web interaction prototype | None | Implemented; demo privacy disclosure, both entry points, custom timers and habits |
| MP-02 | Verified Entra/OIDC principal and privacy policy | Identity/security review F01 | No persona headers in live mode; employee A cannot read/update/delete B's data; editorial/admin roles gain no personal-data bypass |
| MP-03 | Schema, DTO/OpenAPI contracts and ownership service | MP-02 | Additive migration; scoped queries; validation; idempotency, unique checks and optimistic locking on PostgreSQL |
| MP-04 | React live adapter and personal dashboard | MP-03 | CRUD/history persists across authenticated devices; errors cannot become demo success; account switches cancel pending requests and clear state |
| MP-05 | Production timer state machine | MP-04 | Deadline-based pause/resume/reset; persisted paused/deadline state; no duplicate completion on resume/reload; explicit phase transition; accessible status announcements |
| MP-06 | React Native screens and local lifecycle | MP-03, MP-05 | Same navigation and contracts; AppState resume; encrypted local cache where offline storage is enabled; foreground/background device tests on iOS and Android |
| MP-07 | Hints, breathing and mindfulness accessibility | MP-05 | Text alternatives, reduced motion, Stop, keyboard/screen-reader flow; review content sources; no therapeutic efficacy claims |
| MP-08 | Privacy and reliability release gate | MP-04 through MP-07 | Cross-user tests, shared-device logout, offline conflicts, deletion, timezone/DST, clock changes, mobile sleep, storage failure, interrupted sessions; security review sign-off |

Suggested delivery slices, with estimates to be agreed after MP-02: (1) identity + contracts; (2) private habits on web; (3) timers and reset tools; (4) native parity + hardening. Do not schedule a real employee pilot before MP-02 and MP-08 pass.

## Timer / offline rules

Settings ranges: focus 1–120 minutes; breaks 1–60; reset 1–30. Editing settings affects a new session, not a running deadline. Persist absolute deadline plus remaining-at-pause; recompute on resume. Do not depend on a background JS interval. On system clock changes reconcile against monotonic elapsed time where available. No automatic microphone, sound or notification permission. Device-native notifications require opt-in and generic content. Offline check-ins queue under the owner identity with version/idempotency metadata; retries cannot duplicate records, and logout must clear unsent private data or explicitly ask whether to discard it. Concurrent devices return a visible conflict instead of silent overwrite.

## Verification and launch measures

Run PostgreSQL integration tests, route-level authorization tests, web browser tests and real-device lifecycle tests. Test storage disabled, keyboard navigation, text zoom, 390px viewport, reload during focus, completing while asleep, DST midnight and persona/session replacement. Count opt-in feature adoption only in aggregate; never publish individual habit adherence or classify employee productivity. Product value is a hypothesis, not a measured benefit in this POC.

## Content sources

- NHS task-starting guidance: https://www.nhs.uk/every-mind-matters/mental-wellbeing-tips/self-help-cbt-techniques/tackling-your-to-do-list/
- NHS mindfulness: https://www.nhs.uk/mental-health/self-help/tips-and-support/mindfulness/
- Cleveland Clinic box breathing: https://health.clevelandclinic.org/box-breathing-benefits

Guidance was checked on 17 November 2026. The application paraphrases short practical exercises; it provides no treatment or guaranteed outcome.
