# Mastek Pulse / Campfire POC — Application and Security Review

**Reviewed:** 17 November 2026 (IST)  
**Repository:** `/Users/pranjalmedhi/Desktop/Workspace/Mastek-Pulse`  
**Baseline HEAD:** `cb28c168d7b77860210c598c386586074d33e7ff`  
**Scope:** Current working tree, including modified and untracked application files; Spring Boot API, database migrations, React employee/portal clients, standalone browser demo, React Native prototype, configuration and tests. This is a whole-POC review, not just a commit diff.

## Assessment

**Suitable for a controlled demonstration with synthetic data; not ready for a real employee pilot.** The API has useful publication and audience controls, but identity is still impersonation by request header, some endpoints bypass publication visibility, and reproducible concurrency defects affect connection capacity and recognition. “Live” in the web client selects the Java transport; it does not establish secure authentication, PostgreSQL persistence, real AI, or working native integration.

The baseline build/tests pass. Nine additional review probes demonstrate faulty behavior that the existing suite does not cover. No application fixes were made during this review. Existing working-tree changes were preserved.

Priority: **P1** = address before a shared pilot because access or core data integrity is affected; **P2** = correct before relying on the affected journey or capability. Deliberate demo shortcuts are identified separately from implementation defects.

## Findings

### F01 — [P1, Security] A caller can impersonate any employee or publisher

**Evidence:** `backend/src/main/java/com/mastek/pulse/common/IdentityService.java:40–74`; `common/IdentityController.java:46–50`; `common/GlobalExceptionHandler.java:70–73` (the latter paths are under the same Java package root).

`authenticate()` trusts `X-Pulse-User-Id`; missing headers default to Asha. Requiring an explicit header only removes the default—it does not authenticate the claimed identity. `/api/v1/personas` has no authentication and lists IDs, email addresses, roles and audience memberships. Wildcard CORS further allows arbitrary origins to call/read these APIs where browser network policy permits.

**Reproduced:** With strict-header mode enabled, an unauthenticated persona-list request returned 200, and supplying only `X-Pulse-User-Id: usr_marcus` returned the publishing queue. Every role check downstream inherits this impersonated identity.

**Remediation / acceptance:** Keep persona authentication and persona enumeration in an explicit local demo profile. For a real pilot, use a verified security principal, reject client identity headers, validate token signature/issuer/expiry/audience, and restrict allowed origins. A forged publisher header must fail even when the ID is valid. Spring Security documents the supported [JWT resource-server validation mechanism](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

### F02 — [P1, Security] Invitations expose unpublished content and use the working audience

**Evidence:** `backend/src/main/java/com/mastek/pulse/connection/ConnectionService.java:103–110, 173–177, 337–346`.

Request creation checks `story.audienceId` but never requires a published snapshot. A creator can attach an invitation to a draft; a different audience member can request it directly. The response falls back to `currentVersion` and returns the draft title. Withdrawal also leaves the invitation available, subject to its own expiry/capacity checks. Editing the working audience of a published story changes request eligibility before that audience change is approved.

**Reproduced:** Created Daniel's unpublished draft with an invitation, then sent a request as Asha. It returned `PENDING` and the unpublished title.

**Remediation / acceptance:** Apply `requirePublishedVisibility` before creating new requests and use the published audience. Define access for historical requests separately; never fall back to unpublished revisions in participant responses. Test draft, withdrawn and pending audience-change cases through connection endpoints, not only story/AI endpoints. This follows OWASP's requirement to [validate authorization on every access path](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

### F03 — [P1, Application] Concurrent acceptance of one request consumes two places

**Evidence:** `backend/src/main/java/com/mastek/pulse/connection/ConnectionService.java:166–190, 211–218`; `connection/ConnectionRequestEntity.java:51–52`.

The request is loaded before the invitation lock. Two transactions can both retain a `PENDING`, version-1 request in their persistence contexts. After serializing on the invitation, the second transaction still checks its stale request. The integer `version` field is not a JPA `@Version`, so it does not prevent the overwrite.

**Reproduced:** Two transactions preloaded the same request, then accepted it concurrently. Both succeeded: one accepted request consumed **two** places. The database capacity constraint prevents exceeding capacity, but does not prevent this double counting.

**Remediation / acceptance:** Serialize/reload the request under a consistent locking order, or use an atomic state/version compare-and-set whose failure rolls back the capacity change. Repeat acceptance must produce one transition, one capacity increment and one acceptance event; competing requests must still respect total capacity.

### F04 — [P1, Application] Appreciation can award points after withdrawal

**Evidence:** `backend/src/main/java/com/mastek/pulse/recognition/RecognitionService.java:153–195, 203–220, 365–383`; `content/PublishingWorkflowService.java:478–488`.

Appreciation reads and checks the content before locking users; it never locks/rechecks the content. Withdrawal locks the content but its reversal/reconciliation path does not take the same user locks. A transaction that observed `PUBLISHED` can continue after withdrawal and insert new positive events that the completed reversal never saw. The reconciliation path also bypasses the normal locks used for weekly cap decisions.

**Reproduced:** Paused an appreciation transaction after reading the published item, withdrew it in another transaction, then resumed appreciation. The item remained `WITHDRAWN`, but a new **+5 giving award** was committed.

**Remediation / acceptance:** Establish a consistent lock order across content, user cap checks, appreciation and withdrawal; recheck publication after the relevant lock. Reversal/reconciliation must share the award locking protocol. Concurrent withdrawal/appreciation must leave no active reward for withdrawn content, and competing promotions must not exceed caps.

### F05 — [P2, Security/Application] Media URLs are not signed or expiry-checked

**Evidence:** `backend/src/main/java/com/mastek/pulse/listen/ListenController.java:127–145, 179`; `content/FeedService.java:262–264`.

The stream endpoint only rejects the literal signature string `expired`. Missing or arbitrary signatures are accepted, and the URL's `exp` value is not parsed. It checks the working audience but not publication status, published revision or audio readiness. Its current payload is a generated eight-second chime; this is a missing security boundary, not evidence that real stored recordings were disclosed.

**Reproduced:** A request for a draft with no signature and `exp=1` returned 200 `audio/wav`.

**Remediation / acceptance:** Clearly isolate synthetic playback. Before serving real assets, authorize the published snapshot and issue/verify an expiring token bound to its media asset/revision, or use a storage provider's signed URLs. Missing/tampered/expired tokens and withdrawn media must fail. Browser `<audio>` cannot carry the custom persona header used by normal API calls, so the delivery design must also support real browser authentication.

### F06 — [P2, Application] Publication replay earns another reward in a later week

**Evidence:** `backend/src/main/java/com/mastek/pulse/content/PublishingWorkflowService.java:402–408`; `recognition/RecognitionService.java:93–126`.

The already-published branch invokes the award service again. Award identity and weekly limits use the **current** clock week, not the original publication event. A previously published story therefore qualifies for a fresh weekly source key without a new publication.

**Reproduced:** Published a story on 17 November, advanced the injected clock to 24 November, and replayed the same publish call. `publishedAt` stayed unchanged, but the author received +20 for the week of 23 November.

**Remediation / acceptance:** Bind award deduplication and attribution to the actual publication event and its timestamp. A replay across a week boundary must neither create an award nor change historical totals. Keep genuine republication rules explicit.

### F07 — [P2, Application] Scheduled revisions of published stories never release

**Evidence:** `backend/src/main/java/com/mastek/pulse/content/PublishingWorkflowService.java:420–429`; `outbox/OutboxWorkerService.java:60–63, 91–97`.

Scheduling only changes item status to `SCHEDULED` when no published version exists. An already-published item stays `PUBLISHED`, while the worker searches only for `SCHEDULED` items. The resulting outbox event is simply marked processed; it does not release that revision.

**Reproduced:** Published revision 1, edited and approved revision 2, scheduled it, advanced beyond its due time and ran the worker. Revision 1 was still published.

**Remediation / acceptance:** Represent pending publication separately from live publication, including the scheduled revision ID, and process it under a content lock with approval/audience/media revalidation. Test both first publication and replacement publication, plus edit/withdrawal while scheduled.

### F08 — [P2, Application] Publishing is not bound to the revision the publisher saw

**Evidence:** `backend/src/main/java/com/mastek/pulse/content/PublishingWorkflowService.java:388–400`; `web/src/api/apiClient.ts:1620–1632`; `web/src/portal/PublishingPortalWorkspace.tsx:116–126`.

The API checks the expected revision only if the caller supplies it, and the web client never sends it. A publisher looking at approved revision 1 can click Publish after another user has created and approved revision 2; the server publishes revision 2 instead. Reviewer approval remains enforced, but the publisher's action is not bound to their displayed content/audience.

**Reproduced:** Used the web client's publish payload after replacing approved revision 1 with approved revision 2. Revision 2 was published successfully.

**Remediation / acceptance:** Require a positive integer `revisionNumber` for publish/schedule, send `selectedItem.currentVersion`, and return 409 on mismatch. Reject missing/malformed values rather than silently selecting the latest revision.

### F09 — [P2, Security/Application] The AI budget cap is mutable by employees and races under concurrency

**Evidence:** `backend/src/main/java/com/mastek/pulse/ai/AiController.java:54–62`; `ai/AiAssistanceService.java:168–178`; `backend/src/main/resources/application.yml:49–54`.

`/api/v1/ai/budget/simulate-cap` authenticates but never checks an admin role or demo profile; any persona can change the process-wide cap. Independently, `count()` followed by ledger insertion is not atomic. The configured `pulse.ai.enabled` flag is not consulted by the assistance service.

**Reproduced:** With one action remaining, two concurrent transactions each received `aiAvailable=true` and committed two ledger entries, exceeding the cap by one. The unrestricted cap route is confirmed by source inspection. Current assistance is deterministic string generation; actual provider spend was not exercised.

**Remediation / acceptance:** Remove the simulation route from live profiles, honor an explicit enable/disable control, and reserve budget atomically in shared persistence before any billable operation. Test concurrent calls at the final allowance and unauthorized attempts to change policy.

### F10 — [P2, Security] Audit and metric endpoints do not consistently respect scope

**Evidence:** `backend/src/main/java/com/mastek/pulse/content/PublishingWorkflowService.java:533–544`; `connection/ConnectionController.java:96–101`.

Audit scoping only resolves content items/revisions; other target types default to allowed. `REQUEST_ACCEPTED` audit events contain host/request IDs and capacity details, so scoped editorial staff can see connection activity outside their audience. Missing content targets also default to allowed. `/pilot/metrics` only authenticates and reads all requests/feedback, regardless of role or audience; hiding its UI from employees does not restrict its API.

**Validation:** Source-confirmed; no dedicated HTTP disclosure probe was run for this finding.

**Remediation / acceptance:** Define explicit policies per audit target type; resolve connection events through their content audience and fail closed on unresolved targets. Require the intended analytics role and audience scope. Test a reviewer without restricted-audience membership and an ordinary employee making direct API calls.

### F11 — [P2, Security/Application] Persona changes can retain or reload the previous persona's data

**Evidence:** `web/src/App.tsx:88–99, 101–168, 171–182`.

`clearPersonaScopedState()` does not clear audio queues, selected audio or the transcript modal. `loadAllData()` has no abort or generation check: an earlier request can finish after a persona/transport change and overwrite the new user's state, including portal data. A slow old request can also clear the loading flag while the new request is still pending. This defeats the intended persona separation in the demonstrator and is unsafe to carry into real account switching.

**Validation:** Source-confirmed asynchronous ordering defect; browser timing reproduction was not run.

**Remediation / acceptance:** Cancel obsolete requests or gate every state update with a request generation/session ID; clear all user-bound media/transcript state on identity/transport changes. Test delayed old-persona responses arriving last and switching while a transcript is open.

### F12 — [P2, Application] Web playback progress is a timer, not actual media position

**Evidence:** `web/src/App.tsx:183–234, 285`; `backend/src/main/java/com/mastek/pulse/listen/ListenController.java:140`.

The player increments a separate counter every second. Start/resume and ±15-second controls change React state but never set `HTMLAudioElement.currentTime`. The element has no time/error/ended synchronization, and `play()` failures are swallowed after setting playing state. The eight-second chime can finish or fail while the UI keeps recording progress against a several-minute episode.

**Validation:** Source-confirmed; real-device playback was not tested.

**Remediation / acceptance:** Drive position from media events, seek after metadata loads, save actual position, handle play failures/ended state, and distinguish sample audio from real-duration content. Resume and seek should be verified against the audible position and persisted API value.

### F13 — [P2, Application] Live pilot metrics include invented success indicators

**Evidence:** `backend/src/main/java/com/mastek/pulse/connection/ConnectionController.java:125–149`.

The live endpoint always reports `returnParticipationPct=46.5` and `materialTrustIncidents=0`. Host workload comes from seeded user fields, and the “weekly active” denominator counts all users with no activity/week filter. Useful-connection percentage counts two people per connection without deduplicating people. These values can imply pilot success without measuring it.

**Validation:** Source-confirmed constants/calculation. No inference about actual pilot outcomes is warranted.

**Remediation / acceptance:** Return unavailable values as unknown with provenance, or implement period-bound activity/incident/workload measurements and distinct-person counts. Keep illustrative metrics explicitly labeled and confined to demo mode; test empty data and repeated connections involving the same participants.

### F14 — [P2, Application/Performance] Leaderboard computation repeatedly loads all content for every user

**Evidence:** `backend/src/main/java/com/mastek/pulse/recognition/RecognitionService.java:493–505, 578–579`; `web/src/App.tsx:118–126`.

For each user, the leaderboard loads memberships and calls the progress calculator, which performs several queries and loads the entire content table. It computes every candidate before taking the top five. The React shell calls this on employee refreshes, including refreshes after mutations. Cost grows with users multiplied by content volume, contradicting the documented 10,000-employee readiness claim.

**Validation:** Source-confirmed query pattern; no load benchmark or latency claim.

**Remediation / acceptance:** Aggregate scoped weekly events/appreciations in the database or maintain a bounded projection; batch membership lookup and apply ranking/limits server-side. Establish query-count and latency budgets against representative data before making scale claims.

## Deliberate prototype boundaries and other gaps

These matter to acceptance, but should not be confused with demonstrated production exploits:

- **Native app is a UI simulation.** `mobile/App.tsx:180–185, 397–423` changes local React state for sending/submitting and claims the draft is saved. It has no API calls or durable draft storage. `NativeAudioService` exists but is never imported by the app; the radio play button has no handler. Backend cross-device progress tests do not establish that the native journey works. Wire it to the API/storage/audio lifecycle, or label it explicitly as a non-persistent prototype.
- **Outbox delivery is a stub.** `OutboxWorkerService.java:91–107` marks pending events `PROCESSED` without dispatching notifications. It has no claim/lease/row-lock mechanism for multiple workers. Scheduled-item processing also lacks the content locking used by synchronous publishing. Do not describe notification delivery or multi-instance worker correctness as verified.
- **Input validation is inconsistent.** Most controllers use untyped maps. General content creation/editing lacks the challenge service's length constraints; invalid types, null edits and malformed schedule timestamps can surface as server errors. Connection request idempotency checks do not compare payloads or recover concurrent insert conflicts. Add typed validation and focused negative/retry cases.
- **Local deployment defaults need containment.** Compose publishes PostgreSQL on all interfaces (`docker-compose.yml:22–23`) with a checked-in development credential. The API's default database is in-memory H2, even if the frontend says “live”; restart loses data. Use loopback binding for demos and explicit durable database/secret configuration for pilots. No actual deployment exposure was tested, and credential values are intentionally omitted here.
- **Dependency/security operations are unverified.** Build files specify Spring Boot 3.3.4 and Expo 51; the installed web build reports Vite 5.4.21. No current dependency vulnerability scan, secret-history scan, deployed TLS/header review, accessibility audit, or penetration test was performed. These version observations are not CVE findings.
- **Documentation overstates implementation.** Architecture prose describes OIDC, enforced rate limiting, signed media, notification dispatch and broad scalability that are absent or partial in code. Some documents also describe Java integration as pending while current source includes it. Treat diagrams and prior validation notes as intent/history; update capability statements from actual evidence.

## Validation performed

All execution used a separate copy at `/private/tmp/mastek-pulse-review-20261117`, with synthetic H2 data. The original application source hashes remained unchanged during review. Existing development databases were not used.

| Check | Result | Meaning / limitation |
| --- | --- | --- |
| React TypeScript + Vite production build | Passed | Compiles and bundles current web source; does not prove browser workflows |
| Standalone browser engine tests | 11 passed | Covers `web/public/campfire/engine.mjs`, not the React live adapter or Java API |
| Existing Java suite, H2 | 7 passed | Includes existing integration/recognition cases; does not cover the defects above |
| Additional review probes, H2 | 9 passed | Assertions intentionally confirm faulty behavior; these are not fixes or passing security acceptance tests |
| PostgreSQL-specific rerun | Not performed | Earlier repository claims were not counted as fresh evidence |
| Native build/device tests | Not performed | Native dependencies were absent from the checkout |

The initial Java run encountered Mockito/Byte Buddy dynamic-attach restrictions in this execution environment. Rerunning with the cached Byte Buddy agent attached at JVM startup passed; no project source/build file was changed to do this.

### Reproducing the checks

Run against a disposable checkout/database. In the isolated copy, the commands were equivalent to:

```sh
npm --prefix web run build
npm --prefix web run test:campfire
mvn -o -f backend/pom.xml -DargLine=-javaagent:/path/to/byte-buddy-agent-1.14.19.jar test
mvn -o -f backend/pom.xml -DargLine=-javaagent:/path/to/byte-buddy-agent-1.14.19.jar -Dtest=ReviewProbesTest test
```

The baseline seven tests ran before the temporary probe file was added. Maven 3.9.4 and Java 21.0.6 were used. Review probe source, Java run logs and source hashes are retained outside this repository in:

`/Users/pranjalmedhi/Documents/ChatGPT/Mastek Pulse/outputs/code-review-2026-11-17/`

Probe-to-finding mapping: header impersonation → F01; draft invitation → F02; double accept → F03; appreciation/withdrawal race → F04; unsigned draft media → F05; next-week replay → F06; scheduled replacement → F07; omitted publication revision → F08; concurrent AI allowance → F09. Race probes use separate transactions and barriers to force the problematic interleavings; PostgreSQL regression tests are still required.

## Recommended order of work

1. Separate local demonstration from real-pilot security; fix identity and apply publication/audience policy consistently (F01, F02, F05, F10).
2. Repair transaction and event identity invariants (F03, F04, F06–F09), then run focused regression tests on PostgreSQL, including multi-worker scheduling.
3. Correct web state/playback and metric provenance (F11–F13); explicitly label or complete native and notification journeys.
4. Replace repeated full-table projections (F14), run dependency/security checks and representative load tests, and update documentation to match verified capabilities.

Preserve the existing strengths: explicit demo/live transport with no silent fallback, scoped feed/story queries, reviewer role and author checks, revision approval invalidation, database uniqueness/capacity constraints, and transactional publication awards. The findings identify where those controls do not yet cover the complete journey.
